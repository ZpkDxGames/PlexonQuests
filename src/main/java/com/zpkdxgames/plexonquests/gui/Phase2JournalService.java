package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.gui.player.JournalFilter;
import com.zpkdxgames.plexonquests.gui.player.JournalNavigationContext;
import com.zpkdxgames.plexonquests.gui.player.JournalNextAction;
import com.zpkdxgames.plexonquests.gui.player.JournalView;
import com.zpkdxgames.plexonquests.gui.player.JournalViewModel;
import com.zpkdxgames.plexonquests.gui.player.QuestProgressPresentation;
import com.zpkdxgames.plexonquests.gui.player.QuestStatePresentation;
import com.zpkdxgames.plexonquests.persistence.HistoryEntry;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.rotation.PeriodKeyService;
import com.zpkdxgames.plexonquests.rotation.RerollService;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.JournalStateResolver;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Unified player-facing Quest Journal. This class owns presentation and navigation only.
 * Quest lifecycle, progress, tracking, persistence, rerolls and reward transactions remain
 * authoritative in their existing services.
 */
public final class Phase2JournalService implements Listener {
    private static final List<Integer> CONTENT = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    private static final List<Integer> DETAIL_OBJECTIVES = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final List<Integer> DETAIL_REWARDS = List.of(28, 29, 30, 31, 32, 33, 34);
    private static final long CLICK_DEBOUNCE_NANOS = Duration.ofMillis(250).toNanos();
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final StorageService storage;
    private final RewardService rewards;
    private final RerollService rerolls;
    private final QuestEligibilityService eligibility;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final QuestTrackingService tracking;
    private final TextService text;
    private final JournalStateResolver states;
    private final ItemFactory items = new ItemFactory();

    public Phase2JournalService(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            StorageService storage,
            RewardService rewards,
            RerollService rerolls,
            QuestEligibilityService eligibility,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            QuestTrackingService tracking,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.storage = storage;
        this.rewards = rewards;
        this.rerolls = rerolls;
        this.eligibility = eligibility;
        this.prerequisites = prerequisites;
        this.history = history;
        this.tracking = tracking;
        this.text = text;
        this.states = new JournalStateResolver(eligibility, prerequisites, history);
    }

    public JournalStateResolver stateResolver() {
        return states;
    }

    public List<String> activeQuestIds(Player player) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) return List.of();
        return profile.visibleAssignments().stream()
                .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)
                .map(assignment -> assignment.definition().id())
                .distinct()
                .sorted()
                .toList();
    }

    public void openOverview(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        tracking.reconcile(player, profile);
        JournalViewModel model = homeModel(player, profile);
        Holder holder = create(JournalNavigationContext.home(), "<gradient:#F6C85F:#E9A83A><bold>Quest Journal</bold></gradient>");
        topNavigation(holder, JournalView.HOME);

        item(holder, 20, Material.WRITABLE_BOOK, "<gold><bold>Active Quests</bold>", List.of(
                "<gray>In progress <white>" + model.active(),
                "<gray>Ready to claim <green>" + model.readyToClaim(),
                "", "<dark_gray>Click to view active quests"), false,
                (p, c) -> openActive(p, JournalNavigationContext.active(0, null)));
        item(holder, 22, Material.NETHER_STAR, "<yellow><bold>Next Action</bold>", List.of(
                "<white>" + safe(model.nextAction().label()),
                "", "<dark_gray>Click to continue"), true,
                (p, c) -> runNextAction(p, model.nextAction()));
        item(holder, 24, Material.ENDER_EYE, "<aqua><bold>Eligible Quests</bold>", List.of(
                "<gray>Eligible now <aqua>" + model.eligible(),
                "<gray>Locked <white>" + model.locked(),
                "", "<dark_gray>Click to browse"), false,
                (p, c) -> openAvailable(p, JournalNavigationContext.eligible(0, JournalFilter.ALL, null, null)));
        item(holder, 29, Material.LODESTONE, "<yellow><bold>Tracked Quest</bold>", model.hasTrackedQuest()
                        ? List.of("<gray>Quest <white>" + safe(model.trackedQuest()), "", "<dark_gray>Click to continue")
                        : List.of("<gray>No active quest is tracked.", "<dark_gray>Track one from Active Quests."),
                model.hasTrackedQuest(), (p, c) -> openTracked(p));
        item(holder, 31, Material.EMERALD, "<green><bold>Ready to Claim</bold>", List.of(
                "<gray>Rewards waiting <green>" + model.readyToClaim(),
                "", model.readyToClaim() > 0 ? "<dark_gray>Click to view" : "<dark_gray>Complete an active objective first"),
                model.readyToClaim() > 0, (p, c) -> openActive(p, JournalNavigationContext.active(0, null)));
        item(holder, 33, Material.KNOWLEDGE_BOOK, "<green><bold>Completed</bold>", List.of(
                "<gray>Lifetime claimed <white>" + model.completed(),
                "", "<dark_gray>Click for quest history"), false, (p, c) -> openCompleted(p));
        item(holder, 40, Material.CLOCK, "<white><bold>Rotation</bold>", rotationLore(), false, null);
        item(holder, 42, Material.PAPER, "<white><bold>Help</bold>", List.of(
                "<gray>Active, Eligible, Tracked and rewards.",
                "<gray>Learn how Daily, Weekly and Milestone quests work.",
                "", "<dark_gray>Click for help"), false, (p, c) -> openHelp(p));
        item(holder, 53, Material.NETHER_STAR, "<yellow><bold>" + safe(model.nextAction().label()) + "</bold>",
                List.of("<dark_gray>Recommended next step"), true, (p, c) -> runNextAction(p, model.nextAction()));
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openActive(Player player) {
        openActive(player, JournalNavigationContext.active(0, null));
    }

    public void openActive(Player player, int page, QuestScope scope) {
        openActive(player, JournalNavigationContext.active(page, scope));
    }

    private void openActive(Player player, JournalNavigationContext context) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        tracking.reconcile(player, profile);
        List<QuestAssignment> assignments = profile.assignments().stream()
                .filter(a -> a.state() == AssignmentState.ACTIVE
                        || a.state() == AssignmentState.COMPLETED
                        || a.state() == AssignmentState.CLAIMING)
                .filter(a -> context.scope() == null || a.definition().scope() == context.scope())
                .sorted(Comparator.comparingInt((QuestAssignment a) -> a.state() == AssignmentState.COMPLETED ? 0 : 1)
                        .thenComparing(QuestAssignment::assignedAt))
                .toList();
        int pages = pages(assignments.size());
        int page = clampPage(context.page(), pages);
        JournalNavigationContext actual = context.withPage(page);
        Holder holder = create(actual, "<gold><bold>Active Quests</bold>");
        topNavigation(holder, JournalView.ACTIVE);
        int offset = page * CONTENT.size();
        for (int i = 0; i < CONTENT.size() && offset + i < assignments.size(); i++) {
            QuestAssignment assignment = assignments.get(offset + i);
            boolean tracked = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
            int slot = CONTENT.get(i);
            holder.inventory.setItem(slot, assignmentCard(assignment, tracked));
            holder.actions.put(slot, (p, click) -> openDetailsById(p, assignment.id(), actual));
        }
        if (assignments.isEmpty()) {
            item(holder, 22, Material.GRAY_DYE, "<gray><bold>No active quests</bold>", List.of(
                    "<gray>Your next Daily or Weekly assignment will appear here.",
                    "", "<dark_gray>Click to browse eligible quests"), false,
                    (p, c) -> openAvailable(p, JournalNavigationContext.eligible(0, JournalFilter.ALL, null, context.scope())));
        }
        listControls(holder, page, pages,
                page > 0 ? p -> openActive(p, actual.withPage(page - 1)) : null,
                page + 1 < pages ? p -> openActive(p, actual.withPage(page + 1)) : null);
        item(holder, 47, Material.COMPASS, "<aqua><bold>Scope: " + scopeLabel(actual.scope()) + "</bold>", List.of(
                "<gray>Daily • Weekly • Milestone • Assigned",
                "", "<dark_gray>Click to change scope"), false,
                (p, c) -> openActive(p, actual.withScope(nextScope(actual.scope()))));
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openAvailable(Player player, int page, String category) {
        openAvailable(player, JournalNavigationContext.eligible(page, JournalFilter.ALL, category, null));
    }

    public void openAvailable(Player player, int page, String category, QuestScope scope) {
        openAvailable(player, JournalNavigationContext.eligible(page, JournalFilter.ALL, category, scope));
    }

    private void openAvailable(Player player, JournalNavigationContext context) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        List<DiscoveryEntry> entries = new ArrayList<>();
        for (QuestDefinition definition : configs.snapshot().registry().quests().values()) {
            if (!definition.enabled()) continue;
            if (context.scope() != null && definition.scope() != context.scope()) continue;
            if (context.category() != null && !definition.category().equalsIgnoreCase(context.category())) continue;
            JournalState state = states.resolve(player, profile, definition);
            if (state != JournalState.AVAILABLE && state != JournalState.LOCKED) continue;
            if (context.filter() == JournalFilter.ELIGIBLE && state != JournalState.AVAILABLE) continue;
            if (context.filter() == JournalFilter.LOCKED && state != JournalState.LOCKED) continue;
            entries.add(new DiscoveryEntry(definition, state, lockedReason(player, profile, definition, state)));
        }
        entries.sort(Comparator.comparingInt((DiscoveryEntry e) -> e.state() == JournalState.AVAILABLE ? 0 : 1)
                .thenComparing(e -> e.definition().category(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(e -> plainName(e.definition()), String.CASE_INSENSITIVE_ORDER));
        int pages = pages(entries.size());
        int page = clampPage(context.page(), pages);
        JournalNavigationContext actual = context.withPage(page);
        Holder holder = create(actual, "<aqua><bold>Eligible Quests</bold>");
        topNavigation(holder, JournalView.ELIGIBLE);
        int offset = page * CONTENT.size();
        for (int i = 0; i < CONTENT.size() && offset + i < entries.size(); i++) {
            DiscoveryEntry entry = entries.get(offset + i);
            int slot = CONTENT.get(i);
            holder.inventory.setItem(slot, discoveryCard(entry));
            holder.actions.put(slot, (p, click) -> openDefinition(p, entry.definition().id(), actual));
        }
        if (entries.isEmpty()) {
            item(holder, 22, Material.GRAY_DYE,
                    context.filter() == JournalFilter.ELIGIBLE ? "<gray><bold>No eligible quests</bold>" : "<gray><bold>Nothing here</bold>",
                    List.of("<gray>Complete your current requirements to unlock more quests.",
                            "", "<dark_gray>Change the filter or scope to look elsewhere."), false, null);
        }
        listControls(holder, page, pages,
                page > 0 ? p -> openAvailable(p, actual.withPage(page - 1)) : null,
                page + 1 < pages ? p -> openAvailable(p, actual.withPage(page + 1)) : null);
        item(holder, 46, Material.HOPPER, "<yellow><bold>Filter: " + actual.filter().label() + "</bold>", List.of(
                "<gray>All • Eligible • Locked",
                "", "<dark_gray>Click to change filter"), false,
                (p, c) -> openAvailable(p, actual.withFilter(actual.filter().next())));
        item(holder, 47, Material.COMPASS, "<aqua><bold>Scope: " + scopeLabel(actual.scope()) + "</bold>", List.of(
                "<gray>Daily • Weekly • Milestone • Assigned",
                "", "<dark_gray>Click to change scope"), false,
                (p, c) -> openAvailable(p, actual.withScope(nextScope(actual.scope()))));
        item(holder, 51, Material.BOOKSHELF, "<light_purple><bold>Category: " + categoryLabel(actual.category()) + "</bold>", List.of(
                "<gray>Browse one configured category at a time.",
                "", "<dark_gray>Click to change category"), false,
                (p, c) -> openAvailable(p, actual.withCategory(nextCategory(actual.category()))));
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    /** Compatibility command entry; categories are now a filter in Eligible Quests. */
    public void openCategories(Player player) {
        openAvailable(player, JournalNavigationContext.eligible(0, JournalFilter.ALL, null, null));
    }

    public void openTracked(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        tracking.reconcile(player, profile);
        JournalNavigationContext context = new JournalNavigationContext(JournalView.TRACKED, 0, JournalFilter.ALL, null, null);
        Holder holder = create(context, "<yellow><bold>Tracked Quest</bold>");
        topNavigation(holder, JournalView.TRACKED);
        QuestAssignment trackedAssignment = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (trackedAssignment == null || trackedAssignment.state() != AssignmentState.ACTIVE) {
            item(holder, 22, Material.GRAY_DYE, "<gray><bold>No tracked quest</bold>", List.of(
                    "<gray>Open an active quest and choose Track.",
                    "", "<dark_gray>Click to view active quests"), false,
                    (p, c) -> openActive(p));
            item(holder, 53, Material.WRITABLE_BOOK, "<gold><bold>View Active Quests</bold>", List.of(), false,
                    (p, c) -> openActive(p));
        } else {
            holder.inventory.setItem(22, assignmentCard(trackedAssignment, true));
            holder.actions.put(22, (p, c) -> openDetailsById(p, trackedAssignment.id(), context));
            item(holder, 53, Material.LODESTONE, "<yellow><bold>Continue Tracked Quest</bold>", List.of(
                    "<white>" + safe(plainName(trackedAssignment.definition())),
                    "", "<dark_gray>Click for quest details"), true,
                    (p, c) -> openDetailsById(p, trackedAssignment.id(), context));
        }
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openCompleted(Player player) {
        openCompleted(player, JournalNavigationContext.completed(0));
    }

    private void openCompleted(Player player, JournalNavigationContext context) {
        if (!player.hasPermission("plexonquests.history")) {
            player.sendMessage(text.parse("<red>Completed quest history is not available to you."));
            openOverview(player);
            return;
        }
        int pageSize = CONTENT.size();
        int page = Math.max(0, context.page());
        JournalNavigationContext actual = JournalNavigationContext.completed(page);
        Holder holder = create(actual, "<green><bold>Completed Quests</bold>");
        topNavigation(holder, JournalView.COMPLETED);
        item(holder, 22, Material.CLOCK, "<gray><bold>Loading completed quests…</bold>", List.of(
                "<dark_gray>Your journal will update here when ready."), false, null);
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);

        storage.history(player.getUniqueId(), pageSize + 1, page * pageSize).whenComplete((entries, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                        return;
                    }
                    clearContent(holder);
                    if (failure != null) {
                        item(holder, 22, Material.RED_DYE, "<red><bold>History unavailable</bold>", List.of(
                                "<gray>Your quest data was not changed.",
                                "<dark_gray>Close and try again shortly."), false, null);
                        return;
                    }
                    boolean hasNext = entries.size() > pageSize;
                    List<HistoryEntry> visible = hasNext ? entries.subList(0, pageSize) : entries;
                    for (int i = 0; i < visible.size(); i++) {
                        HistoryEntry entry = visible.get(i);
                        holder.inventory.setItem(CONTENT.get(i), historyCard(entry));
                    }
                    if (visible.isEmpty()) {
                        item(holder, 22, Material.GRAY_DYE, "<gray><bold>No completed quests yet</bold>", List.of(
                                "<gray>Finish an active quest and claim its reward first."), false, null);
                    }
                    listControls(holder, page, hasNext ? page + 2 : page + 1,
                            page > 0 ? p -> openCompleted(p, JournalNavigationContext.completed(page - 1)) : null,
                            hasNext ? p -> openCompleted(p, JournalNavigationContext.completed(page + 1)) : null);
                }));
    }

    /** Legacy compatibility entry. Registry/config health is no longer a player destination. */
    public void openStatistics(Player player) {
        openOverview(player);
    }

    public void openHelp(Player player) {
        if (profile(player) == null) return;
        Holder holder = create(new JournalNavigationContext(JournalView.HELP, 0, JournalFilter.ALL, null, null),
                "<white><bold>Quest Journal Help</bold>");
        topNavigation(holder, JournalView.HELP);
        item(holder, 19, Material.WRITABLE_BOOK, "<gold><bold>Active</bold>", List.of(
                "<gray>Quests you are currently progressing.",
                "<gray>Open one to see objectives and rewards."), false, null);
        item(holder, 21, Material.ENDER_EYE, "<aqua><bold>Eligible</bold>", List.of(
                "<gray>Quests you can receive when their assignment rule runs.",
                "<gray>Locked quests explain what you still need."), false, null);
        item(holder, 23, Material.LODESTONE, "<yellow><bold>Tracked</bold>", List.of(
                "<gray>A tracked quest is still an Active quest.",
                "<gray>Tracking keeps one quest easy to find."), false, null);
        item(holder, 25, Material.EMERALD, "<green><bold>Ready to Claim</bold>", List.of(
                "<gray>All required progress is complete.",
                "<gray>Open the quest and choose Claim Reward."), false, null);
        item(holder, 29, Material.CLOCK, "<white><bold>Daily & Weekly</bold>", List.of(
                "<gray>Daily quests may be assigned by the daily rotation.",
                "<gray>Weekly quests may be assigned by the weekly rotation."), false, null);
        item(holder, 31, Material.NETHER_STAR, "<light_purple><bold>Milestone</bold>", List.of(
                "<gray>Milestone quests start automatically when requirements are met."), false, null);
        item(holder, 33, Material.BOOK, "<white><bold>Assigned</bold>", List.of(
                "<gray>Some quests are assigned directly by server staff."), false, null);
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openReroll(Player player, QuestAssignment assignment) {
        openReroll(player, assignment, JournalNavigationContext.active(0, assignment.definition().scope()));
    }

    private void openReroll(Player player, QuestAssignment assignment, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        QuestAssignment live = profile == null ? null : profile.assignment(assignment.id()).orElse(null);
        if (live == null || live.state() != AssignmentState.ACTIVE) {
            stale(player, parent);
            return;
        }
        rerolls.prepare(player, live, pending -> openRerollConfirmation(player, pending, parent));
    }

    private void openRerollConfirmation(
            Player player, RerollService.PendingReroll pending, JournalNavigationContext parent) {
        Holder holder = create(new JournalNavigationContext(
                        JournalView.REROLL_CONFIRMATION, 0, JournalFilter.ALL, null, pending.previous().definition().scope()),
                "<light_purple><bold>Confirm Reroll</bold>", 27);
        item(holder, 4, Material.PAPER, "<white><bold>What changes</bold>", List.of(
                "<gray>Your current quest will be replaced.",
                "<gray>Current progress on it will be lost.",
                "<gray>New quest <white>" + safe(plainName(pending.replacement().definition()))), false, null);
        item(holder, 11, Material.LIME_DYE, "<green><bold>Confirm Reroll</bold>", List.of(
                "<gray>Cost <white>" + money(pending.cost()),
                "<gray>Replace <white>" + safe(plainName(pending.previous().definition())),
                "", "<dark_gray>Click once to confirm"), true, (p, c) -> {
                    if (!holder.submit("reroll")) return;
                    p.closeInventory();
                    rerolls.confirm(p);
                });
        item(holder, 13, pending.replacement().definition().display().icon().material(),
                "<light_purple><bold>" + safe(plainName(pending.replacement().definition())) + "</bold>", List.of(
                        "<gray>Replacement quest",
                        "<gray>Scope <white>" + scopeLabel(pending.replacement().definition().scope()),
                        "<gray>Category <white>" + safe(pretty(pending.replacement().definition().category()))), false, null);
        item(holder, 15, Material.RED_DYE, "<red><bold>Cancel</bold>", List.of(
                "<gray>Keep your current quest and progress."), false, (p, c) -> {
                    rerolls.cancel(p);
                    openContext(p, parent);
                });
        item(holder, 22, Material.BARRIER, "<red><bold>Close</bold>", List.of(
                "<gray>Closing cancels this confirmation."), false, (p, c) -> {
                    rerolls.cancel(p);
                    p.closeInventory();
                });
        player.openInventory(holder.inventory);
    }

    private void openDetailsById(Player player, UUID assignmentId, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (assignment == null) {
            stale(player, parent);
            return;
        }
        openDetails(player, assignment, parent);
    }

    private void openDetails(Player player, QuestAssignment assignment, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestAssignment live = profile.assignment(assignment.id()).orElse(null);
        if (live == null) {
            stale(player, parent);
            return;
        }
        boolean trackedState = profile.pinnedAssignment().filter(live.id()::equals).isPresent()
                && live.state() == AssignmentState.ACTIVE;
        Holder holder = create(parent, "<aqua><bold>Quest Details</bold>");
        topNavigation(holder, selectedForParent(parent));
        renderDetailsHeader(holder, live, trackedState);

        List<ObjectiveProgress> objectives = live.objectives();
        int visibleObjectives = Math.min(objectives.size(), DETAIL_OBJECTIVES.size());
        int directObjectives = objectives.size() > DETAIL_OBJECTIVES.size() ? DETAIL_OBJECTIVES.size() - 1 : visibleObjectives;
        for (int i = 0; i < directObjectives; i++) {
            ObjectiveProgress objective = objectives.get(i);
            QuestProgressPresentation progress = QuestProgressPresentation.of(objective.current(), objective.required());
            item(holder, DETAIL_OBJECTIVES.get(i), Material.TARGET,
                    "<white><bold>" + objective.definition().display() + "</bold>", List.of(
                            text.progressBarMarkup(progress.percentage()) + " <white>" + progress.percentage() + "%",
                            "<gray>" + text.formatNumber(progress.current()) + " / " + text.formatNumber(progress.required()),
                            objective.complete() ? "<green>Complete" : "<gold>In progress"),
                    objective.complete(), null);
        }
        if (objectives.size() > DETAIL_OBJECTIVES.size()) {
            item(holder, DETAIL_OBJECTIVES.getLast(), Material.PAPER, "<white><bold>+" + (objectives.size() - directObjectives) + " more objectives</bold>", List.of(
                    "<gray>Additional objectives are part of this quest."), false, null);
        }
        QuestProgressPresentation total = QuestProgressPresentation.of(live.displayProgress().current(), live.displayProgress().required());
        item(holder, 20, Material.COMPASS, "<gold><bold>Overall Progress</bold>", List.of(
                text.progressBarMarkup(total.percentage()) + " <white>" + total.percentage() + "%",
                "<gray>" + text.formatNumber(total.current()) + " / " + text.formatNumber(total.required()),
                objectives.size() > 1 ? "<gray>Objectives <white>" + objectives.size() : ""), false, null);
        item(holder, 22, Material.TRIPWIRE_HOOK, "<yellow><bold>Requirements</bold>", prerequisiteLore(player, live.definition()), false, null);
        item(holder, 24, Material.CLOCK, "<aqua><bold>Availability</bold>", List.of(
                "<gray>" + assignmentMechanism(live.definition().scope()),
                live.expiresAt().map(expiry -> "<gray>Time remaining <white>" + text.formatDuration(Duration.between(Instant.now(), expiry)))
                        .orElse("<gray>No scheduled expiry")), false, null);

        List<?> rewardEntries = live.definition().rewards().entries();
        int directRewards = rewardEntries.size() > DETAIL_REWARDS.size() ? DETAIL_REWARDS.size() - 1 : rewardEntries.size();
        for (int i = 0; i < directRewards; i++) {
            var reward = live.definition().rewards().entries().get(i);
            item(holder, DETAIL_REWARDS.get(i), Material.CHEST, "<green><bold>Reward</bold>", List.of(
                    "<white>" + reward.display()), false, null);
        }
        if (rewardEntries.size() > DETAIL_REWARDS.size()) {
            item(holder, DETAIL_REWARDS.getLast(), Material.CHEST, "<green><bold>+" + (rewardEntries.size() - directRewards) + " more rewards</bold>", List.of(
                    "<gray>Additional rewards are included."), false, null);
        }
        if (rewardEntries.isEmpty()) {
            item(holder, 31, Material.CHEST, "<gray><bold>No listed rewards</bold>", List.of(), false, null);
        }
        item(holder, 40, stateMaterial(live.state()), assignmentStateColor(live.state()) + "<bold>" + QuestStatePresentation.label(live.state()) + "</bold>", List.of(
                live.state() == AssignmentState.ACTIVE ? "<gray>Tracking <yellow>" + QuestStatePresentation.tracking(trackedState) : "",
                "<gray>Reward summary <white>" + rewardSummary(live.definition())),
                live.state() == AssignmentState.COMPLETED, null);

        back(holder, parent);
        if (live.state() == AssignmentState.COMPLETED && player.hasPermission("plexonquests.claim")) {
            item(holder, 53, Material.EMERALD, "<green><bold>Claim Reward</bold>", List.of(
                    "<gray>Receive <white>" + rewardSummary(live.definition()),
                    "", "<dark_gray>Click once to claim"), true, (p, c) -> claim(p, live.id(), parent, holder));
        }
        if (live.state() == AssignmentState.ACTIVE
                && live.definition().scope().rotating()
                && configs.snapshot().settings().rerolls().enabled()
                && player.hasPermission("plexonquests.reroll")) {
            item(holder, 46, Material.ENDER_PEARL, "<light_purple><bold>Reroll Quest</bold>", rerollLore(player, live), false,
                    (p, c) -> {
                        if (!holder.submit("prepare-reroll")) return;
                        openReroll(p, live, parent);
                    });
        }
        refreshTrackingPresentation(player, holder, live, parent);
        close(holder);
        player.openInventory(holder.inventory);
    }

    private void renderDetailsHeader(Holder holder, QuestAssignment live, boolean trackedState) {
        List<String> headerLore = new ArrayList<>();
        headerLore.add("<dark_gray>" + scopeLabel(live.definition().scope()) + " • " + safe(pretty(live.definition().category())));
        if (!live.definition().display().shortDescription().isBlank()) {
            headerLore.add("");
            headerLore.add("<gray>" + live.definition().display().shortDescription());
        }
        headerLore.add("");
        headerLore.add("<gray>Status " + assignmentStateColor(live.state()) + QuestStatePresentation.label(live.state()));
        if (live.state() == AssignmentState.ACTIVE) {
            headerLore.add("<gray>Tracking <yellow>" + QuestStatePresentation.tracking(trackedState));
        }
        item(holder, 4, live.definition().display().icon().material(),
                live.definition().display().name(), headerLore, trackedState || live.state() == AssignmentState.COMPLETED, null);
    }

    private void refreshTrackingPresentation(
            Player player, Holder holder, QuestAssignment live, JournalNavigationContext parent) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        boolean trackedState = profile != null
                && profile.pinnedAssignment().filter(live.id()::equals).isPresent()
                && live.state() == AssignmentState.ACTIVE;
        renderDetailsHeader(holder, live, trackedState);
        item(holder, 49, stateMaterial(live.state()), "<white><bold>Status</bold>", List.of(
                assignmentStateColor(live.state()) + QuestStatePresentation.label(live.state()),
                live.state() == AssignmentState.ACTIVE ? "<yellow>Tracking: " + QuestStatePresentation.tracking(trackedState) : ""), false, null);
        if (live.state() == AssignmentState.ACTIVE && player.hasPermission("plexonquests.pin")) {
            item(holder, 51, Material.LODESTONE,
                    trackedState ? "<yellow><bold>Untrack Quest</bold>" : "<yellow><bold>Track Quest</bold>", List.of(
                            trackedState ? "<gray>Remove this quest from your tracked shortcut." : "<gray>Keep this quest easy to find.",
                            "", "<dark_gray>Click to " + (trackedState ? "untrack" : "track")), trackedState,
                    (p, c) -> toggleTracked(p, live.id(), parent, holder));
        } else {
            holder.actions.remove(51);
        }
    }

    private void openDefinition(Player player, String questId, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestDefinition definition = configs.snapshot().registry().quests().get(questId);
        if (definition == null || !definition.enabled()) {
            stale(player, parent);
            return;
        }
        QuestAssignment assigned = states.latest(profile, definition.id()).orElse(null);
        if (assigned != null && (assigned.state() == AssignmentState.ACTIVE
                || assigned.state() == AssignmentState.COMPLETED
                || assigned.state() == AssignmentState.CLAIMING)) {
            openDetails(player, assigned, parent);
            return;
        }
        JournalState state = states.resolve(player, profile, definition);
        Holder holder = create(parent, "<aqua><bold>Quest Details</bold>");
        topNavigation(holder, JournalView.ELIGIBLE);
        List<String> header = new ArrayList<>();
        header.add("<dark_gray>" + scopeLabel(definition.scope()) + " • " + safe(pretty(definition.category())));
        if (!definition.display().shortDescription().isBlank()) {
            header.add("");
            header.add("<gray>" + definition.display().shortDescription());
        }
        header.add("");
        header.add("<gray>Status " + journalStateColor(state) + QuestStatePresentation.label(state));
        item(holder, 4, definition.display().icon().material(), definition.display().name(), header,
                state == JournalState.AVAILABLE, null);

        List<String> objectiveLore = definition.objectives().values().stream().limit(6)
                .map(o -> "<gray>• <white>" + o.display() + " <dark_gray>×</dark_gray><white>" + text.formatNumber(o.amount()))
                .toList();
        List<String> objectives = new ArrayList<>(objectiveLore);
        if (definition.objectives().size() > 6) objectives.add("<gray>+" + (definition.objectives().size() - 6) + " more");
        item(holder, 20, Material.TARGET, "<aqua><bold>Objectives</bold>", objectives, false, null);
        item(holder, 22, Material.TRIPWIRE_HOOK, "<yellow><bold>Requirements</bold>", prerequisiteLore(player, definition), false, null);
        item(holder, 24, Material.CLOCK, "<white><bold>How it starts</bold>", List.of(
                "<gray>" + assignmentMechanism(definition.scope()),
                state == JournalState.AVAILABLE ? "<aqua>Eligible now" : "<gray>" + safe(lockedReason(player, profile, definition, state))), false, null);
        item(holder, 31, Material.CHEST, "<green><bold>Rewards</bold>", rewardLore(definition), false, null);
        item(holder, 40, stateMaterial(state), journalStateColor(state) + "<bold>" + QuestStatePresentation.label(state) + "</bold>", List.of(
                state == JournalState.AVAILABLE ? "<gray>This quest is eligible for its normal assignment rule." : "<gray>" + safe(lockedReason(player, profile, definition, state))),
                state == JournalState.AVAILABLE, null);
        back(holder, parent);
        item(holder, 49, stateMaterial(state), "<white><bold>Status</bold>", List.of(
                journalStateColor(state) + QuestStatePresentation.label(state)), false, null);
        close(holder);
        player.openInventory(holder.inventory);
    }

    private void claim(Player player, UUID assignmentId, JournalNavigationContext parent, Holder holder) {
        if (!holder.submit("claim")) return;
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment live = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (live == null || live.state() != AssignmentState.COMPLETED) {
            stale(player, parent);
            return;
        }
        if (!player.hasPermission("plexonquests.claim")) {
            player.sendMessage(text.parse("<red>You cannot claim quest rewards yet."));
            return;
        }
        player.closeInventory();
        rewards.claim(player, live);
    }

    private void toggleTracked(
            Player player, UUID assignmentId, JournalNavigationContext parent, Holder holder) {
        String submission = "track:" + assignmentId;
        if (!holder.submit(submission)) return;
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment live = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (live == null || live.state() != AssignmentState.ACTIVE) {
            stale(player, parent);
            return;
        }
        QuestTrackingService.Result result = tracking.toggle(player, assignmentId);
        switch (result) {
            case TRACKED -> player.sendMessage(text.parse("<yellow>Quest is now tracked."));
            case UNTRACKED -> player.sendMessage(text.parse("<gray>Quest is no longer tracked."));
            case NO_PERMISSION -> player.sendMessage(text.parse("<red>You cannot change quest tracking yet."));
            case NOT_ACTIVE -> player.sendMessage(text.parse("<red>Only active quests can be tracked."));
            case NOT_FOUND -> player.sendMessage(text.parse("<red>This quest changed while the menu was open."));
            case UNCHANGED -> { }
        }
        holder.release(submission);
        refreshTrackingPresentation(player, holder, live, parent);
    }

    private void runNextAction(Player player, JournalNextAction action) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        switch (action) {
            case CLAIM_READY -> profile.assignments().stream()
                    .filter(a -> a.state() == AssignmentState.COMPLETED)
                    .findFirst()
                    .ifPresentOrElse(a -> openDetails(player, a, JournalNavigationContext.home()), () -> openOverview(player));
            case CONTINUE_TRACKED -> openTracked(player);
            case VIEW_ACTIVE -> openActive(player);
            case BROWSE_ELIGIBLE -> openAvailable(player, 0, null);
        }
    }

    private void openContext(Player player, JournalNavigationContext context) {
        if (context == null) {
            openOverview(player);
            return;
        }
        switch (context.view()) {
            case HOME -> openOverview(player);
            case ACTIVE -> openActive(player, context);
            case ELIGIBLE -> openAvailable(player, context);
            case COMPLETED -> openCompleted(player, context);
            case TRACKED -> openTracked(player);
            case HELP -> openHelp(player);
            case DETAILS, REROLL_CONFIRMATION -> openOverview(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !MenuInteractionRouter.supportsAction(event.getClick())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        Action action = holder.actions.get(slot);
        if (action == null || !holder.acceptInteraction()) return;
        ClickType click = event.getClick();
        MenuInteractionRouter.defer(plugin, player, holder, () -> action.run(player, click));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private JournalViewModel homeModel(Player player, PlayerProfile profile) {
        int active = 0;
        int ready = 0;
        int eligibleCount = 0;
        int locked = 0;
        for (QuestDefinition definition : configs.snapshot().registry().quests().values()) {
            JournalState state = states.resolve(player, profile, definition);
            switch (state) {
                case ACTIVE, TRACKED -> active++;
                case COMPLETABLE -> ready++;
                case AVAILABLE -> eligibleCount++;
                case LOCKED -> locked++;
                default -> { }
            }
        }
        QuestAssignment trackedAssignment = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        boolean hasTracked = trackedAssignment != null && trackedAssignment.state() == AssignmentState.ACTIVE;
        String trackedName = hasTracked ? plainName(trackedAssignment.definition()) : "";
        JournalNextAction next = JournalNextAction.resolve(ready, hasTracked, active);
        return new JournalViewModel(active, ready, eligibleCount, locked, profile.completedTotal(), trackedName, next);
    }

    private ItemStack assignmentCard(QuestAssignment assignment, boolean trackedState) {
        QuestProgressPresentation progress = QuestProgressPresentation.of(
                assignment.displayProgress().current(), assignment.displayProgress().required());
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + scopeLabel(assignment.definition().scope()) + " • " + safe(pretty(assignment.definition().category())));
        lore.add("");
        lore.add("<gray>" + primaryObjective(assignment));
        if (assignment.objectives().size() > 1) lore.add("<gray>+" + (assignment.objectives().size() - 1) + " more");
        lore.add(text.progressBarMarkup(progress.percentage()) + " <white>" + progress.percentage() + "%");
        lore.add("<gray>" + text.formatNumber(progress.current()) + " / " + text.formatNumber(progress.required()));
        lore.add("<gray>Status " + assignmentStateColor(assignment.state()) + QuestStatePresentation.label(assignment.state()));
        if (assignment.state() == AssignmentState.ACTIVE) lore.add("<gray>Tracking <yellow>" + QuestStatePresentation.tracking(trackedState));
        lore.add("<gray>Reward <white>" + rewardSummary(assignment.definition()));
        lore.add("");
        lore.add(assignment.state() == AssignmentState.COMPLETED ? "<green>Click to view & claim" : "<dark_gray>Click to view details");
        return items.create(assignment.definition().display().icon().material(), text.parse(assignment.definition().display().name()),
                components(lore), trackedState || assignment.state() == AssignmentState.COMPLETED);
    }

    private ItemStack discoveryCard(DiscoveryEntry entry) {
        QuestDefinition definition = entry.definition();
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + scopeLabel(definition.scope()) + " • " + safe(pretty(definition.category())));
        if (!definition.display().shortDescription().isBlank()) {
            lore.add("");
            lore.add("<gray>" + definition.display().shortDescription());
        }
        lore.add("");
        lore.add("<gray>Status " + journalStateColor(entry.state()) + QuestStatePresentation.label(entry.state()));
        lore.add("<gray>Assignment <white>" + assignmentMechanism(definition.scope()));
        if (entry.state() == JournalState.LOCKED) lore.add("<gray>Requires <white>" + safe(entry.reason()));
        lore.add("<gray>Reward <white>" + rewardSummary(definition));
        lore.add("");
        lore.add("<dark_gray>Click to view details");
        return items.create(definition.display().icon().material(), text.parse(definition.display().name()), components(lore),
                entry.state() == JournalState.AVAILABLE);
    }

    private ItemStack historyCard(HistoryEntry entry) {
        String when = entry.claimedAt() != null
                ? HISTORY_DATE.format(entry.claimedAt())
                : entry.completedAt() != null ? HISTORY_DATE.format(entry.completedAt()) : "Completed";
        return items.create(Material.BOOK, text.parse(entry.displayName()), components(List.of(
                "<dark_gray>" + scopeLabel(entry.scope()) + " • " + safe(pretty(entry.rarity())),
                "",
                "<gray>Status <green>" + QuestStatePresentation.label(entry.state()),
                "<gray>Completed <white>" + safe(when),
                entry.rewardSummary() == null || entry.rewardSummary().isBlank()
                        ? "<gray>Reward <white>Claimed"
                        : "<gray>Reward <white>" + historyRewardSummary(entry.rewardSummary()))), false);
    }

    private List<String> prerequisiteLore(Player player, QuestDefinition definition) {
        Set<String> required = prerequisites.prerequisites(definition.id());
        if (required.isEmpty()) return List.of("<green>No quest prerequisites");
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<String> lore = new ArrayList<>();
        for (String questId : required) {
            lore.add((missing.contains(questId) ? "<red>Not complete: " : "<green>Complete: ")
                    + "<white>" + safe(questName(questId)));
        }
        return List.copyOf(lore);
    }

    private String lockedReason(Player player, PlayerProfile profile, QuestDefinition definition, JournalState state) {
        if (state != JournalState.LOCKED) return "Eligible";
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        if (!missing.isEmpty()) {
            String first = missing.iterator().next();
            return "Complete \"" + questName(first) + "\" first.";
        }
        if (!history.loaded(player.getUniqueId())
                && (!prerequisites.prerequisites(definition.id()).isEmpty() || !definition.scope().rotating())) {
            return "Quest history is still loading.";
        }
        var rules = definition.eligibility();
        boolean bypass = player.hasPermission("plexonquests.bypass.eligibility");
        if (!bypass && !rules.requiredPermission().isBlank() && !player.hasPermission(rules.requiredPermission())) {
            return "Unlock the required server access first.";
        }
        if (!bypass && !rules.rankCategories().isEmpty() && !rules.rankCategories().contains(profile.rankCategory())) {
            return "Reach an eligible rank first.";
        }
        if (!bypass && !rules.worlds().isEmpty() && !rules.worlds().contains(player.getWorld().getName())) {
            return "Travel to an eligible world first.";
        }
        if (!rules.requiredIntegrations().isEmpty()) {
            return "A required server feature is currently unavailable.";
        }
        return eligibility.evaluate(player, profile, definition).eligible()
                ? "Complete the listed requirements."
                : "Complete the listed requirements to unlock this quest.";
    }

    private List<String> rewardLore(QuestDefinition definition) {
        List<String> lore = new ArrayList<>();
        definition.rewards().entries().stream().limit(6)
                .forEach(reward -> lore.add("<gray>• " + reward.display()));
        if (definition.rewards().entries().size() > 6) {
            lore.add("<gray>+" + (definition.rewards().entries().size() - 6) + " more");
        }
        if (lore.isEmpty()) lore.add("<gray>No listed rewards");
        return List.copyOf(lore);
    }

    private List<String> rerollLore(Player player, QuestAssignment assignment) {
        int free = rerolls.freeRemaining(player, assignment.definition().scope());
        boolean bypassCost = player.hasPermission("plexonquests.bypass.reroll-cost");
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Replace this active quest with another from its pool.");
        lore.add("<red>Current progress on this quest will be lost.");
        if (free > 0 || bypassCost) {
            lore.add("<gray>Cost <green>Free");
        } else if (configs.snapshot().settings().rerolls().paidEnabled()) {
            lore.add("<gray>Cost <white>" + money(configs.snapshot().settings().rerolls().paidCost()));
        } else {
            lore.add("<gray>Cost <red>Unavailable");
        }
        lore.add("");
        lore.add("<dark_gray>Click to review the replacement before confirming");
        return List.copyOf(lore);
    }

    private List<String> rotationLore() {
        try {
            PeriodKeyService periods = new PeriodKeyService(configs.snapshot().settings().rotation());
            Instant now = Instant.now();
            var daily = periods.period(QuestScope.DAILY, now);
            var weekly = periods.period(QuestScope.WEEKLY, now);
            return List.of(
                    "<gray>Daily refresh <white>" + text.formatDuration(Duration.between(now, daily.endsAt())),
                    "<gray>Weekly refresh <white>" + text.formatDuration(Duration.between(now, weekly.endsAt())));
        } catch (RuntimeException ignored) {
            return List.of("<gray>Daily and Weekly assignments rotate automatically.");
        }
    }

    private String primaryObjective(QuestAssignment assignment) {
        if (assignment.objectives().isEmpty()) return "No objectives";
        ObjectiveProgress firstIncomplete = assignment.objectives().stream()
                .filter(objective -> !objective.complete())
                .findFirst()
                .orElse(assignment.objectives().getFirst());
        return firstIncomplete.definition().display();
    }

    private String rewardSummary(QuestDefinition definition) {
        List<String> rewards = definition.rewards().entries().stream()
                .limit(2)
                .map(reward -> reward.display())
                .toList();
        if (rewards.isEmpty()) return "No listed reward";
        String joined = String.join(" + ", rewards);
        int more = definition.rewards().entries().size() - rewards.size();
        return more > 0 ? joined + " +" + more + " more" : joined;
    }

    private String historyRewardSummary(String encoded) {
        if (encoded == null || encoded.isBlank()) return "Claimed";
        return encoded
                .replaceFirst("^[A-Za-z0-9_-]+=", "")
                .replaceAll(",[A-Za-z0-9_-]+=", " <dark_gray>+ ");
    }

    private String questName(String questId) {
        QuestDefinition definition = configs.snapshot().registry().quests().get(questId);
        return definition == null ? "another quest" : plainName(definition);
    }

    private String plainName(QuestDefinition definition) {
        return text.plain(text.parse(definition.display().name()));
    }

    private String assignmentMechanism(QuestScope scope) {
        return switch (scope) {
            case DAILY -> "May be assigned by the daily rotation.";
            case WEEKLY -> "May be assigned by the weekly rotation.";
            case MILESTONE -> "Starts automatically when its requirements are met.";
            case MANUAL -> "Assigned by server staff.";
        };
    }

    private Holder create(JournalNavigationContext context, String title) {
        return create(context, title, 54);
    }

    private Holder create(JournalNavigationContext context, String title, int size) {
        Holder holder = new Holder(context);
        holder.inventory = Bukkit.createInventory(holder, size, text.parse(title));
        ItemStack filler = items.create(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int i = 0; i < size; i++) holder.inventory.setItem(i, filler);
        return holder;
    }

    private void topNavigation(Holder holder, JournalView selected) {
        if (holder.inventory.getSize() < 54) return;
        nav(holder, 0, Material.COMPASS, "Journal Home", JournalView.HOME, selected, this::openOverview);
        nav(holder, 1, Material.WRITABLE_BOOK, "Active", JournalView.ACTIVE, selected, this::openActive);
        nav(holder, 2, Material.ENDER_EYE, "Eligible", JournalView.ELIGIBLE, selected, p -> openAvailable(p, 0, null));
        item(holder, 4, sectionMaterial(selected), "<gradient:#F6C85F:#E9A83A><bold>" + sectionLabel(selected) + "</bold></gradient>",
                List.of("<dark_gray>Quest Journal"), true, null);
        nav(holder, 6, Material.KNOWLEDGE_BOOK, "Completed", JournalView.COMPLETED, selected, this::openCompleted);
        nav(holder, 7, Material.LODESTONE, "Tracked", JournalView.TRACKED, selected, this::openTracked);
        nav(holder, 8, Material.PAPER, "Help", JournalView.HELP, selected, this::openHelp);
    }

    private void nav(Holder holder, int slot, Material material, String name, JournalView view, JournalView selected, Consumer<Player> open) {
        boolean active = view == selected;
        item(holder, slot, material,
                active ? "<gradient:#F6C85F:#E9A83A><bold>" + name + "</bold></gradient>" : "<white>" + name,
                List.of(active ? "<gold>Selected" : "<dark_gray>Click to open"), active,
                (p, c) -> open.accept(p));
    }

    private void listControls(
            Holder holder, int page, int pages, Consumer<Player> previous, Consumer<Player> next) {
        if (previous != null) {
            item(holder, 48, Material.ARROW, "<white><bold>Previous</bold>", List.of(
                    "<gray>Page " + page + " of " + pages), false, (p, c) -> previous.accept(p));
        }
        item(holder, 49, Material.MAP, "<white><bold>Page " + (page + 1) + " / " + Math.max(1, pages) + "</bold>", List.of(), false, null);
        if (next != null) {
            item(holder, 50, Material.ARROW, "<white><bold>Next</bold>", List.of(
                    "<gray>Page " + (page + 2) + " of " + pages), false, (p, c) -> next.accept(p));
        }
    }

    private void back(Holder holder, JournalNavigationContext parent) {
        item(holder, 45, Material.ARROW, "<white><bold>Back</bold>", List.of(
                "<gray>Return to the previous journal view."), false, (p, c) -> openContext(p, parent));
    }

    private void home(Holder holder) {
        item(holder, 45, Material.COMPASS, "<white><bold>Journal Home</bold>", List.of(
                "<dark_gray>Return to your quest summary"), false, (p, c) -> openOverview(p));
    }

    private void close(Holder holder) {
        int slot = holder.inventory.getSize() >= 53 ? 52 : holder.inventory.getSize() - 1;
        item(holder, slot, Material.BARRIER, "<red><bold>Close</bold>", List.of(), false, (p, c) -> p.closeInventory());
    }

    private void clearContent(Holder holder) {
        ItemStack filler = items.create(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int slot : CONTENT) {
            holder.inventory.setItem(slot, filler);
            holder.actions.remove(slot);
        }
    }

    private void item(Holder holder, int slot, Material material, String name, List<String> lore, boolean glow, Action action) {
        holder.inventory.setItem(slot, items.create(material, text.parse(name), components(lore), glow));
        if (action == null) holder.actions.remove(slot);
        else holder.actions.put(slot, action);
    }

    private List<Component> components(List<String> lines) {
        return lines.stream().map(line -> line == null || line.isBlank() ? Component.empty() : text.parse(line)).toList();
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile value = profiles.profile(player).orElse(null);
        if (value == null) player.sendMessage(text.parse("<yellow>Your quest profile is still loading."));
        return value;
    }

    private void stale(Player player, JournalNavigationContext parent) {
        player.sendMessage(text.parse("<yellow>This quest changed while the menu was open. <white>The journal has been refreshed."));
        openContext(player, parent);
    }

    private List<String> categories() {
        return configs.snapshot().registry().quests().values().stream()
                .filter(QuestDefinition::enabled)
                .map(QuestDefinition::category)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String nextCategory(String current) {
        List<String> categories = categories();
        if (categories.isEmpty()) return null;
        if (current == null) return categories.getFirst();
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(current)) {
                return i + 1 < categories.size() ? categories.get(i + 1) : null;
            }
        }
        return categories.getFirst();
    }

    private static int pages(int entries) {
        return Math.max(1, (entries + CONTENT.size() - 1) / CONTENT.size());
    }

    private static int clampPage(int requested, int pages) {
        return Math.max(0, Math.min(requested, Math.max(1, pages) - 1));
    }

    private static QuestScope nextScope(QuestScope scope) {
        if (scope == null) return QuestScope.DAILY;
        return switch (scope) {
            case DAILY -> QuestScope.WEEKLY;
            case WEEKLY -> QuestScope.MILESTONE;
            case MILESTONE -> QuestScope.MANUAL;
            case MANUAL -> null;
        };
    }

    private static String scopeLabel(QuestScope scope) {
        if (scope == null) return "All";
        return switch (scope) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MILESTONE -> "Milestone";
            case MANUAL -> "Assigned";
        };
    }

    private static String categoryLabel(String category) {
        return category == null ? "All" : pretty(category);
    }

    private static String sectionLabel(JournalView view) {
        return switch (view) {
            case HOME -> "Quest Journal";
            case ACTIVE -> "Active Quests";
            case ELIGIBLE -> "Eligible Quests";
            case COMPLETED -> "Completed Quests";
            case TRACKED -> "Tracked Quest";
            case HELP -> "Journal Help";
            case DETAILS -> "Quest Details";
            case REROLL_CONFIRMATION -> "Confirm Reroll";
        };
    }

    private static Material sectionMaterial(JournalView view) {
        return switch (view) {
            case HOME -> Material.COMPASS;
            case ACTIVE -> Material.WRITABLE_BOOK;
            case ELIGIBLE -> Material.ENDER_EYE;
            case COMPLETED -> Material.KNOWLEDGE_BOOK;
            case TRACKED -> Material.LODESTONE;
            case HELP -> Material.PAPER;
            case DETAILS -> Material.BOOK;
            case REROLL_CONFIRMATION -> Material.ENDER_PEARL;
        };
    }

    private static JournalView selectedForParent(JournalNavigationContext parent) {
        if (parent == null) return JournalView.HOME;
        return switch (parent.view()) {
            case ACTIVE -> JournalView.ACTIVE;
            case ELIGIBLE -> JournalView.ELIGIBLE;
            case COMPLETED -> JournalView.COMPLETED;
            case TRACKED -> JournalView.TRACKED;
            case HELP -> JournalView.HELP;
            default -> JournalView.HOME;
        };
    }

    private static Material stateMaterial(JournalState state) {
        return switch (state) {
            case AVAILABLE -> Material.LIME_DYE;
            case LOCKED, DISABLED -> Material.GRAY_DYE;
            case COOLDOWN, EXPIRED -> Material.CLOCK;
            case COMPLETABLE, COMPLETED -> Material.EMERALD;
            case TRACKED -> Material.LODESTONE;
            default -> Material.GOLD_INGOT;
        };
    }

    private static Material stateMaterial(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> Material.GOLD_INGOT;
            case COMPLETED, CLAIMED -> Material.EMERALD;
            case CLAIMING -> Material.CLOCK;
            case EXPIRED, CANCELLED -> Material.GRAY_DYE;
        };
    }

    private static String journalStateColor(JournalState state) {
        return switch (state) {
            case AVAILABLE -> "<aqua>";
            case ACTIVE, TRACKED -> "<gold>";
            case COMPLETABLE, COMPLETED -> "<green>";
            case LOCKED, DISABLED, COOLDOWN, EXPIRED -> "<gray>";
        };
    }

    private static String assignmentStateColor(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "<gold>";
            case COMPLETED, CLAIMED -> "<green>";
            case CLAIMING -> "<yellow>";
            case EXPIRED, CANCELLED -> "<gray>";
        };
    }

    private static String money(double amount) {
        return amount <= 0D ? "Free" : String.format(Locale.US, "%.2f", amount);
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "General";
        StringBuilder out = new StringBuilder();
        for (String part : value.replace('-', ' ').replace('_', ' ').split(" +")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>");
    }

    private record DiscoveryEntry(QuestDefinition definition, JournalState state, String reason) {}

    @FunctionalInterface
    private interface Action {
        void run(Player player, ClickType click);
    }

    private static final class Holder implements InventoryHolder {
        private final JournalNavigationContext context;
        private final Map<Integer, Action> actions = new HashMap<>();
        private final Set<String> submissions = new HashSet<>();
        private Inventory inventory;
        private long lastInteractionNanos;

        private Holder(JournalNavigationContext context) {
            this.context = context;
        }

        private boolean acceptInteraction() {
            long now = System.nanoTime();
            if (now - lastInteractionNanos < CLICK_DEBOUNCE_NANOS) return false;
            lastInteractionNanos = now;
            return true;
        }

        private boolean submit(String key) {
            return submissions.add(key);
        }

        private void release(String key) {
            submissions.remove(key);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

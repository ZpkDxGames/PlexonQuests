package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.gui.player.JournalFilter;
import com.zpkdxgames.plexonquests.gui.player.JournalNavigationContext;
import com.zpkdxgames.plexonquests.gui.player.JournalView;
import com.zpkdxgames.plexonquests.gui.player.QuestProgressPresentation;
import com.zpkdxgames.plexonquests.integration.skills.PlayerSkillsSnapshot;
import com.zpkdxgames.plexonquests.integration.skills.PlexonSkillsBridge;
import com.zpkdxgames.plexonquests.integration.skills.SkillSnapshot;
import com.zpkdxgames.plexonquests.persistence.HistoryEntry;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.rotation.PeriodKeyService;
import com.zpkdxgames.plexonquests.rotation.QuestCatalogService;
import com.zpkdxgames.plexonquests.rotation.QuestOffer;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.JournalStateResolver;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.QuestParticipationService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import com.zpkdxgames.plexonquests.service.SlotResolver;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
 * PlexonQuests 4.2 player journal. Presentation lives here; catalog, participation,
 * tracking, reward and persistence mutations remain authoritative in their services.
 */
public final class Phase2JournalService implements Listener {
    private static final List<Integer> CONTENT = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    private static final List<Integer> DETAIL_OBJECTIVES = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final List<Integer> DETAIL_REWARDS = List.of(28, 29, 30, 31, 32, 33, 34);
    private static final List<Integer> SKILL_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24);
    private static final long CLICK_DEBOUNCE_NANOS = Duration.ofMillis(250).toNanos();
    private static final DateTimeFormatter HISTORY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final StorageService storage;
    private final RewardService rewards;
    private final QuestCatalogService catalog;
    private final QuestParticipationService participation;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final QuestTrackingService tracking;
    private final PlexonSkillsBridge skills;
    private final TextService text;
    private final JournalStateResolver states;
    private final SlotResolver budgets = new SlotResolver();
    private final ItemFactory items = new ItemFactory();

    public Phase2JournalService(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            StorageService storage,
            RewardService rewards,
            QuestEligibilityService eligibility,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            QuestTrackingService tracking,
            QuestCatalogService catalog,
            QuestParticipationService participation,
            PlexonSkillsBridge skills,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.storage = storage;
        this.rewards = rewards;
        this.prerequisites = prerequisites;
        this.history = history;
        this.tracking = tracking;
        this.catalog = catalog;
        this.participation = participation;
        this.skills = skills;
        this.text = text;
        this.states = new JournalStateResolver(eligibility, prerequisites, history);
    }

    public JournalStateResolver stateResolver() {
        return states;
    }

    public List<String> activeQuestIds(Player player) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) return List.of();
        return profile.activeAssignments().stream()
                .map(assignment -> assignment.definition().id())
                .distinct().sorted().toList();
    }

    public void openOverview(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        tracking.reconcile(player, profile);
        PlayerSkillsSnapshot skillSnapshot = skills.snapshot(player.getUniqueId());
        Holder holder = create(JournalNavigationContext.home(), "<aqua><bold>Quest Board</bold>");
        topNavigation(holder, JournalView.HOME);

        holder.inventory.setItem(22, profileHead(player, profile, skillSnapshot));
        holder.actions.put(22, (p, c) -> openStatistics(p));

        QuestAssignment current = profile.activeAssignments().stream().findFirst().orElse(null);
        if (current == null) {
            item(holder, 20, Material.GRAY_DYE, "<gray><bold>No quest joined</bold>", List.of(
                    "<gray>Choose one quest to start tracking progress.",
                    "", "<aqua>Click to browse Available Quests"), false,
                    (p, c) -> openAvailable(p, 0, null));
        } else {
            QuestProgressPresentation progress = QuestProgressPresentation.of(
                    current.displayProgress().current(), current.displayProgress().required());
            item(holder, 20, current.definition().display().icon().material(), "<gold><bold>Current Quest</bold>", List.of(
                    "<white>" + safe(plainName(current.definition())),
                    text.progressBarMarkup(progress.percentage()) + " <white>" + progress.percentage() + "%",
                    "<gray>" + text.formatNumber(progress.current()) + " / " + text.formatNumber(progress.required()),
                    "", "<dark_gray>Click for current quest details"), true,
                    (p, c) -> openActive(p));
        }

        item(holder, 24, Material.LIME_CONCRETE, "<green><bold>Available Quests</bold>", List.of(
                "<gray>Daily, Weekly and Milestone offers.",
                budgetLine(player, profile, QuestScope.DAILY),
                budgetLine(player, profile, QuestScope.WEEKLY),
                "", "<dark_gray>Click to choose a quest"), false,
                (p, c) -> openAvailable(p, 0, null));

        item(holder, 29, Material.KNOWLEDGE_BOOK, "<light_purple><bold>Completed</bold>", List.of(
                "<gray>Lifetime completed <white>" + profile.completedTotal(),
                "<gray>Claimable rewards <green>" + profile.claimableCount(),
                "", "<dark_gray>Click for history"), false, (p, c) -> openCompleted(p));

        List<String> skillsLore = new ArrayList<>();
        if (!skillSnapshot.available()) skillsLore.add("<gray>Skills unavailable");
        else if (!skillSnapshot.ready()) skillsLore.add("<yellow>Skill profile loading...");
        else skillsLore.add("<gray>Total skill level <aqua>" + skillSnapshot.totalLevel());
        skillsLore.add("");
        skillsLore.add("<dark_gray>Click for PlexonSkills progress");
        item(holder, 31, Material.EXPERIENCE_BOTTLE, "<aqua><bold>Skills</bold>", skillsLore, false,
                (p, c) -> openStatistics(p));

        item(holder, 33, Material.PAPER, "<white><bold>Help</bold>", List.of(
                "<gray>Players choose quests explicitly in 4.2.",
                "<gray>Only one normal quest runs at a time by default.",
                "", "<dark_gray>Click for help"), false, (p, c) -> openHelp(p));

        if (participation.legacyOverflow(profile)) {
            item(holder, 40, Material.ORANGE_DYE, "<gold><bold>Legacy active quests</bold>", List.of(
                    "<gray>Finish these before joining a new quest.",
                    "<gray>Active legacy assignments <white>" + profile.activeAssignments().size()), true,
                    (p, c) -> openActive(p));
        } else {
            item(holder, 40, Material.CLOCK, "<white><bold>Resets</bold>", rotationLore(), false, null);
        }
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openActive(Player player) {
        openActive(player, 0, null);
    }

    public void openActive(Player player, int page, QuestScope scope) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        List<QuestAssignment> active = profile.activeAssignments().stream()
                .filter(a -> scope == null || a.definition().scope() == scope)
                .toList();
        if (active.size() == 1 && !participation.legacyOverflow(profile)) {
            openDetails(player, active.getFirst(), JournalNavigationContext.active(0, scope));
            return;
        }
        Holder holder = create(JournalNavigationContext.active(page, scope), "<gold><bold>Current Quest</bold>");
        topNavigation(holder, JournalView.ACTIVE);
        if (active.isEmpty()) {
            item(holder, 22, Material.GRAY_DYE, "<gray><bold>No quest joined</bold>", List.of(
                    "<gray>No gameplay objectives are active for you.",
                    "", "<green>Click to browse Available Quests"), false,
                    (p, c) -> openAvailable(p, 0, null));
        } else {
            item(holder, 4, Material.ORANGE_DYE, "<gold><bold>Legacy active quests</bold>", List.of(
                    "<gray>Finish these before joining a new quest."), true, null);
            int pages = pages(active.size());
            int actualPage = clampPage(page, pages);
            int offset = actualPage * CONTENT.size();
            for (int i = 0; i < CONTENT.size() && offset + i < active.size(); i++) {
                QuestAssignment assignment = active.get(offset + i);
                int slot = CONTENT.get(i);
                holder.inventory.setItem(slot, assignmentCard(assignment));
                holder.actions.put(slot, (p, c) -> openDetails(p, assignment, JournalNavigationContext.active(actualPage, scope)));
            }
            listControls(holder, actualPage, pages,
                    actualPage > 0 ? p -> openActive(p, actualPage - 1, scope) : null,
                    actualPage + 1 < pages ? p -> openActive(p, actualPage + 1, scope) : null);
        }
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    public void openAvailable(Player player, int page, String category) {
        openAvailable(player, page, category, null);
    }

    public void openAvailable(Player player, int page, String category, QuestScope scope) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        JournalNavigationContext context = JournalNavigationContext.eligible(page, JournalFilter.ALL, category, scope);
        Holder holder = create(context, "<green><bold>Available Quests</bold>");
        topNavigation(holder, JournalView.ELIGIBLE);
        item(holder, 22, Material.CLOCK, "<gray><bold>Loading quest board...</bold>", List.of(
                "<dark_gray>Offers are stable for this rotation period."), false, null);
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);

        offers(player, profile, scope).whenComplete((offers, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
            clearContent(holder);
            if (failure != null) {
                item(holder, 22, Material.RED_DYE, "<red><bold>Quest board unavailable</bold>", List.of(
                        "<gray>Your quest data was not changed.", "<dark_gray>Close and try again shortly."), false, null);
                return;
            }
            List<QuestOffer> filtered = offers.stream()
                    .filter(offer -> category == null || offer.definition().category().equalsIgnoreCase(category))
                    .sorted(Comparator.comparing((QuestOffer o) -> offerOrder(o.state()))
                            .thenComparing(o -> plainName(o.definition()), String.CASE_INSENSITIVE_ORDER))
                    .toList();
            int pages = pages(filtered.size());
            int actualPage = clampPage(page, pages);
            int offset = actualPage * CONTENT.size();
            for (int i = 0; i < CONTENT.size() && offset + i < filtered.size(); i++) {
                QuestOffer offer = filtered.get(offset + i);
                int slot = CONTENT.get(i);
                holder.inventory.setItem(slot, offerCard(player, offer));
                holder.actions.put(slot, (p, c) -> openOfferDetails(p, offer, context.withPage(actualPage)));
            }
            if (filtered.isEmpty()) {
                item(holder, 22, Material.GRAY_DYE, "<gray><bold>No available offers</bold>", List.of(
                        "<gray>Try another scope or wait for the next rotation."), false, null);
            }
            listControls(holder, actualPage, pages,
                    actualPage > 0 ? p -> openAvailable(p, actualPage - 1, category, scope) : null,
                    actualPage + 1 < pages ? p -> openAvailable(p, actualPage + 1, category, scope) : null);
            item(holder, 47, Material.COMPASS, "<aqua><bold>Scope: " + scopeLabel(scope) + "</bold>", List.of(
                    "<gray>All • Daily • Weekly • Milestone", "", "<dark_gray>Click to change scope"), false,
                    (p, c) -> openAvailable(p, 0, category, nextBrowseScope(scope)));
        }));
    }

    public void openCategories(Player player) {
        openAvailable(player, 0, null);
    }

    public void openTracked(Player player) {
        openActive(player);
    }

    public void openCompleted(Player player) {
        openCompleted(player, 0);
    }

    private void openCompleted(Player player, int page) {
        if (!player.hasPermission("plexonquests.history")) {
            player.sendMessage(text.parse("<red>Completed quest history is not available to you."));
            openOverview(player);
            return;
        }
        int pageSize = CONTENT.size();
        Holder holder = create(JournalNavigationContext.completed(page), "<light_purple><bold>Completed Quests</bold>");
        topNavigation(holder, JournalView.COMPLETED);
        item(holder, 22, Material.CLOCK, "<gray><bold>Loading history...</bold>", List.of(), false, null);
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
        storage.history(player.getUniqueId(), pageSize + 1, page * pageSize).whenComplete((entries, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) return;
                    clearContent(holder);
                    if (failure != null) {
                        item(holder, 22, Material.RED_DYE, "<red><bold>History unavailable</bold>", List.of(
                                "<gray>Your quest data was not changed."), false, null);
                        return;
                    }
                    boolean hasNext = entries.size() > pageSize;
                    List<HistoryEntry> visible = hasNext ? entries.subList(0, pageSize) : entries;
                    for (int i = 0; i < visible.size(); i++) holder.inventory.setItem(CONTENT.get(i), historyCard(visible.get(i)));
                    if (visible.isEmpty()) item(holder, 22, Material.GRAY_DYE, "<gray><bold>No completed quests yet</bold>", List.of(), false, null);
                    listControls(holder, page, hasNext ? page + 2 : page + 1,
                            page > 0 ? p -> openCompleted(p, page - 1) : null,
                            hasNext ? p -> openCompleted(p, page + 1) : null);
                }));
    }

    /** Compatibility entry for /quests statistics and /quests stats; now the Skills page. */
    public void openStatistics(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        PlayerSkillsSnapshot snapshot = skills.snapshot(player.getUniqueId());
        Holder holder = create(new JournalNavigationContext(JournalView.HELP, 0, JournalFilter.ALL, null, null),
                "<aqua><bold>Quest Skills</bold>", 36);
        holder.inventory.setItem(4, profileHead(player, profile, snapshot));
        if (!snapshot.available()) {
            item(holder, 13, Material.GRAY_DYE, "<gray><bold>Skills unavailable</bold>", List.of(
                    "<gray>PlexonSkills is optional; quests remain fully functional."), false, null);
        } else if (!snapshot.ready()) {
            item(holder, 13, Material.CLOCK, "<yellow><bold>Skill profile loading...</bold>", List.of(
                    "<gray>Open this page again after your skill profile is ready."), false, null);
        } else {
            int count = Math.min(SKILL_SLOTS.size(), snapshot.skills().size());
            for (int i = 0; i < count; i++) {
                SkillSnapshot skill = snapshot.skills().get(i);
                int percent = (int) Math.round(Math.max(0D, Math.min(1D, skill.progress())) * 100D);
                item(holder, SKILL_SLOTS.get(i), skill.icon(), "<aqua><bold>" + safe(skill.displayName()) + "</bold>", List.of(
                        "<gray>Level <white>" + skill.level(),
                        "<gray>Total XP <white>" + text.formatNumber(skill.totalXp()),
                        "<gray>Next level XP <white>" + text.formatNumber(skill.xpForNextLevel()),
                        text.progressBarMarkup(percent) + " <white>" + percent + "%"), false, null);
            }
        }
        item(holder, 27, Material.COMPASS, "<white><bold>Journal Home</bold>", List.of(), false, (p, c) -> openOverview(p));
        item(holder, 35, Material.BARRIER, "<red><bold>Close</bold>", List.of(), false, (p, c) -> p.closeInventory());
        player.openInventory(holder.inventory);
    }

    public void openHelp(Player player) {
        if (profile(player) == null) return;
        Holder holder = create(new JournalNavigationContext(JournalView.HELP, 0, JournalFilter.ALL, null, null),
                "<white><bold>Quest Board Help</bold>");
        topNavigation(holder, JournalView.HELP);
        item(holder, 19, Material.LIME_CONCRETE, "<green><bold>Choose a quest</bold>", List.of(
                "<gray>Open Available Quests, inspect one, then choose Join Quest."), false, null);
        item(holder, 21, Material.GOLD_INGOT, "<gold><bold>One running quest</bold>", List.of(
                "<gray>Only ACTIVE assignments occupy the default runtime slot.",
                "<gray>Completed-but-unclaimed quests do not block a new join."), false, null);
        item(holder, 23, Material.CLOCK, "<aqua><bold>Period budgets</bold>", List.of(
                "<gray>Daily and Weekly participation limits remain controlled.",
                "<gray>Abandoning consumes the period budget by default."), false, null);
        item(holder, 25, Material.EXPERIENCE_BOTTLE, "<aqua><bold>PlexonSkills</bold>", List.of(
                "<gray>Skills are shown for context only.",
                "<gray>They do not become new quest requirements in 4.2."), false, null);
        item(holder, 31, Material.RED_CONCRETE, "<red><bold>Abandon</bold>", List.of(
                "<gray>Current quest progress is lost after explicit confirmation."), false, null);
        home(holder);
        close(holder);
        player.openInventory(holder.inventory);
    }

    /** Rerolls are retained for API/source compatibility but removed from normal player UX. */
    public void openReroll(Player player, QuestAssignment assignment) {
        player.sendMessage(text.parse("<gray>Rerolls are no longer needed — choose a quest from <green>Available Quests</green>."));
        openAvailable(player, 0, null, assignment.definition().scope());
    }

    private void openOfferDetails(Player player, QuestOffer offer, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestAssignment active = profile.assignments().stream()
                .filter(a -> a.definition().id().equals(offer.definition().id()))
                .filter(a -> a.state() == AssignmentState.ACTIVE || a.state() == AssignmentState.COMPLETED || a.state() == AssignmentState.CLAIMING)
                .findFirst().orElse(null);
        if (active != null) {
            openDetails(player, active, parent);
            return;
        }
        QuestDefinition definition = offer.definition();
        Holder holder = create(parent, "<aqua><bold>Quest Details</bold>");
        item(holder, 4, definition.display().icon().material(), definition.display().name(), List.of(
                "<dark_gray>" + scopeLabel(definition.scope()) + " • " + safe(pretty(definition.category())),
                "", "<gray>" + definition.display().shortDescription(),
                "", "<gray>Status " + offerColor(offer.state()) + offerLabel(offer.state())), offer.joinable(), null);
        renderDefinitionObjectives(holder, definition);
        item(holder, 22, Material.TRIPWIRE_HOOK, "<yellow><bold>Requirements</bold>", prerequisiteLore(player, definition), false, null);
        List<String> context = new ArrayList<>();
        relevantSkill(definition, skills.snapshot(player.getUniqueId())).ifPresent(skill ->
                context.add("<gray>Relevant skill <aqua>" + safe(skill.displayName()) + " <white>Lv. " + skill.level()));
        context.add(offer.expiresAt() == null ? "<gray>No scheduled expiry" :
                "<gray>Time remaining <white>" + text.formatDuration(Duration.between(Instant.now(), offer.expiresAt())));
        item(holder, 24, Material.CLOCK, "<aqua><bold>Availability</bold>", context, false, null);
        renderDefinitionRewards(holder, definition);
        back(holder, parent);
        if (offer.joinable()) {
            List<String> joinLore = new ArrayList<>();
            joinLore.add("<gray>Only one normal quest can run at a time.");
            if (definition.scope().rotating()) joinLore.add(budgetLine(player, profile, definition.scope()));
            joinLore.add("");
            joinLore.add("<green>Click to join this quest");
            item(holder, 53, Material.LIME_CONCRETE, "<green><bold>Join Quest</bold>", joinLore, true,
                    (p, c) -> join(p, offer, holder));
        } else {
            item(holder, 53, Material.GRAY_CONCRETE, "<gray><bold>" + offerLabel(offer.state()) + "</bold>", List.of(
                    "<gray>" + safe(offer.lockedReason())), false, null);
        }
        close(holder);
        player.openInventory(holder.inventory);
    }

    private void join(Player player, QuestOffer offer, Holder holder) {
        if (!holder.submit("join")) return;
        participation.join(player, offer).whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (failure != null) {
                player.sendMessage(text.parse("<red>Quest join failed safely; no quest was started."));
                return;
            }
            switch (result) {
                case JOINED -> {
                    player.sendMessage(text.parse("<green>Quest joined. <white>Only its active objectives are now tracked."));
                    openActive(player);
                }
                case ACTIVE_LIMIT -> player.sendMessage(text.parse("<yellow>Finish or abandon your current quest first."));
                case PERIOD_LIMIT -> player.sendMessage(text.parse("<yellow>Your participation budget for this period is exhausted."));
                case DUPLICATE -> player.sendMessage(text.parse("<yellow>You already participated in this quest during this period."));
                case IN_FLIGHT -> player.sendMessage(text.parse("<yellow>A quest join is already being processed."));
                case STALE_OFFER -> {
                    player.sendMessage(text.parse("<yellow>This offer expired or changed. The board was refreshed."));
                    openAvailable(player, 0, null, offer.scope());
                }
                default -> player.sendMessage(text.parse("<red>This quest cannot be joined right now."));
            }
        }));
    }

    private void openDetails(Player player, QuestAssignment assignment, JournalNavigationContext parent) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestAssignment live = profile.assignment(assignment.id()).orElse(null);
        if (live == null) {
            stale(player, parent);
            return;
        }
        Holder holder = create(parent, "<gold><bold>Current Quest</bold>");
        QuestProgressPresentation total = QuestProgressPresentation.of(live.displayProgress().current(), live.displayProgress().required());
        item(holder, 4, live.definition().display().icon().material(), live.definition().display().name(), List.of(
                "<dark_gray>" + scopeLabel(live.definition().scope()) + " • " + safe(pretty(live.definition().category())),
                "", text.progressBarMarkup(total.percentage()) + " <white>" + total.percentage() + "%",
                "<gray>" + text.formatNumber(total.current()) + " / " + text.formatNumber(total.required()),
                "<gray>Status " + assignmentColor(live.state()) + assignmentLabel(live.state())), true, null);

        int count = Math.min(DETAIL_OBJECTIVES.size(), live.objectives().size());
        for (int i = 0; i < count; i++) {
            ObjectiveProgress objective = live.objectives().get(i);
            QuestProgressPresentation progress = QuestProgressPresentation.of(objective.current(), objective.required());
            item(holder, DETAIL_OBJECTIVES.get(i), Material.TARGET, "<white><bold>" + safe(objective.definition().display()) + "</bold>", List.of(
                    text.progressBarMarkup(progress.percentage()) + " <white>" + progress.percentage() + "%",
                    "<gray>" + text.formatNumber(progress.current()) + " / " + text.formatNumber(progress.required())), objective.complete(), null);
        }
        renderDefinitionRewards(holder, live.definition());
        List<String> context = new ArrayList<>();
        relevantSkill(live.definition(), skills.snapshot(player.getUniqueId())).ifPresent(skill ->
                context.add("<gray>Relevant skill <aqua>" + safe(skill.displayName()) + " <white>Lv. " + skill.level()));
        live.expiresAt().ifPresent(expiry -> context.add("<gray>Time remaining <white>" + text.formatDuration(Duration.between(Instant.now(), expiry))));
        item(holder, 24, Material.CLOCK, "<aqua><bold>Quest Context</bold>", context, false, null);

        back(holder, parent);
        if (live.state() == AssignmentState.COMPLETED && player.hasPermission("plexonquests.claim")) {
            item(holder, 53, Material.EMERALD, "<green><bold>Claim Reward</bold>", List.of(
                    "<gray>Receive <white>" + rewardSummary(live.definition()), "", "<green>Click once to claim"), true,
                    (p, c) -> claim(p, live.id(), holder));
        } else if (live.state() == AssignmentState.ACTIVE && configs.snapshot().settings().participation().allowAbandon()) {
            item(holder, 53, Material.RED_CONCRETE, "<red><bold>Abandon Quest</bold>", List.of(
                    "<red>Progress will be lost.",
                    configs.snapshot().settings().participation().abandonConsumesPeriodBudget()
                            ? "<gray>This still consumes the period participation budget."
                            : "<gray>Period participation will be recalculated by policy.",
                    "", "<dark_gray>Click to review confirmation"), false,
                    (p, c) -> openAbandonConfirmation(p, live, parent));
        }
        close(holder);
        player.openInventory(holder.inventory);
    }

    private void openAbandonConfirmation(Player player, QuestAssignment assignment, JournalNavigationContext parent) {
        Holder holder = create(parent, "<red><bold>Abandon Quest?</bold>", 27);
        item(holder, 4, assignment.definition().display().icon().material(), assignment.definition().display().name(), List.of(
                "<red>All progress on this running quest will be lost.",
                "<gray>You cannot rejoin the same quest during this period."), false, null);
        item(holder, 11, Material.RED_CONCRETE, "<red><bold>Confirm Abandon</bold>", List.of(
                configs.snapshot().settings().participation().abandonConsumesPeriodBudget()
                        ? "<gray>The period participation remains consumed."
                        : "<gray>Participation usage follows configured policy.",
                "", "<red>Click once to abandon"), false, (p, c) -> {
                    if (!holder.submit("abandon")) return;
                    QuestParticipationService.LeaveResult result = participation.abandon(p, assignment.id());
                    if (result == QuestParticipationService.LeaveResult.ABANDONED) {
                        p.sendMessage(text.parse("<gray>Quest abandoned. Its objective interest was removed."));
                        openAvailable(p, 0, null, assignment.definition().scope());
                    } else {
                        p.sendMessage(text.parse("<yellow>The quest changed before abandonment could complete."));
                        openOverview(p);
                    }
                });
        item(holder, 15, Material.LIME_CONCRETE, "<green><bold>Keep Quest</bold>", List.of(
                "<gray>Return without changing progress."), false, (p, c) -> openDetails(p, assignment, parent));
        item(holder, 22, Material.BARRIER, "<red><bold>Close</bold>", List.of(), false, (p, c) -> p.closeInventory());
        player.openInventory(holder.inventory);
    }

    private void claim(Player player, UUID assignmentId, Holder holder) {
        if (!holder.submit("claim")) return;
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment live = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (live == null || live.state() != AssignmentState.COMPLETED) {
            player.sendMessage(text.parse("<yellow>This quest changed while the menu was open."));
            openOverview(player);
            return;
        }
        player.closeInventory();
        rewards.claim(player, live);
    }

    private CompletableFuture<List<QuestOffer>> offers(Player player, PlayerProfile profile, QuestScope scope) {
        if (scope != null) return catalog.offers(player, profile, scope);
        CompletableFuture<List<QuestOffer>> daily = catalog.offers(player, profile, QuestScope.DAILY);
        CompletableFuture<List<QuestOffer>> weekly = catalog.offers(player, profile, QuestScope.WEEKLY);
        CompletableFuture<List<QuestOffer>> milestones = catalog.offers(player, profile, QuestScope.MILESTONE);
        return CompletableFuture.allOf(daily, weekly, milestones).thenApply(ignored -> {
            List<QuestOffer> combined = new ArrayList<>();
            combined.addAll(daily.join());
            combined.addAll(weekly.join());
            combined.addAll(milestones.join());
            return List.copyOf(combined);
        });
    }

    private ItemStack profileHead(Player player, PlayerProfile profile, PlayerSkillsSnapshot snapshot) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Player <white>" + safe(player.getName()));
        if (!snapshot.available()) lore.add("<gray>Skills <dark_gray>Unavailable");
        else if (!snapshot.ready()) lore.add("<gray>Skills <yellow>Loading...");
        else lore.add("<gray>Total skill level <aqua>" + snapshot.totalLevel());
        lore.add("<gray>Active quests <gold>" + profile.activeAssignments().size() + "<gray> / <white>"
                + configs.snapshot().settings().participation().maximumActiveQuests());
        lore.add(budgetLine(player, profile, QuestScope.DAILY));
        lore.add(budgetLine(player, profile, QuestScope.WEEKLY));
        lore.add("<gray>Completed total <light_purple>" + profile.completedTotal());
        lore.add("<gray>Claimable rewards <green>" + profile.claimableCount());
        if (participation.legacyOverflow(profile)) {
            lore.add("");
            lore.add("<gold>Legacy active quests — finish these before joining a new quest.");
        }
        return items.playerHead(player, text.parse("<aqua><bold>" + safe(player.getName()) + "</bold>"), components(lore), true);
    }

    private ItemStack assignmentCard(QuestAssignment assignment) {
        QuestProgressPresentation progress = QuestProgressPresentation.of(
                assignment.displayProgress().current(), assignment.displayProgress().required());
        return items.create(assignment.definition().display().icon().material(), text.parse(assignment.definition().display().name()), components(List.of(
                "<dark_gray>" + scopeLabel(assignment.definition().scope()) + " • " + safe(pretty(assignment.definition().category())),
                "", "<gray>" + primaryObjective(assignment),
                text.progressBarMarkup(progress.percentage()) + " <white>" + progress.percentage() + "%",
                "<gray>" + text.formatNumber(progress.current()) + " / " + text.formatNumber(progress.required()),
                "<gray>Reward <white>" + rewardSummary(assignment.definition()),
                "", "<dark_gray>Click for details")), true);
    }

    private ItemStack offerCard(Player player, QuestOffer offer) {
        QuestDefinition definition = offer.definition();
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + scopeLabel(definition.scope()) + " • " + safe(pretty(definition.category())));
        lore.add("<gray>Status " + offerColor(offer.state()) + offerLabel(offer.state()));
        if (!definition.display().shortDescription().isBlank()) {
            lore.add("");
            lore.add("<gray>" + definition.display().shortDescription());
        }
        relevantSkill(definition, skills.snapshot(player.getUniqueId())).ifPresent(skill ->
                lore.add("<gray>Skill <aqua>" + safe(skill.displayName()) + " <white>Lv. " + skill.level()));
        lore.add("<gray>Objective <white>" + primaryObjective(definition));
        lore.add("<gray>Reward <white>" + rewardSummary(definition));
        if (offer.expiresAt() != null) lore.add("<gray>Reset <white>" + text.formatDuration(Duration.between(Instant.now(), offer.expiresAt())));
        if (!offer.lockedReason().isBlank()) lore.add("<gray>Reason <white>" + safe(offer.lockedReason()));
        lore.add("");
        lore.add("<dark_gray>Click to inspect");
        return items.create(definition.display().icon().material(), text.parse(definition.display().name()), components(lore), offer.joinable());
    }

    private ItemStack historyCard(HistoryEntry entry) {
        String when = entry.claimedAt() != null ? HISTORY_DATE.format(entry.claimedAt())
                : entry.completedAt() != null ? HISTORY_DATE.format(entry.completedAt()) : "Completed";
        return items.create(Material.BOOK, text.parse(entry.displayName()), components(List.of(
                "<dark_gray>" + scopeLabel(entry.scope()) + " • " + safe(pretty(entry.rarity())),
                "", "<gray>Status <green>" + assignmentLabel(entry.state()),
                "<gray>Completed <white>" + safe(when),
                entry.rewardSummary() == null || entry.rewardSummary().isBlank()
                        ? "<gray>Reward <white>Claimed" : "<gray>Reward <white>" + safe(entry.rewardSummary()))), false);
    }

    private void renderDefinitionObjectives(Holder holder, QuestDefinition definition) {
        List<?> objectives = definition.objectives().values().stream().toList();
        int count = Math.min(DETAIL_OBJECTIVES.size(), objectives.size());
        for (int i = 0; i < count; i++) {
            var objective = definition.objectives().values().stream().toList().get(i);
            item(holder, DETAIL_OBJECTIVES.get(i), Material.TARGET, "<white><bold>" + safe(objective.display()) + "</bold>", List.of(
                    "<gray>Required <white>" + text.formatNumber(objective.amount())), false, null);
        }
    }

    private void renderDefinitionRewards(Holder holder, QuestDefinition definition) {
        int count = Math.min(DETAIL_REWARDS.size(), definition.rewards().entries().size());
        for (int i = 0; i < count; i++) {
            var reward = definition.rewards().entries().get(i);
            item(holder, DETAIL_REWARDS.get(i), Material.CHEST, "<green><bold>Reward</bold>", List.of(
                    "<white>" + reward.display()), false, null);
        }
        if (count == 0) item(holder, 31, Material.CHEST, "<gray><bold>No listed rewards</bold>", List.of(), false, null);
    }

    private java.util.Optional<SkillSnapshot> relevantSkill(QuestDefinition definition, PlayerSkillsSnapshot snapshot) {
        if (!snapshot.available() || !snapshot.ready() || !configs.snapshot().settings().skillsDisplay().enabled()) {
            return java.util.Optional.empty();
        }
        String mapped = configs.snapshot().settings().skillsDisplay().categoryMap()
                .get(definition.category().toLowerCase(Locale.ROOT));
        return snapshot.skill(mapped);
    }

    private List<String> prerequisiteLore(Player player, QuestDefinition definition) {
        Set<String> required = prerequisites.prerequisites(definition.id());
        if (required.isEmpty()) return List.of("<green>No quest prerequisites");
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<String> lore = new ArrayList<>();
        for (String questId : required) lore.add((missing.contains(questId) ? "<red>Not complete: " : "<green>Complete: ")
                + "<white>" + safe(questName(questId)));
        return List.copyOf(lore);
    }

    private String budgetLine(Player player, PlayerProfile profile, QuestScope scope) {
        if (!scope.rotating()) return "";
        PeriodKeyService periods = new PeriodKeyService(configs.snapshot().settings().rotation());
        String key = periods.period(scope, Instant.now()).key();
        int limit = budgets.resolveParticipationBudget(player, scope, profile.rankCategory(), configs.snapshot().settings());
        int used = profile.periodParticipationCount(scope, key);
        return "<gray>" + scopeLabel(scope) + " participation <white>" + used + "<gray> / <white>" + limit;
    }

    private List<String> rotationLore() {
        PeriodKeyService periods = new PeriodKeyService(configs.snapshot().settings().rotation());
        Instant now = Instant.now();
        return List.of(
                "<gray>Daily reset <white>" + text.formatDuration(Duration.between(now, periods.period(QuestScope.DAILY, now).endsAt())),
                "<gray>Weekly reset <white>" + text.formatDuration(Duration.between(now, periods.period(QuestScope.WEEKLY, now).endsAt())));
    }

    private String primaryObjective(QuestAssignment assignment) {
        if (assignment.objectives().isEmpty()) return "No objectives";
        ObjectiveProgress objective = assignment.objectives().stream().filter(o -> !o.complete()).findFirst().orElse(assignment.objectives().getFirst());
        return objective.definition().display();
    }

    private String primaryObjective(QuestDefinition definition) {
        return definition.objectives().values().stream().findFirst()
                .map(objective -> objective.display() + " ×" + text.formatNumber(objective.amount()))
                .orElse("No objectives");
    }

    private String rewardSummary(QuestDefinition definition) {
        List<String> values = definition.rewards().entries().stream().limit(2).map(reward -> reward.display()).toList();
        if (values.isEmpty()) return "No listed reward";
        int more = definition.rewards().entries().size() - values.size();
        return String.join(" + ", values) + (more > 0 ? " +" + more + " more" : "");
    }

    private String questName(String questId) {
        QuestDefinition definition = configs.snapshot().registry().quests().get(questId);
        return definition == null ? "another quest" : plainName(definition);
    }

    private String plainName(QuestDefinition definition) {
        return text.plain(text.parse(definition.display().name()));
    }

    private void topNavigation(Holder holder, JournalView selected) {
        if (holder.inventory.getSize() < 54) return;
        nav(holder, 0, Material.COMPASS, "Home", JournalView.HOME, selected, this::openOverview);
        nav(holder, 1, Material.GOLD_INGOT, "Current", JournalView.ACTIVE, selected, this::openActive);
        nav(holder, 2, Material.LIME_CONCRETE, "Available", JournalView.ELIGIBLE, selected, p -> openAvailable(p, 0, null));
        item(holder, 4, Material.PLAYER_HEAD, "<aqua><bold>Quest Board</bold>", List.of("<dark_gray>PlexonQuests 4.2"), true, null);
        nav(holder, 6, Material.KNOWLEDGE_BOOK, "Completed", JournalView.COMPLETED, selected, this::openCompleted);
        nav(holder, 7, Material.EXPERIENCE_BOTTLE, "Skills", JournalView.HELP, selected, this::openStatistics);
        nav(holder, 8, Material.PAPER, "Help", JournalView.HELP, selected, this::openHelp);
    }

    private void nav(Holder holder, int slot, Material material, String name, JournalView view, JournalView selected, Consumer<Player> open) {
        boolean active = view == selected;
        item(holder, slot, material, active ? "<aqua><bold>" + name + "</bold>" : "<white>" + name,
                List.of(active ? "<aqua>Selected" : "<dark_gray>Click to open"), active, (p, c) -> open.accept(p));
    }

    private Holder create(JournalNavigationContext context, String title) {
        return create(context, title, 54);
    }

    private Holder create(JournalNavigationContext context, String title, int size) {
        Holder holder = new Holder(context);
        holder.inventory = Bukkit.createInventory(holder, size, text.parse(title));
        ItemStack filler = filler();
        for (int i = 0; i < size; i++) holder.inventory.setItem(i, filler);
        return holder;
    }

    private void listControls(Holder holder, int page, int pages, Consumer<Player> previous, Consumer<Player> next) {
        if (previous != null) item(holder, 48, Material.ARROW, "<white><bold>Previous</bold>", List.of(), false, (p, c) -> previous.accept(p));
        item(holder, 49, Material.MAP, "<white><bold>Page " + (page + 1) + " / " + Math.max(1, pages) + "</bold>", List.of(), false, null);
        if (next != null) item(holder, 50, Material.ARROW, "<white><bold>Next</bold>", List.of(), false, (p, c) -> next.accept(p));
    }

    private void back(Holder holder, JournalNavigationContext parent) {
        item(holder, 45, Material.ARROW, "<white><bold>Back</bold>", List.of(), false, (p, c) -> openContext(p, parent));
    }

    private void home(Holder holder) {
        item(holder, 45, Material.COMPASS, "<white><bold>Quest Board Home</bold>", List.of(), false, (p, c) -> openOverview(p));
    }

    private void close(Holder holder) {
        int slot = holder.inventory.getSize() >= 53 ? 52 : holder.inventory.getSize() - 1;
        item(holder, slot, Material.BARRIER, "<red><bold>Close</bold>", List.of(), false, (p, c) -> p.closeInventory());
    }

    private void clearContent(Holder holder) {
        ItemStack filler = filler();
        for (int slot : CONTENT) {
            holder.inventory.setItem(slot, filler);
            holder.actions.remove(slot);
        }
    }

    private ItemStack filler() {
        String configured = configs.snapshot().menus().string(
                "common.filler.material", Material.BLACK_STAINED_GLASS_PANE.name());
        Material material;
        try {
            material = Material.valueOf(configured.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        return items.create(material, Component.empty(), List.of(), false);
    }

    private void item(Holder holder, int slot, Material material, String name, List<String> lore, boolean glow, Action action) {
        holder.inventory.setItem(slot, items.create(material, text.parse(name), components(lore), glow));
        if (action == null) holder.actions.remove(slot); else holder.actions.put(slot, action);
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
        player.sendMessage(text.parse("<yellow>This quest changed while the menu was open. <white>The board has been refreshed."));
        openContext(player, parent);
    }

    private void openContext(Player player, JournalNavigationContext context) {
        if (context == null) { openOverview(player); return; }
        switch (context.view()) {
            case HOME -> openOverview(player);
            case ACTIVE, TRACKED -> openActive(player, context.page(), context.scope());
            case ELIGIBLE -> openAvailable(player, context.page(), context.category(), context.scope());
            case COMPLETED -> openCompleted(player, context.page());
            case HELP -> openHelp(player);
            case DETAILS, REROLL_CONFIRMATION -> openOverview(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !MenuInteractionRouter.supportsAction(event.getClick())) return;
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

    private static int pages(int entries) { return Math.max(1, (entries + CONTENT.size() - 1) / CONTENT.size()); }
    private static int clampPage(int requested, int pages) { return Math.max(0, Math.min(requested, Math.max(1, pages) - 1)); }
    private static int offerOrder(QuestOffer.State state) { return state == QuestOffer.State.AVAILABLE ? 0 : state == QuestOffer.State.ACTIVE ? 1 : 2; }

    private static QuestScope nextBrowseScope(QuestScope scope) {
        if (scope == null) return QuestScope.DAILY;
        return switch (scope) {
            case DAILY -> QuestScope.WEEKLY;
            case WEEKLY -> QuestScope.MILESTONE;
            case MILESTONE, MANUAL -> null;
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

    private static String offerLabel(QuestOffer.State state) {
        return switch (state) {
            case AVAILABLE -> "Available";
            case ACTIVE -> "Active";
            case READY_TO_CLAIM -> "Ready to Claim";
            case COMPLETED -> "Completed";
            case LOCKED -> "Locked";
            case PERIOD_LIMIT_REACHED -> "Period Limit Reached";
            case ACTIVE_SLOT_OCCUPIED -> "Active Slot Occupied";
            case EXPIRED -> "Expired";
        };
    }

    private static String offerColor(QuestOffer.State state) {
        return switch (state) {
            case AVAILABLE -> "<green>";
            case ACTIVE -> "<gold>";
            case READY_TO_CLAIM -> "<green>";
            case COMPLETED -> "<light_purple>";
            case LOCKED, PERIOD_LIMIT_REACHED, ACTIVE_SLOT_OCCUPIED, EXPIRED -> "<gray>";
        };
    }

    private static String assignmentLabel(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "Active";
            case COMPLETED -> "Ready to Claim";
            case CLAIMING -> "Claiming";
            case CLAIMED -> "Claimed";
            case CANCELLED -> "Cancelled";
            case EXPIRED -> "Expired";
        };
    }

    private static String assignmentColor(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "<gold>";
            case COMPLETED, CLAIMED -> "<green>";
            case CLAIMING -> "<yellow>";
            case CANCELLED, EXPIRED -> "<gray>";
        };
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

    @FunctionalInterface
    private interface Action { void run(Player player, ClickType click); }

    private static final class Holder implements InventoryHolder {
        private final JournalNavigationContext context;
        private final Map<Integer, Action> actions = new HashMap<>();
        private final Set<String> submissions = new HashSet<>();
        private Inventory inventory;
        private long lastInteractionNanos;

        private Holder(JournalNavigationContext context) { this.context = context; }
        private boolean acceptInteraction() {
            long now = System.nanoTime();
            if (now - lastInteractionNanos < CLICK_DEBOUNCE_NANOS) return false;
            lastInteractionNanos = now;
            return true;
        }
        private boolean submit(String key) { return submissions.add(key); }
        @Override public Inventory getInventory() { return inventory; }
    }
}

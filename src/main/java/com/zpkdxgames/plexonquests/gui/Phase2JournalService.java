package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestTrackEvent;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.reward.RewardDefinition;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.JournalStateResolver;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
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

/**
 * Phase 2 premium journal surface. It is intentionally additive to the mature 3.x MenuService;
 * assigned quest details, claiming, rerolls and history continue to use the existing transaction-aware UI.
 */
public final class Phase2JournalService implements Listener {
    private static final List<Integer> CONTENT_SLOTS = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final ConfigManager configs;
    private final ProfileService profiles;
    private final MenuService legacyMenus;
    private final QuestEligibilityService eligibility;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final TextService text;
    private final JournalStateResolver states;
    private final ItemFactory items = new ItemFactory();

    public Phase2JournalService(
            ConfigManager configs,
            ProfileService profiles,
            MenuService legacyMenus,
            QuestEligibilityService eligibility,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            TextService text) {
        this.configs = configs;
        this.profiles = profiles;
        this.legacyMenus = legacyMenus;
        this.eligibility = eligibility;
        this.prerequisites = prerequisites;
        this.history = history;
        this.text = text;
        this.states = new JournalStateResolver(eligibility, prerequisites, history);
    }

    public JournalStateResolver stateResolver() {
        return states;
    }

    public void openOverview(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        Phase2Holder holder = create(player, View.OVERVIEW, "<gradient:#F6C85F:#E9A83A><bold>Quest Journal</bold></gradient>");
        bindNavigation(holder, player, View.OVERVIEW);

        Counts counts = counts(player, profile);
        set(holder, 20, Material.WRITABLE_BOOK, "<gold><bold>Active Quests</bold>", List.of(
                "<gray>In progress <white>" + counts.active(),
                "<gray>Ready to claim <green>" + counts.completable(),
                "",
                "<dark_gray>Click to view active quests"), false, (viewer, click) -> openActive(viewer));
        set(holder, 22, Material.ENDER_EYE, "<aqua><bold>Quest Discovery</bold>", List.of(
                "<gray>Available <white>" + counts.available(),
                "<gray>Locked <red>" + counts.locked(),
                "<gray>Definitions <white>" + configs.snapshot().registry().quests().size(),
                "",
                "<dark_gray>Click to browse the catalog"), false, (viewer, click) -> openAvailable(viewer, 0, null));
        set(holder, 24, Material.LODESTONE, "<yellow><bold>Tracked Quest</bold>", trackedLore(profile),
                profile.pinnedAssignment().isPresent(), (viewer, click) -> openTracked(viewer));
        set(holder, 30, Material.KNOWLEDGE_BOOK, "<green><bold>Completion</bold>", List.of(
                "<gray>Lifetime claimed <white>" + profile.completedTotal(),
                "<gray>History cache " + (history.loaded(player.getUniqueId()) ? "<green>Ready" : "<yellow>Loading"),
                history.truncated(player.getUniqueId()) ? "<yellow>Older prerequisite history is conservatively bounded" : "",
                "",
                "<dark_gray>Click to browse completion history"), false, (viewer, click) -> openCompleted(viewer));
        set(holder, 32, Material.BOOKSHELF, "<light_purple><bold>Categories</bold>", List.of(
                "<gray>Configured <white>" + categoryCounts().size(),
                "<gray>Browse quests by author-defined category.",
                "",
                "<dark_gray>Click to browse categories"), false, (viewer, click) -> openCategories(viewer));
        set(holder, 40, Material.EXPERIENCE_BOTTLE, "<aqua><bold>Statistics</bold>", List.of(
                "<gray>Active <white>" + counts.active(),
                "<gray>Claimable <white>" + profile.claimableCount(),
                "<gray>Completed <white>" + profile.completedTotal(),
                "",
                "<dark_gray>Click for progression statistics"), false, (viewer, click) -> openStatistics(viewer));
        set(holder, 42, Material.PAPER, "<white><bold>Help</bold>", List.of(
                "<gray>Discovery -> Tracking -> Progress",
                "<gray>Reward Preview -> Completion -> History",
                "",
                "<dark_gray>Click for controls and state meanings"), false, (viewer, click) -> openHelp(viewer));
        player.openInventory(holder.inventory);
    }

    public void openActive(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        Phase2Holder holder = create(player, View.ACTIVE, "<gold><bold>Active Quests</bold>");
        bindNavigation(holder, player, View.ACTIVE);
        List<QuestAssignment> assignments = profile.visibleAssignments().stream()
                .filter(value -> value.state() == AssignmentState.ACTIVE
                        || value.state() == AssignmentState.COMPLETED
                        || value.state() == AssignmentState.CLAIMING)
                .sorted(Comparator.comparing((QuestAssignment value) -> value.state() == AssignmentState.COMPLETED ? 0 : 1)
                        .thenComparing(QuestAssignment::assignedAt))
                .toList();
        for (int index = 0; index < CONTENT_SLOTS.size() && index < assignments.size(); index++) {
            QuestAssignment assignment = assignments.get(index);
            boolean tracked = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
            ItemStack card = assignmentCard(assignment, tracked);
            int slot = CONTENT_SLOTS.get(index);
            holder.inventory.setItem(slot, card);
            holder.actions.put(slot, (viewer, click) -> {
                if (click.isRightClick() && assignment.state() == AssignmentState.ACTIVE) {
                    toggleTracked(viewer, assignment);
                } else {
                    legacyMenus.openDetails(viewer, assignment, MenuContext.journal(assignment.definition().scope()));
                }
            });
        }
        if (assignments.isEmpty()) {
            set(holder, 22, Material.GRAY_DYE, "<gray>No active quests", List.of(
                    "<dark_gray>Browse Available Quests to see what can be assigned next."), false,
                    (viewer, click) -> openAvailable(viewer, 0, null));
        }
        player.openInventory(holder.inventory);
    }

    public void openAvailable(Player player, int requestedPage, String category) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        String suffix = category == null ? "" : " <dark_gray>/</dark_gray> <white>" + escape(category);
        Phase2Holder holder = create(player, View.AVAILABLE, "<aqua><bold>Quest Discovery</bold></aqua>" + suffix);
        bindNavigation(holder, player, View.AVAILABLE);
        List<QuestDefinition> definitions = configs.snapshot().registry().quests().values().stream()
                .filter(definition -> category == null || definition.category().equalsIgnoreCase(category))
                .filter(definition -> {
                    JournalState state = states.resolve(player, profile, definition);
                    return state == JournalState.AVAILABLE || state == JournalState.LOCKED
                            || state == JournalState.COOLDOWN || state == JournalState.DISABLED;
                })
                .sorted(Comparator.comparingInt((QuestDefinition definition) -> stateOrder(states.resolve(player, profile, definition)))
                        .thenComparing(QuestDefinition::category)
                        .thenComparing(QuestDefinition::id))
                .toList();
        int pages = Math.max(1, (definitions.size() + CONTENT_SLOTS.size() - 1) / CONTENT_SLOTS.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int offset = page * CONTENT_SLOTS.size();
        for (int index = 0; index < CONTENT_SLOTS.size() && offset + index < definitions.size(); index++) {
            QuestDefinition definition = definitions.get(offset + index);
            int slot = CONTENT_SLOTS.get(index);
            holder.inventory.setItem(slot, definitionCard(player, profile, definition));
            holder.actions.put(slot, (viewer, click) -> openDefinitionDetails(viewer, definition, page, category));
        }
        if (definitions.isEmpty()) {
            set(holder, 22, Material.LIME_DYE, "<green>Nothing waiting here", List.of(
                    "<gray>All visible quests are already active or completed."), false, null);
        }
        pageControls(holder, player, page, pages, category);
        player.openInventory(holder.inventory);
    }

    public void openCategories(Player player) {
        if (profile(player) == null) {
            return;
        }
        Phase2Holder holder = create(player, View.CATEGORIES, "<light_purple><bold>Quest Categories</bold>");
        bindNavigation(holder, player, View.CATEGORIES);
        List<Map.Entry<String, Long>> categories = categoryCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        for (int index = 0; index < CONTENT_SLOTS.size() && index < categories.size(); index++) {
            Map.Entry<String, Long> entry = categories.get(index);
            int slot = CONTENT_SLOTS.get(index);
            set(holder, slot, Material.BOOKSHELF,
                    "<gradient:#C8A2FF:#8FD3FF><bold>" + escape(pretty(entry.getKey())) + "</bold></gradient>",
                    List.of("<gray>Quest definitions <white>" + entry.getValue(), "", "<dark_gray>Click to browse"),
                    false, (viewer, click) -> openAvailable(viewer, 0, entry.getKey()));
        }
        player.openInventory(holder.inventory);
    }

    public void openTracked(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (tracked == null) {
            player.sendMessage(text.parse("<gray>No tracked quest. <white>Right-click an active quest to track it."));
            openActive(player);
            return;
        }
        legacyMenus.openDetails(player, tracked, MenuContext.journal(tracked.definition().scope()));
    }

    public void openCompleted(Player player) {
        legacyMenus.openHistory(player, 0, MenuContext.journal(null));
    }

    public void openStatistics(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        Counts counts = counts(player, profile);
        Phase2Holder holder = create(player, View.STATISTICS, "<aqua><bold>Quest Statistics</bold>");
        bindNavigation(holder, player, View.STATISTICS);
        set(holder, 20, Material.CLOCK, "<white><bold>Current Cycle</bold>", List.of(
                "<gray>Active <white>" + counts.active(),
                "<gray>Ready to claim <green>" + counts.completable(),
                "<gray>Available <aqua>" + counts.available(),
                "<gray>Locked <red>" + counts.locked()), false, null);
        set(holder, 22, Material.EXPERIENCE_BOTTLE, "<gold><bold>Lifetime</bold>", List.of(
                "<gray>Claimed quests <white>" + profile.completedTotal(),
                "<gray>Cached unique completions <white>" + history.completedQuestIds(player.getUniqueId()).size()), false, null);
        set(holder, 24, Material.COMPARATOR, "<light_purple><bold>Definition Health</bold>", List.of(
                "<gray>Quests <white>" + configs.snapshot().registry().quests().size(),
                "<gray>Pools <white>" + configs.snapshot().registry().pools().size(),
                "<gray>Rarities <white>" + configs.snapshot().registry().rarities().size(),
                "<gray>Prerequisite nodes <white>" + prerequisites.snapshot().questCount()), false, null);
        player.openInventory(holder.inventory);
    }

    public void openHelp(Player player) {
        if (profile(player) == null) {
            return;
        }
        Phase2Holder holder = create(player, View.HELP, "<white><bold>Quest Journal Help</bold>");
        bindNavigation(holder, player, View.HELP);
        set(holder, 19, Material.ENDER_EYE, "<aqua><bold>Discover</bold>", List.of(
                "<gray>Available shows definitions you can receive now.",
                "<gray>Locked quests show prerequisite or eligibility gates."), false, null);
        set(holder, 21, Material.LODESTONE, "<yellow><bold>Track</bold>", List.of(
                "<gray>Right-click an active quest to track it.",
                "<gray>Only one quest is tracked at a time for 3.x pin compatibility."), false, null);
        set(holder, 23, Material.WRITABLE_BOOK, "<gold><bold>Progress</bold>", List.of(
                "<gray>Open an assigned quest for objective progress and reward preview.",
                "<gray>Progress remains event-indexed; the journal does not poll the database."), false, null);
        set(holder, 25, Material.CHEST, "<green><bold>Complete</bold>", List.of(
                "<gray>COMPLETABLE means objectives are complete and rewards are ready.",
                "<gray>Claim delivery uses the durable transaction/preflight path."), false, null);
        set(holder, 31, Material.MAP, "<white><bold>State Guide</bold>", List.of(
                "<red>LOCKED <dark_gray>- prerequisite/eligibility gate",
                "<aqua>AVAILABLE <dark_gray>- eligible for assignment",
                "<gold>ACTIVE <dark_gray>- progressing",
                "<yellow>TRACKED <dark_gray>- active and pinned",
                "<green>COMPLETABLE <dark_gray>- reward ready",
                "<gray>COOLDOWN <dark_gray>- rotating quest completed this period",
                "<green>COMPLETED <dark_gray>- non-rotating completion"), false, null);
        player.openInventory(holder.inventory);
    }

    private void openDefinitionDetails(Player player, QuestDefinition definition, int returnPage, String category) {
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return;
        }
        QuestAssignment assignment = states.latest(profile, definition.id()).orElse(null);
        if (assignment != null && !assignment.state().terminal()) {
            legacyMenus.openDetails(player, assignment, MenuContext.journal(assignment.definition().scope()));
            return;
        }
        Phase2Holder holder = create(player, View.DEFINITION, "<aqua><bold>Quest Details</bold>");
        JournalState state = states.resolve(player, profile, definition);
        List<String> header = new ArrayList<>();
        header.add("<dark_gray>" + definition.scope() + " • " + escape(pretty(definition.category())));
        if (!definition.display().shortDescription().isBlank()) {
            header.add("");
            header.add("<gray>" + escape(definition.display().shortDescription()));
        }
        header.add("");
        header.add("<gray>State " + stateColor(state) + state);
        header.add("<gray>Rarity <white>" + escape(definition.rarity()));
        holder.inventory.setItem(4, items.create(
                definition.display().icon().material(), text.parse(definition.display().name()), components(header), false));

        Set<String> required = prerequisites.prerequisites(definition.id());
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<String> prerequisiteLore = new ArrayList<>();
        if (required.isEmpty()) {
            prerequisiteLore.add("<green>No quest prerequisites");
        } else {
            for (String id : required) {
                prerequisiteLore.add((missing.contains(id) ? "<red>✗ " : "<green>✓ ") + "<white>" + escape(id));
            }
        }
        set(holder, 20, Material.CHAIN, "<gold><bold>Prerequisites</bold>", prerequisiteLore, false, null);

        List<String> objectiveLore = new ArrayList<>();
        definition.objectives().values().stream().limit(8).forEach(objective -> objectiveLore.add(
                "<gray>• <white>" + escape(objective.display()) + " <dark_gray>×</dark_gray><white>" + objective.amount()));
        if (definition.objectives().size() > 8) {
            objectiveLore.add("<dark_gray>+" + (definition.objectives().size() - 8) + " more");
        }
        set(holder, 22, Material.TARGET, "<aqua><bold>Objectives</bold>", objectiveLore, false, null);

        List<String> rewardLore = new ArrayList<>();
        definition.rewards().entries().stream().limit(8).forEach(reward -> rewardLore.add(rewardLine(reward)));
        if (definition.rewards().entries().size() > 8) {
            rewardLore.add("<dark_gray>+" + (definition.rewards().entries().size() - 8) + " more");
        }
        set(holder, 24, Material.CHEST, "<green><bold>Reward Preview</bold>", rewardLore, false, null);

        String policy = switch (definition.scope()) {
            case DAILY -> "Assigned by the daily rotation coordinator";
            case WEEKLY -> "Assigned by the weekly rotation coordinator";
            case MILESTONE -> "Assigned automatically when eligibility is satisfied";
            case MANUAL -> "Assigned explicitly by an administrator";
        };
        var evaluation = eligibility.evaluate(player, profile, definition);
        set(holder, 31, stateMaterial(state), stateColor(state) + "<bold>" + state + "</bold>", List.of(
                "<gray>" + escape(policy),
                evaluation.eligible() ? "<green>Eligibility satisfied" : "<red>" + escape(evaluation.reason()),
                "",
                "<dark_gray>Discovery is read-only; assignment authority is not duplicated."), state == JournalState.AVAILABLE, null);
        set(holder, 45, Material.ARROW, "<white>Back", List.of(), false,
                (viewer, click) -> openAvailable(viewer, returnPage, category));
        set(holder, 53, Material.BARRIER, "<red>Close", List.of(), false, (viewer, click) -> viewer.closeInventory());
        player.openInventory(holder.inventory);
    }

    private void toggleTracked(Player player, QuestAssignment assignment) {
        PlayerProfile profile = profile(player);
        if (profile == null || assignment.state() != AssignmentState.ACTIVE) {
            return;
        }
        boolean wasTracked = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
        profile.pinnedAssignment(wasTracked ? null : assignment.id());
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), !wasTracked));
        player.sendMessage(text.parse(wasTracked
                ? "<gray>Stopped tracking <white>" + escape(text.plain(text.parse(assignment.definition().display().name())))
                : "<yellow>Tracking <white>" + escape(text.plain(text.parse(assignment.definition().display().name())))));
        openActive(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Phase2Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        Action action = holder.actions.get(event.getRawSlot());
        if (action != null) {
            action.run(player, event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Phase2Holder) {
            event.setCancelled(true);
        }
    }

    private Phase2Holder create(Player player, View view, String title) {
        Phase2Holder holder = new Phase2Holder(view);
        holder.inventory = Bukkit.createInventory(holder, 54, text.parse(title));
        ItemStack filler = items.create(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int slot = 0; slot < holder.inventory.getSize(); slot++) {
            holder.inventory.setItem(slot, filler);
        }
        return holder;
    }

    private void bindNavigation(Phase2Holder holder, Player player, View selected) {
        nav(holder, 0, Material.COMPASS, "Overview", View.OVERVIEW, selected, this::openOverview);
        nav(holder, 1, Material.WRITABLE_BOOK, "Active", View.ACTIVE, selected, this::openActive);
        nav(holder, 2, Material.ENDER_EYE, "Available", View.AVAILABLE, selected,
                viewer -> openAvailable(viewer, 0, null));
        nav(holder, 3, Material.BOOKSHELF, "Categories", View.CATEGORIES, selected, this::openCategories);
        nav(holder, 4, Material.LODESTONE, "Tracked", View.TRACKED, selected, this::openTracked);
        nav(holder, 5, Material.KNOWLEDGE_BOOK, "Completed", View.COMPLETED, selected, this::openCompleted);
        nav(holder, 6, Material.EXPERIENCE_BOTTLE, "Statistics", View.STATISTICS, selected, this::openStatistics);
        nav(holder, 7, Material.PAPER, "Help", View.HELP, selected, this::openHelp);
        set(holder, 8, Material.BARRIER, "<red>Close", List.of("<dark_gray>Close the quest journal"), false,
                (viewer, click) -> viewer.closeInventory());
    }

    private void nav(
            Phase2Holder holder,
            int slot,
            Material material,
            String name,
            View view,
            View selected,
            java.util.function.Consumer<Player> action) {
        boolean active = view == selected;
        set(holder, slot, material,
                active ? "<gradient:#F6C85F:#E9A83A><bold>" + name + "</bold></gradient>" : "<white>" + name,
                List.of(active ? "<gold>Selected" : "<dark_gray>Click to open"), active,
                (viewer, click) -> action.accept(viewer));
    }

    private void pageControls(Phase2Holder holder, Player player, int page, int pages, String category) {
        if (page > 0) {
            set(holder, 45, Material.ARROW, "<white>Previous", List.of("<gray>Page " + page + "/" + pages), false,
                    (viewer, click) -> openAvailable(viewer, page - 1, category));
        }
        set(holder, 49, Material.MAP, "<gold>Page " + (page + 1) + "<dark_gray>/</dark_gray><white>" + pages,
                List.of(category == null ? "<gray>All categories" : "<gray>Category <white>" + escape(category)), false, null);
        if (page + 1 < pages) {
            set(holder, 53, Material.ARROW, "<white>Next", List.of("<gray>Page " + (page + 2) + "/" + pages), false,
                    (viewer, click) -> openAvailable(viewer, page + 1, category));
        }
    }

    private ItemStack assignmentCard(QuestAssignment assignment, boolean tracked) {
        var summary = assignment.displayProgress();
        double percentage = summary.percentage();
        String state = assignment.state() == AssignmentState.COMPLETED ? "COMPLETABLE" : tracked ? "TRACKED" : "ACTIVE";
        List<Component> lore = List.of(
                text.parse("<dark_gray>" + assignment.definition().scope() + " • " + escape(pretty(assignment.definition().category()))),
                Component.empty(),
                text.parse("<gray>State " + (assignment.state() == AssignmentState.COMPLETED ? "<green>" : tracked ? "<yellow>" : "<gold>") + state),
                text.parse("<gray>Progress <white>" + text.formatNumber(summary.current()) + "<dark_gray>/</dark_gray><white>" + text.formatNumber(summary.required())),
                text.progressBar(percentage),
                Component.empty(),
                text.parse("<dark_gray>Left-click details • Right-click track"));
        return items.create(assignment.definition().display().icon().material(),
                text.parse(assignment.definition().display().name()), lore, tracked || assignment.state() == AssignmentState.COMPLETED);
    }

    private ItemStack definitionCard(Player player, PlayerProfile profile, QuestDefinition definition) {
        JournalState state = states.resolve(player, profile, definition);
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<Component> lore = new ArrayList<>();
        lore.add(text.parse("<dark_gray>" + definition.scope() + " • " + escape(pretty(definition.category()))));
        lore.add(Component.empty());
        lore.add(text.parse("<gray>State " + stateColor(state) + state));
        lore.add(text.parse("<gray>Objectives <white>" + definition.objectives().size()));
        lore.add(text.parse("<gray>Rewards <white>" + definition.rewards().entries().size()));
        if (!missing.isEmpty()) {
            lore.add(text.parse("<gray>Missing <red>" + escape(String.join(", ", missing))));
        }
        lore.add(Component.empty());
        lore.add(text.parse("<dark_gray>Click for objective, reward and prerequisite preview"));
        return items.create(definition.display().icon().material(), text.parse(definition.display().name()), lore,
                state == JournalState.AVAILABLE);
    }

    private Counts counts(Player player, PlayerProfile profile) {
        int active = 0;
        int available = 0;
        int locked = 0;
        int completable = 0;
        for (QuestDefinition definition : configs.snapshot().registry().quests().values()) {
            switch (states.resolve(player, profile, definition)) {
                case ACTIVE, TRACKED -> active++;
                case AVAILABLE -> available++;
                case LOCKED -> locked++;
                case COMPLETABLE -> completable++;
                default -> { }
            }
        }
        return new Counts(active, available, locked, completable);
    }

    private Map<String, Long> categoryCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        configs.snapshot().registry().quests().values().forEach(definition ->
                counts.merge(definition.category(), 1L, Long::sum));
        return Map.copyOf(counts);
    }

    private List<String> trackedLore(PlayerProfile profile) {
        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (tracked == null) {
            return List.of("<gray>No quest tracked", "", "<dark_gray>Right-click an active quest to track it");
        }
        var progress = tracked.displayProgress();
        return List.of(
                "<gray>Quest <white>" + escape(text.plain(text.parse(tracked.definition().display().name()))),
                "<gray>Progress <white>" + progress.current() + "<dark_gray>/</dark_gray><white>" + progress.required(),
                "",
                "<dark_gray>Click to open tracked quest");
    }

    private void set(
            Phase2Holder holder,
            int slot,
            Material material,
            String name,
            List<String> lore,
            boolean glow,
            Action action) {
        List<Component> components = components(lore.stream().filter(value -> !value.isEmpty()).toList());
        holder.inventory.setItem(slot, items.create(material, text.parse(name), components, glow));
        if (action != null) {
            holder.actions.put(slot, action);
        }
    }

    private List<Component> components(List<String> lines) {
        return lines.stream().map(line -> line.isEmpty() ? Component.empty() : text.parse(line)).toList();
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            player.sendMessage(text.parse("<yellow>Your quest profile is still loading."));
        }
        return profile;
    }

    private static String rewardLine(RewardDefinition reward) {
        return "<gray>• <white>" + escape(reward.display()) + " <dark_gray>(" + reward.type() + ")";
    }

    private static int stateOrder(JournalState state) {
        return switch (state) {
            case AVAILABLE -> 0;
            case LOCKED -> 1;
            case COOLDOWN -> 2;
            case DISABLED -> 3;
            default -> 4;
        };
    }

    private static Material stateMaterial(JournalState state) {
        return switch (state) {
            case AVAILABLE -> Material.LIME_DYE;
            case LOCKED, DISABLED -> Material.RED_DYE;
            case COOLDOWN -> Material.CLOCK;
            case COMPLETABLE, COMPLETED -> Material.EMERALD;
            case TRACKED -> Material.LODESTONE;
            default -> Material.GOLD_INGOT;
        };
    }

    private static String stateColor(JournalState state) {
        return switch (state) {
            case AVAILABLE -> "<aqua>";
            case ACTIVE -> "<gold>";
            case TRACKED -> "<yellow>";
            case COMPLETABLE, COMPLETED -> "<green>";
            case LOCKED, DISABLED -> "<red>";
            case COOLDOWN, EXPIRED -> "<gray>";
        };
    }

    private static String pretty(String value) {
        String[] parts = value.replace('-', ' ').replace('_', ' ').split(" +");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>");
    }

    private enum View { OVERVIEW, ACTIVE, AVAILABLE, CATEGORIES, TRACKED, COMPLETED, STATISTICS, HELP, DEFINITION }

    private record Counts(int active, int available, int locked, int completable) {}

    @FunctionalInterface
    private interface Action {
        void run(Player player, ClickType click);
    }

    private static final class Phase2Holder implements InventoryHolder {
        private final View view;
        private final Map<Integer, Action> actions = new HashMap<>();
        private Inventory inventory;

        private Phase2Holder(View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestTrackEvent;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
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

/**
 * Additive premium journal. Claiming, rerolls, assigned-quest detail and durable history remain delegated to
 * the mature MenuService so Phase 2 cannot create a second reward or assignment authority.
 */
public final class Phase2JournalService implements Listener {
    private static final List<Integer> CONTENT = List.of(
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);

    private final ConfigManager configs;
    private final ProfileService profiles;
    private final MenuService legacy;
    private final QuestEligibilityService eligibility;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final TextService text;
    private final JournalStateResolver states;
    private final ItemFactory items = new ItemFactory();

    public Phase2JournalService(
            ConfigManager configs,
            ProfileService profiles,
            MenuService legacy,
            QuestEligibilityService eligibility,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            TextService text) {
        this.configs = configs;
        this.profiles = profiles;
        this.legacy = legacy;
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
        if (profile == null) return;
        Holder holder = create(View.OVERVIEW, "<gradient:#F6C85F:#E9A83A><bold>Quest Journal</bold></gradient>");
        navigation(holder, View.OVERVIEW);
        Counts count = count(player, profile);
        item(holder, 20, Material.WRITABLE_BOOK, "<gold><bold>Active Quests</bold>", List.of(
                "<gray>In progress <white>" + count.active,
                "<gray>Ready to claim <green>" + count.completable,
                "", "<dark_gray>Click to view"), false, (p, c) -> openActive(p));
        item(holder, 22, Material.ENDER_EYE, "<aqua><bold>Quest Discovery</bold>", List.of(
                "<gray>Available <white>" + count.available,
                "<gray>Locked <red>" + count.locked,
                "<gray>Definitions <white>" + configs.snapshot().registry().quests().size(),
                "", "<dark_gray>Click to browse"), false, (p, c) -> openAvailable(p, 0, null));
        item(holder, 24, Material.LODESTONE, "<yellow><bold>Tracked Quest</bold>", trackedLore(profile),
                profile.pinnedAssignment().isPresent(), (p, c) -> openTracked(p));
        item(holder, 30, Material.KNOWLEDGE_BOOK, "<green><bold>Completion</bold>", List.of(
                "<gray>Lifetime claimed <white>" + profile.completedTotal(),
                "<gray>History cache " + (history.loaded(player.getUniqueId()) ? "<green>Ready" : "<yellow>Loading"),
                history.truncated(player.getUniqueId()) ? "<yellow>Older history reached the bounded cache limit" : "",
                "", "<dark_gray>Click for history"), false, (p, c) -> openCompleted(p));
        item(holder, 32, Material.BOOKSHELF, "<light_purple><bold>Categories</bold>", List.of(
                "<gray>Configured <white>" + categoryCounts().size(), "", "<dark_gray>Click to browse"),
                false, (p, c) -> openCategories(p));
        item(holder, 40, Material.EXPERIENCE_BOTTLE, "<aqua><bold>Statistics</bold>", List.of(
                "<gray>Active <white>" + count.active,
                "<gray>Claimable <white>" + profile.claimableCount(),
                "<gray>Completed <white>" + profile.completedTotal(),
                "", "<dark_gray>Click for statistics"), false, (p, c) -> openStatistics(p));
        item(holder, 42, Material.PAPER, "<white><bold>Help</bold>", List.of(
                "<gray>Discover -> Track -> Progress",
                "<gray>Reward Preview -> Complete -> History",
                "", "<dark_gray>Click for controls"), false, (p, c) -> openHelp(p));
        player.openInventory(holder.inventory);
    }

    public void openActive(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        Holder holder = create(View.ACTIVE, "<gold><bold>Active Quests</bold>");
        navigation(holder, View.ACTIVE);
        List<QuestAssignment> active = profile.visibleAssignments().stream()
                .filter(a -> a.state() == AssignmentState.ACTIVE
                        || a.state() == AssignmentState.COMPLETED
                        || a.state() == AssignmentState.CLAIMING)
                .sorted(Comparator.comparingInt((QuestAssignment a) -> a.state() == AssignmentState.COMPLETED ? 0 : 1)
                        .thenComparing(QuestAssignment::assignedAt))
                .toList();
        for (int i = 0; i < CONTENT.size() && i < active.size(); i++) {
            QuestAssignment assignment = active.get(i);
            boolean tracked = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
            int slot = CONTENT.get(i);
            holder.inventory.setItem(slot, assignmentCard(assignment, tracked));
            holder.actions.put(slot, (p, click) -> {
                if (click.isRightClick() && assignment.state() == AssignmentState.ACTIVE) {
                    toggleTracked(p, assignment);
                } else {
                    legacy.openDetails(p, assignment, MenuContext.journal(assignment.definition().scope()));
                }
            });
        }
        if (active.isEmpty()) {
            item(holder, 22, Material.GRAY_DYE, "<gray>No active quests", List.of(
                    "<dark_gray>Browse Available Quests to see what can be assigned next."), false,
                    (p, c) -> openAvailable(p, 0, null));
        }
        player.openInventory(holder.inventory);
    }

    public void openAvailable(Player player, int requestedPage, String category) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        Holder holder = create(View.AVAILABLE, category == null
                ? "<aqua><bold>Quest Discovery</bold>"
                : "<aqua><bold>Quest Discovery</bold> <dark_gray>/</dark_gray> <white>" + safe(category));
        navigation(holder, View.AVAILABLE);
        List<QuestDefinition> definitions = configs.snapshot().registry().quests().values().stream()
                .filter(q -> category == null || q.category().equalsIgnoreCase(category))
                .filter(q -> {
                    JournalState state = states.resolve(player, profile, q);
                    return state == JournalState.AVAILABLE || state == JournalState.LOCKED
                            || state == JournalState.COOLDOWN || state == JournalState.DISABLED;
                })
                .sorted(Comparator.comparingInt((QuestDefinition q) -> order(states.resolve(player, profile, q)))
                        .thenComparing(QuestDefinition::category).thenComparing(QuestDefinition::id))
                .toList();
        int pages = Math.max(1, (definitions.size() + CONTENT.size() - 1) / CONTENT.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int offset = page * CONTENT.size();
        for (int i = 0; i < CONTENT.size() && offset + i < definitions.size(); i++) {
            QuestDefinition definition = definitions.get(offset + i);
            int slot = CONTENT.get(i);
            holder.inventory.setItem(slot, definitionCard(player, profile, definition));
            holder.actions.put(slot, (p, click) -> openDefinition(p, definition, page, category));
        }
        if (definitions.isEmpty()) {
            item(holder, 22, Material.LIME_DYE, "<green>Nothing waiting here", List.of(
                    "<gray>All visible quests are already active or completed."), false, null);
        }
        pages(holder, page, pages, category);
        player.openInventory(holder.inventory);
    }

    public void openCategories(Player player) {
        if (profile(player) == null) return;
        Holder holder = create(View.CATEGORIES, "<light_purple><bold>Quest Categories</bold>");
        navigation(holder, View.CATEGORIES);
        List<Map.Entry<String, Long>> categories = categoryCounts().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        for (int i = 0; i < CONTENT.size() && i < categories.size(); i++) {
            Map.Entry<String, Long> category = categories.get(i);
            item(holder, CONTENT.get(i), Material.BOOKSHELF,
                    "<gradient:#C8A2FF:#8FD3FF><bold>" + safe(pretty(category.getKey())) + "</bold></gradient>",
                    List.of("<gray>Quest definitions <white>" + category.getValue(), "", "<dark_gray>Click to browse"),
                    false, (p, c) -> openAvailable(p, 0, category.getKey()));
        }
        player.openInventory(holder.inventory);
    }

    public void openTracked(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (tracked == null) {
            player.sendMessage(text.parse("<gray>No tracked quest. <white>Right-click an active quest to track it."));
            openActive(player);
            return;
        }
        legacy.openDetails(player, tracked, MenuContext.journal(tracked.definition().scope()));
    }

    public void openCompleted(Player player) {
        legacy.openHistory(player, 0, MenuContext.journal(null));
    }

    public void openStatistics(Player player) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        Holder holder = create(View.STATISTICS, "<aqua><bold>Quest Statistics</bold>");
        navigation(holder, View.STATISTICS);
        Counts count = count(player, profile);
        item(holder, 20, Material.CLOCK, "<white><bold>Current Cycle</bold>", List.of(
                "<gray>Active <white>" + count.active,
                "<gray>Ready <green>" + count.completable,
                "<gray>Available <aqua>" + count.available,
                "<gray>Locked <red>" + count.locked), false, null);
        item(holder, 22, Material.EXPERIENCE_BOTTLE, "<gold><bold>Lifetime</bold>", List.of(
                "<gray>Claimed quests <white>" + profile.completedTotal(),
                "<gray>Cached unique completions <white>" + history.completedQuestIds(player.getUniqueId()).size()), false, null);
        item(holder, 24, Material.COMPARATOR, "<light_purple><bold>Definition Health</bold>", List.of(
                "<gray>Quests <white>" + configs.snapshot().registry().quests().size(),
                "<gray>Pools <white>" + configs.snapshot().registry().pools().size(),
                "<gray>Rarities <white>" + configs.snapshot().registry().rarities().size(),
                "<gray>Prerequisite graph <white>" + prerequisites.snapshot().questCount()), false, null);
        player.openInventory(holder.inventory);
    }

    public void openHelp(Player player) {
        if (profile(player) == null) return;
        Holder holder = create(View.HELP, "<white><bold>Quest Journal Help</bold>");
        navigation(holder, View.HELP);
        item(holder, 19, Material.ENDER_EYE, "<aqua><bold>Discover</bold>", List.of(
                "<gray>Available shows quests eligible for assignment.",
                "<gray>Locked shows prerequisite or eligibility gates."), false, null);
        item(holder, 21, Material.LODESTONE, "<yellow><bold>Track</bold>", List.of(
                "<gray>Right-click an active quest to track it.",
                "<gray>Tracked is the 4.x name for the compatible 3.x pin."), false, null);
        item(holder, 23, Material.WRITABLE_BOOK, "<gold><bold>Progress</bold>", List.of(
                "<gray>Open an assigned quest for objective progress and rewards.",
                "<gray>The journal never polls SQLite per objective or render."), false, null);
        item(holder, 25, Material.CHEST, "<green><bold>Complete</bold>", List.of(
                "<gray>COMPLETABLE means objectives are done and rewards are ready.",
                "<gray>Claims keep the existing durable preflight/transaction path."), false, null);
        item(holder, 31, Material.MAP, "<white><bold>State Guide</bold>", List.of(
                "<red>LOCKED <dark_gray>- prerequisite/eligibility gate",
                "<aqua>AVAILABLE <dark_gray>- eligible for assignment",
                "<gold>ACTIVE <dark_gray>- progressing",
                "<yellow>TRACKED <dark_gray>- active and pinned",
                "<green>COMPLETABLE <dark_gray>- reward ready",
                "<gray>COOLDOWN <dark_gray>- rotating completion this period",
                "<green>COMPLETED <dark_gray>- non-rotating completion"), false, null);
        player.openInventory(holder.inventory);
    }

    private void openDefinition(Player player, QuestDefinition definition, int page, String category) {
        PlayerProfile profile = profile(player);
        if (profile == null) return;
        QuestAssignment assigned = states.latest(profile, definition.id()).orElse(null);
        if (assigned != null && !assigned.state().terminal()) {
            legacy.openDetails(player, assigned, MenuContext.journal(assigned.definition().scope()));
            return;
        }
        Holder holder = create(View.DEFINITION, "<aqua><bold>Quest Details</bold>");
        JournalState state = states.resolve(player, profile, definition);
        List<String> header = new ArrayList<>();
        header.add("<dark_gray>" + definition.scope() + " • " + safe(pretty(definition.category())));
        if (!definition.display().shortDescription().isBlank()) {
            header.add("");
            header.add("<gray>" + safe(definition.display().shortDescription()));
        }
        header.add("");
        header.add("<gray>State " + stateColor(state) + state);
        header.add("<gray>Rarity <white>" + safe(definition.rarity()));
        holder.inventory.setItem(4, items.create(definition.display().icon().material(),
                text.parse(definition.display().name()), components(header), false));

        Set<String> required = prerequisites.prerequisites(definition.id());
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<String> prerequisiteLore = new ArrayList<>();
        if (required.isEmpty()) {
            prerequisiteLore.add("<green>No quest prerequisites");
        } else {
            for (String id : required) {
                prerequisiteLore.add((missing.contains(id) ? "<red>✗ " : "<green>✓ ") + "<white>" + safe(id));
            }
        }
        item(holder, 20, Material.TRIPWIRE_HOOK, "<gold><bold>Prerequisites</bold>", prerequisiteLore, false, null);

        List<String> objectiveLore = definition.objectives().values().stream().limit(8)
                .map(o -> "<gray>• <white>" + safe(o.display()) + " <dark_gray>×</dark_gray><white>" + o.amount())
                .toList();
        item(holder, 22, Material.TARGET, "<aqua><bold>Objectives</bold>", objectiveLore, false, null);
        item(holder, 24, Material.CHEST, "<green><bold>Reward Preview</bold>", List.of(
                "<gray>Configured rewards <white>" + definition.rewards().entries().size(),
                "<gray>Mode <white>" + definition.rewards().mode(),
                "", "<dark_gray>Exact reward delivery remains transaction-backed."), false, null);

        String policy = switch (definition.scope()) {
            case DAILY -> "Assigned by the daily rotation coordinator";
            case WEEKLY -> "Assigned by the weekly rotation coordinator";
            case MILESTONE -> "Assigned automatically when eligibility is satisfied";
            case MANUAL -> "Assigned explicitly by an administrator";
        };
        var evaluation = eligibility.evaluate(player, profile, definition);
        item(holder, 31, stateMaterial(state), stateColor(state) + "<bold>" + state + "</bold>", List.of(
                "<gray>" + safe(policy),
                evaluation.eligible() ? "<green>Eligibility satisfied" : "<red>" + safe(evaluation.reason()),
                "", "<dark_gray>Discovery is read-only; assignment authority is not duplicated."),
                state == JournalState.AVAILABLE, null);
        item(holder, 45, Material.ARROW, "<white>Back", List.of(), false,
                (p, c) -> openAvailable(p, page, category));
        item(holder, 53, Material.BARRIER, "<red>Close", List.of(), false, (p, c) -> p.closeInventory());
        player.openInventory(holder.inventory);
    }

    private void toggleTracked(Player player, QuestAssignment assignment) {
        PlayerProfile profile = profile(player);
        if (profile == null || assignment.state() != AssignmentState.ACTIVE) return;
        boolean old = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
        profile.pinnedAssignment(old ? null : assignment.id());
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), !old));
        player.sendMessage(text.parse(old ? "<gray>Tracked quest cleared." : "<yellow>Quest is now tracked."));
        openActive(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        Action action = holder.actions.get(slot);
        if (action != null) action.run(player, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private Holder create(View view, String title) {
        Holder holder = new Holder(view);
        holder.inventory = Bukkit.createInventory(holder, 54, text.parse(title));
        ItemStack filler = items.create(Material.GRAY_STAINED_GLASS_PANE, Component.empty(), List.of(), false);
        for (int i = 0; i < holder.inventory.getSize(); i++) holder.inventory.setItem(i, filler);
        return holder;
    }

    private void navigation(Holder holder, View selected) {
        nav(holder, 0, Material.COMPASS, "Overview", View.OVERVIEW, selected, this::openOverview);
        nav(holder, 1, Material.WRITABLE_BOOK, "Active", View.ACTIVE, selected, this::openActive);
        nav(holder, 2, Material.ENDER_EYE, "Available", View.AVAILABLE, selected, p -> openAvailable(p, 0, null));
        nav(holder, 3, Material.BOOKSHELF, "Categories", View.CATEGORIES, selected, this::openCategories);
        nav(holder, 4, Material.LODESTONE, "Tracked", View.TRACKED, selected, this::openTracked);
        nav(holder, 5, Material.KNOWLEDGE_BOOK, "Completed", View.COMPLETED, selected, this::openCompleted);
        nav(holder, 6, Material.EXPERIENCE_BOTTLE, "Statistics", View.STATISTICS, selected, this::openStatistics);
        nav(holder, 7, Material.PAPER, "Help", View.HELP, selected, this::openHelp);
        item(holder, 8, Material.BARRIER, "<red>Close", List.of("<dark_gray>Close the quest journal"), false,
                (p, c) -> p.closeInventory());
    }

    private void nav(Holder holder, int slot, Material material, String name, View view, View selected, Consumer<Player> open) {
        boolean active = view == selected;
        item(holder, slot, material,
                active ? "<gradient:#F6C85F:#E9A83A><bold>" + name + "</bold></gradient>" : "<white>" + name,
                List.of(active ? "<gold>Selected" : "<dark_gray>Click to open"), active,
                (p, c) -> open.accept(p));
    }

    private void pages(Holder holder, int page, int pages, String category) {
        if (page > 0) item(holder, 45, Material.ARROW, "<white>Previous",
                List.of("<gray>Page " + page + "/" + pages), false, (p, c) -> openAvailable(p, page - 1, category));
        item(holder, 49, Material.MAP, "<gold>Page " + (page + 1) + "<dark_gray>/</dark_gray><white>" + pages,
                List.of(category == null ? "<gray>All categories" : "<gray>Category <white>" + safe(category)), false, null);
        if (page + 1 < pages) item(holder, 53, Material.ARROW, "<white>Next",
                List.of("<gray>Page " + (page + 2) + "/" + pages), false, (p, c) -> openAvailable(p, page + 1, category));
    }

    private ItemStack assignmentCard(QuestAssignment assignment, boolean tracked) {
        var progress = assignment.displayProgress();
        String productState = assignment.state() == AssignmentState.COMPLETED ? "COMPLETABLE" : tracked ? "TRACKED" : "ACTIVE";
        List<Component> lore = List.of(
                text.parse("<dark_gray>" + assignment.definition().scope() + " • " + safe(pretty(assignment.definition().category()))),
                Component.empty(),
                text.parse("<gray>State " + (assignment.state() == AssignmentState.COMPLETED ? "<green>" : tracked ? "<yellow>" : "<gold>") + productState),
                text.parse("<gray>Progress <white>" + text.formatNumber(progress.current()) + "<dark_gray>/</dark_gray><white>" + text.formatNumber(progress.required())),
                text.progressBar(progress.percentage()),
                Component.empty(),
                text.parse("<dark_gray>Left-click details • Right-click track"));
        return items.create(assignment.definition().display().icon().material(),
                text.parse(assignment.definition().display().name()), lore, tracked || assignment.state() == AssignmentState.COMPLETED);
    }

    private ItemStack definitionCard(Player player, PlayerProfile profile, QuestDefinition definition) {
        JournalState state = states.resolve(player, profile, definition);
        Set<String> missing = states.missingPrerequisites(player.getUniqueId(), definition.id());
        List<Component> lore = new ArrayList<>();
        lore.add(text.parse("<dark_gray>" + definition.scope() + " • " + safe(pretty(definition.category()))));
        lore.add(Component.empty());
        lore.add(text.parse("<gray>State " + stateColor(state) + state));
        lore.add(text.parse("<gray>Objectives <white>" + definition.objectives().size()));
        lore.add(text.parse("<gray>Rewards <white>" + definition.rewards().entries().size()));
        if (!missing.isEmpty()) lore.add(text.parse("<gray>Missing <red>" + safe(String.join(", ", missing))));
        lore.add(Component.empty());
        lore.add(text.parse("<dark_gray>Click for objective, reward and prerequisite preview"));
        return items.create(definition.display().icon().material(), text.parse(definition.display().name()), lore,
                state == JournalState.AVAILABLE);
    }

    private Counts count(Player player, PlayerProfile profile) {
        Counts result = new Counts();
        for (QuestDefinition definition : configs.snapshot().registry().quests().values()) {
            switch (states.resolve(player, profile, definition)) {
                case ACTIVE, TRACKED -> result.active++;
                case AVAILABLE -> result.available++;
                case LOCKED -> result.locked++;
                case COMPLETABLE -> result.completable++;
                default -> { }
            }
        }
        return result;
    }

    private Map<String, Long> categoryCounts() {
        Map<String, Long> result = new LinkedHashMap<>();
        configs.snapshot().registry().quests().values().forEach(q -> result.merge(q.category(), 1L, Long::sum));
        return Map.copyOf(result);
    }

    private List<String> trackedLore(PlayerProfile profile) {
        QuestAssignment assignment = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (assignment == null) return List.of("<gray>No quest tracked", "", "<dark_gray>Right-click an active quest to track it");
        var progress = assignment.displayProgress();
        return List.of(
                "<gray>Quest <white>" + safe(text.plain(text.parse(assignment.definition().display().name()))),
                "<gray>Progress <white>" + progress.current() + "<dark_gray>/</dark_gray><white>" + progress.required(),
                "", "<dark_gray>Click to open tracked quest");
    }

    private void item(Holder holder, int slot, Material material, String name, List<String> lore, boolean glow, Action action) {
        holder.inventory.setItem(slot, items.create(material, text.parse(name), components(lore), glow));
        if (action != null) holder.actions.put(slot, action);
    }

    private List<Component> components(List<String> lines) {
        return lines.stream().map(line -> line.isEmpty() ? Component.empty() : text.parse(line)).toList();
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile value = profiles.profile(player).orElse(null);
        if (value == null) player.sendMessage(text.parse("<yellow>Your quest profile is still loading."));
        return value;
    }

    private static int order(JournalState state) {
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

    private enum View { OVERVIEW, ACTIVE, AVAILABLE, CATEGORIES, TRACKED, COMPLETED, STATISTICS, HELP, DEFINITION }

    private static final class Counts {
        private int active;
        private int available;
        private int locked;
        private int completable;
    }

    @FunctionalInterface
    private interface Action {
        void run(Player player, ClickType click);
    }

    private static final class Holder implements InventoryHolder {
        private final View view;
        private final Map<Integer, Action> actions = new HashMap<>();
        private Inventory inventory;

        private Holder(View view) {
            this.view = view;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

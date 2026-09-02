package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.QuestFileEditor;
import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.persistence.HistoryEntry;
import com.zpkdxgames.plexonquests.persistence.StorageDiagnostics;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.rotation.RerollService;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.FeedbackChannel;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.SlotResolver;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final StorageService storage;
    private final RewardService rewards;
    private final RerollService rerolls;
    private final IntegrationManager integrations;
    private final BlockOriginService origins;
    private final TextService text;
    private final ItemFactory itemFactory = new ItemFactory();
    private final QuestItemRenderer renderer;
    private final SlotResolver slotResolver = new SlotResolver();
    private final QuestFileEditor editor;
    private final Executor configExecutor;

    public MenuService(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            StorageService storage,
            RewardService rewards,
            RerollService rerolls,
            IntegrationManager integrations,
            BlockOriginService origins,
            TextService text,
            Executor configExecutor) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.storage = storage;
        this.rewards = rewards;
        this.rerolls = rerolls;
        this.integrations = integrations;
        this.origins = origins;
        this.text = text;
        this.renderer = new QuestItemRenderer(configs, text, itemFactory);
        this.editor = new QuestFileEditor(configs.dataDirectory());
        this.configExecutor = configExecutor;
    }

    public void openJournal(Player player, QuestScope scope) {
        QuestScope initialScope = scope == null ? QuestScope.DAILY : scope;
        openJournal(player, new MenuContext(MenuType.JOURNAL, initialScope, QuestStatusFilter.ALL, 0, null, null));
    }

    public void openJournal(Player player, MenuContext context) {
        PlayerProfile profile = requireProfile(player);
        if (profile == null) {
            return;
        }
        QuestScope selectedScope = context.scope() == null ? QuestScope.DAILY : context.scope();
        MenuContext journalContext = new MenuContext(
                MenuType.JOURNAL, selectedScope, context.filter(), context.page(), null, null);
        int size = configs.snapshot().menus().integer("journal.size", 54);
        QuestMenuHolder holder = holder(player, journalContext, size, "journal.title", "<aqua>Quest Journal");
        fill(holder);
        List<QuestAssignment> filtered = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.definition().scope() == selectedScope)
                .filter(assignment -> matches(journalContext.filter(), assignment.state()))
                .sorted(Comparator.comparingInt((QuestAssignment assignment) -> stateOrder(assignment.state()))
                        .thenComparing(QuestAssignment::assignedAt))
                .toList();
        List<Integer> slots = configs.snapshot().menus().integers("journal.quest-slots");
        int pages = Math.max(1, (filtered.size() + slots.size() - 1) / slots.size());
        int page = Math.max(0, Math.min(context.page(), pages - 1));
        int offset = page * slots.size();
        for (int index = 0; index < slots.size(); index++) {
            int sourceIndex = offset + index;
            if (sourceIndex >= filtered.size()) {
                break;
            }
            QuestAssignment assignment = filtered.get(sourceIndex);
            int slot = slots.get(index);
            holder.getInventory().setItem(slot, renderer.questCard(
                    player, assignment, profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()));
            holder.action(slot, (viewer, click) -> handleQuestCard(viewer, journalContext, assignment.id(), click));
        }
        if (filtered.isEmpty() && !slots.isEmpty()) {
            holder.getInventory().setItem(slots.get(slots.size() / 2), renderer.configured(
                    "journal.empty",
                    text.placeholders(
                            "scope_name", scopeName(selectedScope).toLowerCase(Locale.ROOT),
                            "filter_name", filterName(journalContext.filter()).toLowerCase(Locale.ROOT))));
        }
        bindTabs(holder, journalContext);
        setJournalControls(holder, player, profile, journalContext, page, pages);
        player.openInventory(holder.getInventory());
    }

    public void openDetails(Player player, QuestAssignment assignment, MenuContext parent) {
        int size = configs.snapshot().menus().integer("details.size", 54);
        MenuContext context = new MenuContext(
                MenuType.DETAILS, assignment.definition().scope(), QuestStatusFilter.ALL, 0, assignment.id(), parent);
        QuestMenuHolder holder = holder(player, context, size, "details.title", "<aqua>Quest Details");
        fill(holder);
        holder.getInventory().setItem(
                configs.snapshot().menus().integer("details.icon-slot", 4), renderer.detailHeader(assignment));
        List<Integer> objectiveSlots = configs.snapshot().menus().integers("details.objective-slots");
        boolean previousComplete = true;
        int index = 0;
        for (ObjectiveProgress objective : assignment.objectives()) {
            if (index >= objectiveSlots.size()) {
                break;
            }
            boolean locked = assignment.definition().completionMode() == CompletionMode.SEQUENCE && !previousComplete;
            holder.getInventory().setItem(objectiveSlots.get(index++), renderer.objectiveCard(objective, locked));
            previousComplete &= objective.complete();
        }
        List<Integer> rewardSlots = configs.snapshot().menus().integers("details.reward-slots");
        for (int rewardIndex = 0;
                rewardIndex < rewardSlots.size() && rewardIndex < assignment.definition().rewards().entries().size();
                rewardIndex++) {
            holder.getInventory().setItem(
                    rewardSlots.get(rewardIndex),
                    renderer.rewardCard(assignment.definition().rewards().entries().get(rewardIndex)));
        }
        bindCommon(holder, "details", parent);
        int claimSlot = configs.snapshot().menus().integer("details.claim-slot", 51);
        boolean preview = parent != null
                && parent.type() == MenuType.ADMIN
                && "preview".equals(assignment.poolId());
        if (preview) {
            holder.getInventory().setItem(claimSlot, renderer.configured("details.preview", Map.of()));
            player.openInventory(holder.getInventory());
            return;
        }

        PlayerProfile profile = profiles.profile(player).orElse(null);
        boolean assignedToPlayer = profile != null && profile.assignment(assignment.id()).isPresent();
        if (assignedToPlayer && !assignment.state().terminal() && player.hasPermission("plexonquests.pin")) {
            int pinSlot = configs.snapshot().menus().integer("details.pin-slot", 47);
            boolean pinned = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();
            holder.getInventory().setItem(pinSlot, renderer.configured("details.pin", text.placeholders(
                    "pin_action_title", pinned ? "Unpin Quest" : "Pin Quest")));
            holder.action(pinSlot, (viewer, click) -> togglePin(viewer, assignment.id(), context));
        }

        if (assignment.state() == AssignmentState.ACTIVE
                && assignment.definition().scope().rotating()
                && configs.snapshot().settings().rerolls().enabled()
                && player.hasPermission("plexonquests.reroll")) {
            int rerollSlot = configs.snapshot().menus().integer("details.reroll-slot", 49);
            int freeRemaining = rerolls.freeRemaining(player, assignment.definition().scope());
            String rerollCost = freeRemaining > 0
                    ? "Free"
                    : configs.snapshot().settings().rerolls().paidEnabled()
                            ? String.format(Locale.US, "%.2f", configs.snapshot().settings().rerolls().paidCost())
                            : "Unavailable";
            holder.getInventory().setItem(rerollSlot, renderer.configured("details.reroll", text.placeholders(
                    "free_rerolls", Integer.toString(freeRemaining),
                    "reroll_cost", rerollCost)));
            holder.action(rerollSlot, (viewer, click) -> rerolls.prepare(
                    viewer, assignment, pending -> openRerollConfirmation(viewer, pending, context)));
        }

        String claimPath = switch (assignment.state()) {
            case COMPLETED -> "details.claim";
            case CLAIMING -> "details.claim-processing";
            default -> "details.claim-locked";
        };
        ItemStack claimItem = renderer.configured(claimPath, Map.of());
        if (assignment.state() == AssignmentState.COMPLETED) {
            var meta = claimItem.getItemMeta();
            meta.lore(text.expandLines(
                    configs.snapshot().menus().strings("details.claim.lore"),
                    Map.of(), Map.of(), Map.of("rewards", rewardLines(assignment))));
            meta.setEnchantmentGlintOverride(true);
            claimItem.setItemMeta(meta);
            holder.action(claimSlot, (viewer, click) -> {
                if (viewer.hasPermission("plexonquests.claim")) {
                    viewer.closeInventory();
                    rewards.claim(viewer, assignment);
                }
            });
        }
        holder.getInventory().setItem(claimSlot, claimItem);
        player.openInventory(holder.getInventory());
    }

    public void openReroll(Player player, QuestAssignment assignment, MenuContext parent) {
        rerolls.prepare(player, assignment, pending -> openRerollConfirmation(player, pending, parent));
    }

    public void openSettings(Player player, MenuContext parent) {
        PlayerProfile profile = requireProfile(player);
        if (profile == null) {
            return;
        }
        MenuContext context = new MenuContext(MenuType.SETTINGS, null, QuestStatusFilter.ALL, 0, null, parent);
        int size = configs.snapshot().menus().integer("settings.size", 36);
        QuestMenuHolder holder = holder(player, context, size, "settings.title", "<aqua>Quest Feedback");
        fill(holder);
        Map<FeedbackChannel, SettingCopy> copy = settingCopy();
        copy.forEach((channel, setting) -> {
            String key = channel.name().toLowerCase(Locale.ROOT).replace('_', '-');
            int slot = configs.snapshot().menus().integer("settings.toggle-slots." + key, -1);
            if (slot < 0 || slot >= size) {
                return;
            }
            boolean enabled = profile.preferences().enabled(channel);
            Material material = Material.matchMaterial(configs.snapshot().menus().string(
                    enabled ? "settings.enabled-material" : "settings.disabled-material",
                    enabled ? "LIME_DYE" : "GRAY_DYE"));
            Map<String, String> values = text.placeholders(
                    "state_color", enabled ? "#72F1B8" : "#8B95A7",
                    "setting_name", setting.name(),
                    "setting_description", setting.description(),
                    "state", enabled ? "Enabled" : "Disabled");
            List<Component> lore = configs.snapshot().menus().strings("settings.toggle-lore").stream()
                    .map(line -> text.parse(line, values))
                    .toList();
            holder.getInventory().setItem(slot, itemFactory.create(
                    material == null ? Material.GRAY_DYE : material,
                    text.parse(configs.snapshot().menus().string("settings.toggle-name", "<white><setting_name>"), values),
                    lore,
                    enabled));
            holder.action(slot, (viewer, click) -> {
                if (!viewer.hasPermission("plexonquests.settings")) {
                    return;
                }
                profile.preferences(profile.preferences().toggle(channel));
                profiles.persistPreferences(profile);
                openSettings(viewer, parent);
            });
        });
        int back = configs.snapshot().menus().integer("settings.back-slot", 31);
        holder.getInventory().setItem(back, renderer.configured("common.back", Map.of()));
        holder.action(back, (viewer, click) -> openContext(viewer, parent));
        player.openInventory(holder.getInventory());
    }

    public void openHistory(Player player, int page, MenuContext parent) {
        if (!player.hasPermission("plexonquests.history")) {
            return;
        }
        int pageSize = configs.snapshot().menus().integers("history.entry-slots").size();
        int currentPage = Math.max(0, page);
        storage.history(player.getUniqueId(), pageSize + 1, currentPage * pageSize).whenComplete((history, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !player.isOnline()) {
                        if (failure != null) {
                            plugin.getLogger().log(Level.WARNING, "Could not load quest history", failure);
                        }
                        return;
                    }
                    boolean hasNext = history.size() > pageSize;
                    List<HistoryEntry> entries = hasNext ? history.subList(0, pageSize) : history;
                    buildHistory(player, entries, currentPage, parent, hasNext);
                }));
    }

    public void openAdmin(Player player, int page) {
        if (!player.hasPermission("plexonquests.admin.gui")) {
            return;
        }
        MenuContext context = new MenuContext(MenuType.ADMIN, null, QuestStatusFilter.ALL, page, null, null);
        int size = configs.snapshot().menus().integer("admin.size", 54);
        QuestMenuHolder holder = holder(player, context, size, "admin.title", "<gold>Quest Administration");
        fill(holder);
        setAdminHealth(holder);
        List<QuestDefinition> definitions = configs.snapshot().registry().quests().values().stream()
                .sorted(Comparator.comparing(QuestDefinition::id))
                .toList();
        List<Integer> slots = configs.snapshot().menus().integers("admin.quest-slots");
        int pages = Math.max(1, (definitions.size() + slots.size() - 1) / slots.size());
        int currentPage = Math.max(0, Math.min(page, pages - 1));
        int offset = currentPage * slots.size();
        for (int index = 0; index < slots.size() && offset + index < definitions.size(); index++) {
            QuestDefinition definition = definitions.get(offset + index);
            int slot = slots.get(index);
            List<Component> lore = List.of(
                    text.parse("<dark_gray>" + definition.scope() + " • " + definition.rarity()),
                    Component.empty(),
                    text.parse("<gray>ID <white>" + definition.id()),
                    text.parse("<gray>State " + (definition.enabled() ? "<green>Enabled" : "<red>Disabled")),
                    text.parse("<gray>Objectives <white>" + definition.objectives().size()),
                    text.parse("<gray>Source <white>" + definition.source()),
                    Component.empty(),
                    text.parse("<yellow>Left-click <gray>preview"),
                    text.parse("<aqua>Right-click <gray>enable or disable"),
                    text.parse("<dark_gray>Advanced fields remain YAML-editable."));
            holder.getInventory().setItem(slot, itemFactory.create(
                    definition.display().icon().material(), text.parse(definition.display().name()), lore, definition.enabled()));
            holder.action(slot, (viewer, click) -> {
                if (click.isRightClick()) {
                    toggleQuest(viewer, definition, currentPage);
                } else {
                    QuestAssignment preview = QuestAssignment.create(
                            viewer.getUniqueId(), definition, "preview", "preview", Instant.now(), null);
                    openDetails(viewer, preview, context);
                }
            });
        }
        setPagination(holder, "admin", currentPage, pages, () -> openAdmin(player, currentPage - 1), () -> openAdmin(player, currentPage + 1));
        int validate = configs.snapshot().menus().integer("admin.validate-slot", 47);
        holder.getInventory().setItem(validate, itemFactory.create(
                Material.WRITABLE_BOOK,
                text.parse("<aqua><bold>Validation Report</bold>"),
                List.of(text.parse("<gray>Errors <white>" + configs.snapshot().registry().errorCount()),
                        text.parse("<yellow>Click <gray>to print details")),
                false));
        holder.action(validate, (viewer, click) -> printValidation(viewer));
        int reload = configs.snapshot().menus().integer("admin.reload-slot", 48);
        holder.getInventory().setItem(reload, itemFactory.create(
                Material.LIME_DYE, text.parse("<green><bold>Reload</bold>"), List.of(text.parse("<gray>Validate and atomically reload.")), false));
        holder.action(reload, (viewer, click) -> reload(viewer, currentPage));
        int close = configs.snapshot().menus().integer("admin.close-slot", 49);
        holder.getInventory().setItem(close, renderer.configured("common.close", Map.of()));
        holder.action(close, (viewer, click) -> viewer.closeInventory());
        int save = configs.snapshot().menus().integer("admin.save-slot", 50);
        holder.getInventory().setItem(save, itemFactory.create(
                Material.CHEST_MINECART, text.parse("<yellow><bold>Flush Progress</bold>"),
                List.of(text.parse("<gray>Checkpoint dirty progress now.")), false));
        holder.action(save, (viewer, click) -> storage.flushDirty().thenAccept(count ->
                Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(text.message("general.save-success", Map.of())))));
        player.openInventory(holder.getInventory());
    }

    public void openContext(Player player, MenuContext context) {
        if (context == null) {
            openJournal(player, QuestScope.DAILY);
            return;
        }
        switch (context.type()) {
            case JOURNAL -> openJournal(player, context);
            case DETAILS -> {
                QuestAssignment assignment = profiles.profile(player)
                        .flatMap(profile -> profile.assignment(context.assignmentId()))
                        .orElse(null);
                if (assignment == null) {
                    openContext(player, context.parent());
                } else {
                    openDetails(player, assignment, context.parent());
                }
            }
            case HISTORY -> openHistory(player, context.page(), context.parent());
            case SETTINGS -> openSettings(player, context.parent());
            case ADMIN -> openAdmin(player, context.page());
            case REROLL_CONFIRMATION -> {
                if (context.parent() != null) {
                    openContext(player, context.parent());
                }
            }
        }
    }

    private void openRerollConfirmation(
            Player player, RerollService.PendingReroll pending, MenuContext parent) {
        MenuContext context = new MenuContext(
                MenuType.REROLL_CONFIRMATION,
                pending.previous().definition().scope(),
                QuestStatusFilter.ALL,
                0,
                pending.previous().id(),
                parent);
        int size = configs.snapshot().menus().integer("confirmation.size", 27);
        QuestMenuHolder holder = holder(player, context, size, "confirmation.title", "<light_purple>Confirm Reroll");
        fill(holder);
        holder.getInventory().setItem(
                configs.snapshot().menus().integer("confirmation.quest-slot", 13),
                renderer.detailHeader(pending.previous()));
        int confirm = configs.snapshot().menus().integer("confirmation.confirm-slot", 11);
        holder.getInventory().setItem(confirm, renderer.configured("confirmation.confirm", Map.of(
                "reroll_cost", pending.cost() <= 0D ? "Free" : String.format(Locale.US, "%.2f", pending.cost()))));
        holder.action(confirm, (viewer, click) -> {
            viewer.closeInventory();
            rerolls.confirm(viewer);
        });
        int cancel = configs.snapshot().menus().integer("confirmation.cancel-slot", 15);
        holder.getInventory().setItem(cancel, renderer.configured("confirmation.cancel", Map.of()));
        holder.action(cancel, (viewer, click) -> {
            rerolls.cancel(viewer);
            openContext(viewer, parent);
        });
        player.openInventory(holder.getInventory());
    }

    private void buildHistory(
            Player player, List<HistoryEntry> history, int page, MenuContext parent, boolean hasNext) {
        MenuContext context = new MenuContext(MenuType.HISTORY, null, QuestStatusFilter.ALL, page, null, parent);
        int size = configs.snapshot().menus().integer("history.size", 54);
        QuestMenuHolder holder = holder(player, context, size, "history.title", "<light_purple>Quest History");
        fill(holder);
        List<Integer> slots = configs.snapshot().menus().integers("history.entry-slots");
        for (int index = 0; index < slots.size() && index < history.size(); index++) {
            HistoryEntry entry = history.get(index);
            List<Component> lore = List.of(
                    text.parse("<dark_gray>" + entry.scope() + " • " + entry.rarity()),
                    Component.empty(),
                    text.parse("<gray>Result <white>" + entry.state()),
                    text.parse("<gray>Objectives <white>" + entry.objectiveSummary()),
                    text.parse("<gray>Rewards <white>" + entry.rewardSummary()),
                    Component.empty(),
                    text.parse("<dark_gray>Read-only history entry"));
            holder.getInventory().setItem(slots.get(index), itemFactory.create(
                    Material.BOOK, text.parse(entry.displayName()), lore, false));
        }
        int back = configs.snapshot().menus().integer("history.back-slot", 49);
        holder.getInventory().setItem(back, renderer.configured("common.back", Map.of()));
        holder.action(back, (viewer, click) -> openContext(viewer, parent));
        int previous = configs.snapshot().menus().integer("history.previous-slot", 45);
        if (page > 0) {
            holder.getInventory().setItem(previous, renderer.configured("common.previous", Map.of(
                    "page", Integer.toString(page), "pages", "?")));
            holder.action(previous, (viewer, click) -> openHistory(viewer, page - 1, parent));
        }
        int next = configs.snapshot().menus().integer("history.next-slot", 53);
        if (hasNext) {
            holder.getInventory().setItem(next, renderer.configured("common.next", Map.of(
                    "page", Integer.toString(page + 2), "pages", "?")));
            holder.action(next, (viewer, click) -> openHistory(viewer, page + 1, parent));
        }
        player.openInventory(holder.getInventory());
    }

    private void handleQuestCard(Player player, MenuContext context, UUID assignmentId, ClickType click) {
        PlayerProfile profile = requireProfile(player);
        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (assignment == null) {
            openJournal(player, context);
            return;
        }
        if (click.isRightClick()) {
            togglePin(player, assignmentId, context);
        } else if (click.isShiftClick() && click.isLeftClick()) {
            rerolls.prepare(player, assignment, pending -> openRerollConfirmation(player, pending, context));
        } else if (click.isLeftClick()) {
            openDetails(player, assignment, context);
        }
    }

    private void togglePin(Player player, UUID assignmentId, MenuContext returnContext) {
        if (!player.hasPermission("plexonquests.pin")) {
            return;
        }
        PlayerProfile profile = requireProfile(player);
        if (profile == null || profile.assignment(assignmentId).isEmpty()) {
            return;
        }
        boolean unpin = profile.pinnedAssignment().filter(assignmentId::equals).isPresent();
        profile.pinnedAssignment(unpin ? null : assignmentId);
        profiles.persistPreferences(profile);
        String path = unpin ? "quests.unpinned" : "quests.pinned";
        QuestAssignment assignment = profile.assignment(assignmentId).orElseThrow();
        player.sendMessage(text.message(path, Map.of(
                "quest_name", text.plain(text.parse(assignment.definition().display().name())))));
        openContext(player, returnContext);
    }

    private void bindTabs(QuestMenuHolder holder, MenuContext context) {
        bindTab(holder, context, "daily", QuestScope.DAILY);
        bindTab(holder, context, "weekly", QuestScope.WEEKLY);
        bindTab(holder, context, "milestones", QuestScope.MILESTONE);
        bindTab(holder, context, "manual", QuestScope.MANUAL);
    }

    private void bindTab(QuestMenuHolder holder, MenuContext context, String key, QuestScope scope) {
        String path = "journal.tabs." + key;
        int slot = configs.snapshot().menus().integer(path + ".slot", scope.ordinal());
        boolean selected = context.scope() == scope;
        holder.getInventory().setItem(slot, renderer.configured(
                path,
                text.placeholders(
                        "tab_state", selected ? "Selected" : "Click to view",
                        "tab_state_color", selected ? "#72F1B8" : "#8B95A7"),
                Map.of(),
                selected));
        holder.action(slot, (viewer, click) -> openJournal(
                viewer, new MenuContext(MenuType.JOURNAL, scope, context.filter(), 0, null, null)));
    }

    private void setJournalControls(
            QuestMenuHolder holder,
            Player player,
            PlayerProfile profile,
            MenuContext context,
            int page,
            int pages) {
        setPagination(holder, "journal", page, pages,
                () -> openJournal(player, new MenuContext(MenuType.JOURNAL, context.scope(), context.filter(), page - 1, null, null)),
                () -> openJournal(player, new MenuContext(MenuType.JOURNAL, context.scope(), context.filter(), page + 1, null, null)));
        int dailyLimit = slotResolver.resolve(
                player, QuestScope.DAILY, profile.rankCategory(), configs.snapshot().settings());
        int weeklyLimit = slotResolver.resolve(
                player, QuestScope.WEEKLY, profile.rankCategory(), configs.snapshot().settings());
        long dailyUsed = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.definition().scope() == QuestScope.DAILY)
                .count();
        long weeklyUsed = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.definition().scope() == QuestScope.WEEKLY)
                .count();
        long manualUsed = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.definition().scope() == QuestScope.MANUAL)
                .count();
        int summarySlot = configs.snapshot().menus().integer("journal.summary-slot", 47);
        ItemStack summary = renderer.configured("journal.summary", text.placeholders(
                "player", player.getName(),
                "rank_category", profile.rankCategory(),
                "claimable", Long.toString(profile.claimableCount()),
                "completed_total", Long.toString(profile.completedTotal()),
                "daily_used", Long.toString(dailyUsed),
                "daily_limit", Integer.toString(dailyLimit),
                "daily_rerolls", Integer.toString(rerolls.freeRemaining(player, QuestScope.DAILY)),
                "weekly_used", Long.toString(weeklyUsed),
                "weekly_limit", Integer.toString(weeklyLimit),
                "weekly_rerolls", Integer.toString(rerolls.freeRemaining(player, QuestScope.WEEKLY)),
                "manual_used", Long.toString(manualUsed),
                "manual_limit", Integer.toString(configs.snapshot().settings().assignments().maximumActiveManual())));
        if (summary.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            summary.setItemMeta(skull);
        }
        holder.getInventory().setItem(summarySlot, summary);
        int pinnedSlot = configs.snapshot().menus().integer("journal.pinned-slot", 48);
        QuestAssignment pinned = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (pinned == null) {
            holder.getInventory().setItem(pinnedSlot, renderer.configured("journal.pinned-empty", Map.of()));
        } else {
            double percentage = pinned.percentage();
            holder.getInventory().setItem(pinnedSlot, renderer.configured(
                    "journal.pinned",
                    text.placeholders(
                            "pinned_name", text.plain(text.parse(pinned.definition().display().name())),
                            "percentage", Integer.toString((int) Math.floor(percentage)),
                            "progress_color", text.progressColor(percentage)),
                    Map.of("progress_bar", text.progressBar(percentage))));
            holder.action(pinnedSlot, (viewer, click) -> openDetails(viewer, pinned, context));
        }
        int filter = configs.snapshot().menus().integer("journal.filter-slot", 49);
        holder.getInventory().setItem(filter, renderer.configured(
                "journal.filter", Map.of("filter", filterName(context.filter()))));
        holder.action(filter, (viewer, click) -> openJournal(
                viewer, new MenuContext(MenuType.JOURNAL, context.scope(), context.filter().next(), 0, null, null)));
        if (player.hasPermission("plexonquests.history")) {
            int history = configs.snapshot().menus().integer("journal.history-slot", 50);
            holder.getInventory().setItem(history, renderer.configured("journal.history", Map.of()));
            holder.action(history, (viewer, click) -> openHistory(viewer, 0, context));
        }
        if (player.hasPermission("plexonquests.settings")) {
            int settings = configs.snapshot().menus().integer("journal.settings-slot", 51);
            holder.getInventory().setItem(settings, renderer.configured("journal.settings", Map.of()));
            holder.action(settings, (viewer, click) -> openSettings(viewer, context));
        }
    }

    private void bindCommon(QuestMenuHolder holder, String menu, MenuContext parent) {
        int back = configs.snapshot().menus().integer(menu + ".back-slot", 45);
        holder.getInventory().setItem(back, renderer.configured("common.back", Map.of()));
        holder.action(back, (viewer, click) -> openContext(viewer, parent));
        int close = configs.snapshot().menus().integer(menu + ".close-slot", 53);
        holder.getInventory().setItem(close, renderer.configured("common.close", Map.of()));
        holder.action(close, (viewer, click) -> viewer.closeInventory());
    }

    private void setPagination(
            QuestMenuHolder holder, String menu, int page, int pages, Runnable previousAction, Runnable nextAction) {
        if (page > 0) {
            int previous = configs.snapshot().menus().integer(menu + ".previous-slot", 45);
            holder.getInventory().setItem(previous, renderer.configured("common.previous", Map.of(
                    "page", Integer.toString(page), "pages", Integer.toString(pages))));
            holder.action(previous, (viewer, click) -> previousAction.run());
        }
        if (page + 1 < pages) {
            int next = configs.snapshot().menus().integer(menu + ".next-slot", 52);
            holder.getInventory().setItem(next, renderer.configured("common.next", Map.of(
                    "page", Integer.toString(page + 2), "pages", Integer.toString(pages))));
            holder.action(next, (viewer, click) -> nextAction.run());
        }
    }

    private void setAdminHealth(QuestMenuHolder holder) {
        List<Integer> slots = configs.snapshot().menus().integers("admin.health-slots");
        StorageDiagnostics storageHealth = storage.diagnostics();
        List<HealthCard> cards = List.of(
                new HealthCard("loaded", List.of(
                        "<gray>Loaded quests <white>" + configs.snapshot().registry().quests().size(),
                        "<gray>Loaded pools <white>" + configs.snapshot().registry().pools().size(),
                        "<gray>Invalid definitions <white>" + configs.snapshot().registry().errorCount())),
                new HealthCard("assignments", List.of("<gray>Online profiles <white>" + profiles.onlineCount())),
                new HealthCard("database", List.of(
                        "<gray>Status " + (storageHealth.open() ? "<green>Healthy" : "<red>Closed"),
                        "<gray>Last flush <white>" + storageHealth.lastFlushResult())),
                new HealthCard("queue", List.of(
                        "<gray>Depth <white>" + storageHealth.queueDepth() + "<dark_gray>/</dark_gray><white>" + storageHealth.queueCapacity(),
                        "<gray>Dirty <white>" + storageHealth.dirtyAssignments())),
                new HealthCard("integrations", integrations.states().values().stream()
                        .map(state -> "<gray>" + state.id() + " <white>" + state.status()).toList()),
                new HealthCard("origin", List.of(
                        "<gray>Loaded chunks <white>" + origins.loadedChunkCount(),
                        "<gray>Tracked positions <white>" + origins.trackedPositionCount())),
                new HealthCard("uncertainty", List.of("<gray>Review /quests diagnostics for uncertain transactions.")));
        for (int index = 0; index < slots.size() && index < cards.size(); index++) {
            HealthCard card = cards.get(index);
            String path = "admin.health." + card.key();
            Material material = Material.matchMaterial(configs.snapshot().menus().string(path + ".material", "PAPER"));
            holder.getInventory().setItem(slots.get(index), itemFactory.create(
                    material == null ? Material.PAPER : material,
                    text.parse(configs.snapshot().menus().string(path + ".name", card.key())),
                    card.lore().stream().map(text::parse).toList(),
                    false));
        }
    }

    private void toggleQuest(Player player, QuestDefinition definition, int page) {
        if (!player.hasPermission("plexonquests.admin.gui")) {
            return;
        }
        player.closeInventory();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        editor.toggleEnabled(definition);
                    } catch (Exception exception) {
                        throw new java.util.concurrent.CompletionException(exception);
                    }
                }, configExecutor)
                .thenCompose(ignored -> configs.reloadAsync(configExecutor))
                .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !result.success()) {
                        player.sendMessage(text.message("general.reload-failed", Map.of()));
                        if (failure != null) {
                            plugin.getLogger().log(Level.WARNING, "Quest toggle failed", failure);
                        }
                    } else {
                        player.sendMessage(text.message("general.reload-success", Map.of(
                                "quests", Integer.toString(result.snapshot().registry().quests().size()),
                                "pools", Integer.toString(result.snapshot().registry().pools().size()))));
                        openAdmin(player, page);
                    }
                }));
    }

    private void reload(Player player, int page) {
        if (!player.hasPermission("plexonquests.admin.reload")) {
            return;
        }
        configs.reloadAsync(configExecutor).whenComplete((result, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !result.success()) {
                        player.sendMessage(text.message("general.reload-failed", Map.of()));
                    } else {
                        player.sendMessage(text.message("general.reload-success", Map.of(
                                "quests", Integer.toString(result.snapshot().registry().quests().size()),
                                "pools", Integer.toString(result.snapshot().registry().pools().size()))));
                        openAdmin(player, page);
                    }
                }));
    }

    private void printValidation(Player player) {
        player.sendMessage(text.rawMessage("admin.validation-header", Map.of()));
        if (configs.snapshot().registry().issues().isEmpty()) {
            player.sendMessage(text.parse("<green>No definition issues were found."));
            return;
        }
        configs.snapshot().registry().issues().stream().limit(20).forEach(issue ->
                player.sendMessage(text.parse(
                        (issue.severity() == com.zpkdxgames.plexonquests.config.ValidationIssue.Severity.ERROR
                                        ? "<red>ERROR " : "<yellow>WARN ")
                                + "<white><path> <gray><message>",
                        Map.of("path", issue.path(), "message", issue.message()))));
    }

    private List<Component> rewardLines(QuestAssignment assignment) {
        return assignment.definition().rewards().entries().stream()
                .map(reward -> text.parse("<dark_gray>• <gray><reward>", Map.of("reward", reward.display())))
                .toList();
    }

    private PlayerProfile requireProfile(Player player) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            player.sendMessage(text.message("general.not-ready", Map.of()));
        }
        return profile;
    }

    private QuestMenuHolder holder(
            Player player, MenuContext context, int size, String titlePath, String fallback) {
        QuestMenuHolder holder = new QuestMenuHolder(player.getUniqueId(), context);
        holder.create(size, text.parse(configs.snapshot().menus().string(titlePath, fallback)));
        return holder;
    }

    private void fill(QuestMenuHolder holder) {
        ItemStack filler = renderer.filler();
        for (int slot = 0; slot < holder.getInventory().getSize(); slot++) {
            holder.getInventory().setItem(slot, filler);
        }
    }

    private static boolean matches(QuestStatusFilter filter, AssignmentState state) {
        return switch (filter) {
            case ALL -> state != AssignmentState.CANCELLED;
            case ACTIVE -> state == AssignmentState.ACTIVE;
            case COMPLETED -> state == AssignmentState.COMPLETED || state == AssignmentState.CLAIMING;
        };
    }

    private static int stateOrder(AssignmentState state) {
        return switch (state) {
            case COMPLETED -> 0;
            case ACTIVE -> 1;
            case CLAIMING -> 2;
            case CLAIMED -> 3;
            case EXPIRED -> 4;
            case CANCELLED -> 5;
        };
    }

    private static String filterName(QuestStatusFilter filter) {
        return switch (filter) {
            case ALL -> "All quests";
            case ACTIVE -> "In progress";
            case COMPLETED -> "Ready to claim";
        };
    }

    private static String scopeName(QuestScope scope) {
        return switch (scope) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MILESTONE -> "Milestone";
            case MANUAL -> "Assigned";
        };
    }

    private static Map<FeedbackChannel, SettingCopy> settingCopy() {
        return Map.of(
                FeedbackChannel.ACTIONBAR, new SettingCopy("Actionbar Progress", "Compact progress above the hotbar."),
                FeedbackChannel.BOSSBAR, new SettingCopy("Bossbar Progress", "Ephemeral progress after contributions."),
                FeedbackChannel.PROGRESS_SOUNDS, new SettingCopy("Progress Sounds", "Sounds at important thresholds."),
                FeedbackChannel.COMPLETION_SOUNDS, new SettingCopy("Completion Sounds", "Celebration and claim sounds."),
                FeedbackChannel.PARTICLES, new SettingCopy("Particles", "Small completion particle bursts."),
                FeedbackChannel.TITLES, new SettingCopy("Titles", "Quest-complete screen titles."),
                FeedbackChannel.JOIN_REMINDERS, new SettingCopy("Join Reminders", "One subtle reminder after joining."),
                FeedbackChannel.REDUCED_MOTION, new SettingCopy("Reduced Motion", "Suppress particles and screen-heavy effects."));
    }

    private record SettingCopy(String name, String description) {}

    private record HealthCard(String key, List<String> lore) {}
}

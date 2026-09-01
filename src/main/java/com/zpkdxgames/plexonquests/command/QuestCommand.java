package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ValidationIssue;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.gui.MenuContext;
import com.zpkdxgames.plexonquests.gui.MenuService;
import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.persistence.StorageDiagnostics;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ProgressResult;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.rotation.RotationService;
import com.zpkdxgames.plexonquests.service.AssignmentService;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class QuestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ROOTS = List.of(
            "daily", "weekly", "milestones", "pinned", "history", "claim", "reroll", "settings", "info",
            "admin", "reload", "validate", "assign", "cancel", "progress", "complete", "reset", "rotate",
            "save", "diagnostics");

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final AssignmentService assignments;
    private final ProgressService progress;
    private final RotationService rotations;
    private final RewardService rewards;
    private final MenuService menus;
    private final StorageService storage;
    private final IntegrationManager integrations;
    private final BlockOriginService origins;
    private final TextService text;
    private final Executor configExecutor;
    private final Map<UUID, Long> commandTimes = new ConcurrentHashMap<>();

    public QuestCommand(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            AssignmentService assignments,
            ProgressService progress,
            RotationService rotations,
            RewardService rewards,
            MenuService menus,
            StorageService storage,
            IntegrationManager integrations,
            BlockOriginService origins,
            TextService text,
            Executor configExecutor) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.assignments = assignments;
        this.progress = progress;
        this.rotations = rotations;
        this.rewards = rewards;
        this.menus = menus;
        this.storage = storage;
        this.integrations = integrations;
        this.origins = origins;
        this.text = text;
        this.configExecutor = configExecutor;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (sender instanceof Player player && throttled(player)) {
            return true;
        }
        String root = args.length == 0 ? "journal" : args[0].toLowerCase(Locale.ROOT);
        try {
            return switch (root) {
                case "journal" -> open(sender, null);
                case "daily" -> open(sender, QuestScope.DAILY);
                case "weekly" -> open(sender, QuestScope.WEEKLY);
                case "milestones", "milestone" -> open(sender, QuestScope.MILESTONE);
                case "pinned" -> pinned(sender);
                case "history" -> history(sender);
                case "claim" -> claim(sender, argument(args, 1));
                case "reroll" -> reroll(sender, argument(args, 1));
                case "settings" -> settings(sender);
                case "info", "help", "?" -> info(sender);
                case "admin" -> admin(sender);
                case "reload" -> reload(sender);
                case "validate" -> validate(sender);
                case "assign" -> assign(sender, args);
                case "cancel" -> cancel(sender, args);
                case "progress" -> adjustProgress(sender, args);
                case "complete" -> complete(sender, args);
                case "reset" -> reset(sender, args);
                case "rotate" -> rotate(sender, args);
                case "save" -> save(sender);
                case "diagnostics" -> diagnostics(sender);
                default -> usage(sender, "/quests [daily|weekly|milestones|history|settings|info]");
            };
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Quest command failed for " + sender.getName(), exception);
            sender.sendMessage(text.message("general.internal-error", Map.of()));
            return true;
        }
    }

    private boolean open(CommandSender sender, QuestScope scope) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "plexonquests.use")) {
            return true;
        }
        menus.openJournal(player, scope);
        return true;
    }

    private boolean pinned(CommandSender sender) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "plexonquests.pin")) {
            return true;
        }
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return true;
        }
        QuestAssignment assignment = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);
        if (assignment == null) {
            menus.openJournal(player, (QuestScope) null);
        } else {
            menus.openDetails(player, assignment, MenuContext.journal(assignment.definition().scope()));
        }
        return true;
    }

    private boolean history(CommandSender sender) {
        Player player = player(sender);
        if (player != null && allowed(sender, "plexonquests.history")) {
            menus.openHistory(player, 0, MenuContext.journal(null));
        }
        return true;
    }

    private boolean settings(CommandSender sender) {
        Player player = player(sender);
        if (player != null && allowed(sender, "plexonquests.settings")) {
            menus.openSettings(player, MenuContext.journal(null));
        }
        return true;
    }

    private boolean claim(CommandSender sender, String token) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "plexonquests.claim")) {
            return true;
        }
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return true;
        }
        QuestAssignment assignment = resolve(profile, token, AssignmentState.COMPLETED);
        if (assignment == null || assignment.state() != AssignmentState.COMPLETED) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        rewards.claim(player, assignment);
        return true;
    }

    private boolean reroll(CommandSender sender, String token) {
        Player player = player(sender);
        if (player == null || !allowed(sender, "plexonquests.reroll")) {
            return true;
        }
        PlayerProfile profile = profile(player);
        if (profile == null) {
            return true;
        }
        QuestAssignment assignment = resolve(profile, token, AssignmentState.ACTIVE);
        if (assignment == null || !assignment.definition().scope().rotating()) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        menus.openReroll(player, assignment, MenuContext.journal(assignment.definition().scope()));
        return true;
    }

    private boolean info(CommandSender sender) {
        for (String line : configs.snapshot().messages().strings("info.lines")) {
            sender.sendMessage(text.parse(line, Map.of("version", plugin.getPluginMeta().getVersion())));
        }
        return true;
    }

    private boolean admin(CommandSender sender) {
        Player player = player(sender);
        if (player != null && allowed(sender, "plexonquests.admin.gui")) {
            menus.openAdmin(player, 0);
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, "plexonquests.admin.reload")) {
            return true;
        }
        configs.reloadAsync(configExecutor).whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null || !result.success()) {
                sender.sendMessage(text.message("general.reload-failed", Map.of()));
                return;
            }
            integrations.detect();
            profiles.onlineProfiles().forEach(profile -> {
                profiles.refreshRankCategory(profile);
                progress.reindex(profile);
            });
            sender.sendMessage(text.message("general.reload-success", Map.of(
                    "quests", Integer.toString(result.snapshot().registry().quests().size()),
                    "pools", Integer.toString(result.snapshot().registry().pools().size()))));
        }));
        return true;
    }

    private boolean validate(CommandSender sender) {
        if (!allowed(sender, "plexonquests.admin.validate")) {
            return true;
        }
        sender.sendMessage(text.rawMessage("admin.validation-header", Map.of()));
        List<ValidationIssue> issues = configs.snapshot().registry().issues();
        if (issues.isEmpty()) {
            sender.sendMessage(text.parse(configs.snapshot().messages().string("admin.validation-clean", "<green>No issues.")));
        } else {
            issues.stream().limit(50).forEach(issue -> sender.sendMessage(text.parse(
                    (issue.severity() == ValidationIssue.Severity.ERROR ? "<red>ERROR " : "<yellow>WARN ")
                            + "<white><path> <gray><message>",
                    Map.of("path", issue.path(), "message", issue.message()))));
        }
        return true;
    }

    private boolean assign(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.assign")) {
            return true;
        }
        if (args.length != 3) {
            return usage(sender, "/quests assign <player> <quest>");
        }
        Player target = target(sender, args[1]);
        if (target == null) {
            return true;
        }
        PlayerProfile profile = profile(target);
        QuestDefinition definition = configs.snapshot().registry().quests().get(args[2].toLowerCase(Locale.ROOT));
        if (profile == null || definition == null || definition.scope() != QuestScope.MANUAL) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        long manual = profile.assignments(QuestScope.MANUAL).stream()
                .filter(assignment -> !assignment.state().terminal()).count();
        if (manual >= configs.snapshot().settings().assignments().maximumActiveManual()
                && !target.hasPermission("plexonquests.bypass.slot-limit")) {
            sender.sendMessage(text.message("quests.unavailable", Map.of("reason", "manual assignment limit")));
            return true;
        }
        assignments.add(target, profile, definition, "", "manual", Instant.now(), null)
                .whenComplete((added, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !Boolean.TRUE.equals(added)) {
                        sender.sendMessage(text.message("general.internal-error", Map.of()));
                        return;
                    }
                    storage.audit(sender.getName(), target.getUniqueId(), null, "ASSIGN", "command", "", definition.id());
                    sender.sendMessage(text.message("admin.assigned", Map.of(
                            "quest", definition.id(), "player", target.getName())));
                }));
        return true;
    }

    private boolean cancel(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.cancel")) {
            return true;
        }
        if (args.length != 3) {
            return usage(sender, "/quests cancel <player> <assignment>");
        }
        Player target = target(sender, args[1]);
        PlayerProfile profile = target == null ? null : profile(target);
        QuestAssignment assignment = profile == null ? null : resolve(profile, args[2], null);
        if (assignment == null || !assignments.cancel(profile, assignment.id())) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        storage.audit(sender.getName(), target.getUniqueId(), assignment.id(), "CANCEL", "command", "", "CANCELLED");
        sender.sendMessage(text.message("admin.cancelled", Map.of("assignment", assignment.id().toString())));
        return true;
    }

    private boolean adjustProgress(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.progress")) {
            return true;
        }
        if (args.length != 6 || (!args[4].equalsIgnoreCase("add") && !args[4].equalsIgnoreCase("set"))) {
            return usage(sender, "/quests progress <player> <assignment> <objective> <add|set> <amount>");
        }
        Player target = target(sender, args[1]);
        PlayerProfile profile = target == null ? null : profile(target);
        QuestAssignment assignment = profile == null ? null : resolve(profile, args[2], null);
        long amount;
        try {
            amount = Long.parseLong(args[5]);
        } catch (NumberFormatException ignored) {
            return usage(sender, "/quests progress <player> <assignment> <objective> <add|set> <amount>");
        }
        if (assignment == null || amount < 0L) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        ProgressResult result = progress.administrativeProgress(
                profile, assignment, args[3], args[4].equalsIgnoreCase("set"), amount);
        if (!result.accepted()) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        storage.audit(sender.getName(), target.getUniqueId(), assignment.id(), "PROGRESS", args[3],
                Long.toString(result.oldValue()), Long.toString(result.newValue()));
        sender.sendMessage(text.message("admin.progress", Map.of(
                "old", Long.toString(result.oldValue()), "new", Long.toString(result.newValue()))));
        if (result.questCompleted()) {
            Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(
                    target, assignment.id(), assignment.definition().id()));
        }
        return true;
    }

    private boolean complete(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.complete")) {
            return true;
        }
        if (args.length != 3) {
            return usage(sender, "/quests complete <player> <assignment>");
        }
        Player target = target(sender, args[1]);
        PlayerProfile profile = target == null ? null : profile(target);
        QuestAssignment assignment = profile == null ? null : resolve(profile, args[2], AssignmentState.ACTIVE);
        if (assignment == null || !assignments.forceComplete(profile, assignment.id())) {
            sender.sendMessage(text.message("quests.not-found", Map.of()));
            return true;
        }
        storage.audit(sender.getName(), target.getUniqueId(), assignment.id(), "COMPLETE", "command", "ACTIVE", "COMPLETED");
        Bukkit.getPluginManager().callEvent(new QuestCompleteEvent(target, assignment.id(), assignment.definition().id()));
        sender.sendMessage(text.message("admin.completed", Map.of()));
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.reset")) {
            return true;
        }
        if (args.length != 3) {
            return usage(sender, "/quests reset <player> <daily|weekly|milestone|manual|all>");
        }
        Player target = target(sender, args[1]);
        PlayerProfile profile = target == null ? null : profile(target);
        QuestScope scope = args[2].equalsIgnoreCase("all") ? null : scope(args[2]);
        if (profile == null || (scope == null && !args[2].equalsIgnoreCase("all"))) {
            return usage(sender, "/quests reset <player> <daily|weekly|milestone|manual|all>");
        }
        List<QuestAssignment> selected = scope == null ? profile.assignments() : profile.assignments(scope);
        selected.forEach(assignment -> assignments.cancel(profile, assignment.id()));
        storage.audit(sender.getName(), target.getUniqueId(), null, "RESET", args[2], "", Integer.toString(selected.size()));
        sender.sendMessage(text.message("admin.reset", Map.of("scope", args[2], "player", target.getName())));
        return true;
    }

    private boolean rotate(CommandSender sender, String[] args) {
        if (!allowed(sender, "plexonquests.admin.rotate")) {
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            return usage(sender, "/quests rotate <daily|weekly> [player|all]");
        }
        QuestScope scope = scope(args[1]);
        if (scope == null || !scope.rotating()) {
            return usage(sender, "/quests rotate <daily|weekly> [player|all]");
        }
        List<Player> targets;
        if (args.length == 2 && sender instanceof Player player) {
            targets = List.of(player);
        } else if (args.length == 3 && args[2].equalsIgnoreCase("all")) {
            targets = List.copyOf(Bukkit.getOnlinePlayers());
        } else if (args.length == 3) {
            Player target = target(sender, args[2]);
            if (target == null) {
                return true;
            }
            targets = List.of(target);
        } else {
            return usage(sender, "/quests rotate <daily|weekly> [player|all]");
        }
        for (Player target : targets) {
            profiles.profile(target).ifPresent(profile -> rotations.forceRotate(target, profile, scope));
        }
        storage.audit(sender.getName(), null, null, "ROTATE", scope.name(), "", Integer.toString(targets.size()));
        sender.sendMessage(text.message("admin.rotated", Map.of("scope", scope.name())));
        return true;
    }

    private boolean save(CommandSender sender) {
        if (!allowed(sender, "plexonquests.admin.save")) {
            return true;
        }
        storage.flushDirty().thenCompose(ignored -> storage.checkpoint()).whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(text.message(
                        failure == null ? "general.save-success" : "general.internal-error", Map.of()))));
        return true;
    }

    private boolean diagnostics(CommandSender sender) {
        if (!allowed(sender, "plexonquests.admin.diagnostics")) {
            return true;
        }
        StorageDiagnostics state = storage.diagnostics();
        long assignmentCount = profiles.onlineProfiles().stream().mapToLong(profile -> profile.assignments().stream()
                .filter(assignment -> !assignment.state().terminal()).count()).sum();
        long objectiveCount = profiles.onlineProfiles().stream().flatMap(profile -> profile.assignments().stream())
                .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)
                .mapToLong(assignment -> assignment.objectives().size()).sum();
        sender.sendMessage(text.rawMessage("admin.diagnostics-header", Map.of()));
        List<String> lines = List.of(
                "<gray>Profiles <white>" + profiles.onlineCount(),
                "<gray>Active assignments/objectives <white>" + assignmentCount + "<dark_gray>/</dark_gray><white>" + objectiveCount,
                "<gray>Dirty assignments <white>" + state.dirtyAssignments(),
                "<gray>Writer queue <white>" + state.queueDepth() + "<dark_gray>/</dark_gray><white>" + state.queueCapacity(),
                "<gray>Last flush <white>" + state.lastFlushResult() + " <dark_gray>(" + state.lastFlushDuration().toMillis() + "ms)",
                "<gray>Rejected writes <white>" + state.rejectedOperations(),
                "<gray>Origin chunks/positions <white>" + origins.loadedChunkCount() + "<dark_gray>/</dark_gray><white>" + origins.trackedPositionCount(),
                "<gray>Invalid definitions <white>" + configs.snapshot().registry().errorCount());
        lines.forEach(line -> sender.sendMessage(text.parse(line)));
        integrations.states().values().stream().sorted(Comparator.comparing(stateValue -> stateValue.id()))
                .forEach(integration -> sender.sendMessage(text.parse(
                        "<gray>" + integration.id() + " <white>" + integration.status()
                                + (integration.detectedVersion().isBlank()
                                        ? "" : " <dark_gray>v" + integration.detectedVersion()))));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return filter(ROOTS.stream().filter(root -> visible(sender, root)).toList(), args[0]);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && Set.of("assign", "cancel", "progress", "complete", "reset").contains(root)) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList(), args[1]);
        }
        if (args.length == 3 && root.equals("assign")) {
            return filter(configs.snapshot().registry().quests().values().stream()
                    .filter(quest -> quest.scope() == QuestScope.MANUAL)
                    .map(QuestDefinition::id).sorted().toList(), args[2]);
        }
        if (args.length == 2 && root.equals("rotate")) {
            return filter(List.of("daily", "weekly"), args[1]);
        }
        if (args.length == 3 && root.equals("rotate")) {
            List<String> values = new ArrayList<>();
            values.add("all");
            Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().forEach(values::add);
            return filter(values, args[2]);
        }
        if (args.length == 3 && root.equals("reset")) {
            return filter(List.of("daily", "weekly", "milestone", "manual", "all"), args[2]);
        }
        if (args.length == 5 && root.equals("progress")) {
            return filter(List.of("add", "set"), args[4]);
        }
        return List.of();
    }

    private boolean visible(CommandSender sender, String root) {
        return switch (root) {
            case "admin" -> sender.hasPermission("plexonquests.admin.gui");
            case "reload" -> sender.hasPermission("plexonquests.admin.reload");
            case "validate" -> sender.hasPermission("plexonquests.admin.validate");
            case "assign" -> sender.hasPermission("plexonquests.admin.assign");
            case "cancel" -> sender.hasPermission("plexonquests.admin.cancel");
            case "progress" -> sender.hasPermission("plexonquests.admin.progress");
            case "complete" -> sender.hasPermission("plexonquests.admin.complete");
            case "reset" -> sender.hasPermission("plexonquests.admin.reset");
            case "rotate" -> sender.hasPermission("plexonquests.admin.rotate");
            case "save" -> sender.hasPermission("plexonquests.admin.save");
            case "diagnostics" -> sender.hasPermission("plexonquests.admin.diagnostics");
            default -> true;
        };
    }

    private boolean throttled(Player player) {
        long now = System.nanoTime();
        long cooldown = configs.snapshot().settings().security().commandCooldownMillis() * 1_000_000L;
        Long previous = commandTimes.put(player.getUniqueId(), now);
        return previous != null && now - previous < cooldown;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(text.message("general.player-only", Map.of()));
        return null;
    }

    private Player target(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            sender.sendMessage(text.message("general.player-not-found", Map.of("player", name)));
        }
        return target;
    }

    private PlayerProfile profile(Player player) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            player.sendMessage(text.message("general.not-ready", Map.of()));
        }
        return profile;
    }

    private boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        sender.sendMessage(text.message("general.no-permission", Map.of()));
        return false;
    }

    private boolean usage(CommandSender sender, String usage) {
        sender.sendMessage(text.message("general.invalid-usage", Map.of("usage", usage)));
        return true;
    }

    private static QuestScope scope(String value) {
        try {
            return QuestScope.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String argument(String[] args, int index) {
        return args.length > index ? args[index] : "";
    }

    private static QuestAssignment resolve(PlayerProfile profile, String token, AssignmentState preferred) {
        List<QuestAssignment> visible = profile.visibleAssignments();
        if (token == null || token.isBlank()) {
            QuestAssignment pinned = profile.pinnedAssignment().flatMap(profile::assignment)
                    .filter(assignment -> preferred == null || assignment.state() == preferred).orElse(null);
            if (pinned != null) {
                return pinned;
            }
            return visible.stream().filter(assignment -> preferred == null || assignment.state() == preferred)
                    .findFirst().orElse(null);
        }
        try {
            int index = Integer.parseInt(token) - 1;
            if (index >= 0 && index < visible.size()) {
                QuestAssignment selected = visible.get(index);
                return preferred == null || selected.state() == preferred ? selected : null;
            }
        } catch (NumberFormatException ignored) {
            // Continue with UUID/quest ID resolution.
        }
        String lowered = token.toLowerCase(Locale.ROOT);
        List<QuestAssignment> matches = visible.stream()
                .filter(assignment -> assignment.id().toString().startsWith(lowered)
                        || assignment.definition().id().equals(lowered))
                .filter(assignment -> preferred == null || assignment.state() == preferred)
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        Set<String> unique = new LinkedHashSet<>();
        values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).forEach(unique::add);
        return List.copyOf(unique);
    }
}

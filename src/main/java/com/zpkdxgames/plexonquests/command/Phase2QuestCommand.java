package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.gui.Phase2JournalService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 4.x command compatibility layer around the mature 3.x QuestCommand. */
public final class Phase2QuestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> JOURNAL_ROOTS = List.of(
            "overview", "active", "available", "categories", "tracked", "completed", "statistics", "help");

    private final JavaPlugin plugin;
    private final QuestCommand delegate;
    private final Phase2JournalService journal;
    private final QuestPrerequisiteService prerequisites;
    private final TextService text;
    private final Executor configExecutor;
    private final AdminConfirmationGate confirmations = new AdminConfirmationGate(Duration.ofSeconds(30));

    public Phase2QuestCommand(
            JavaPlugin plugin,
            QuestCommand delegate,
            Phase2JournalService journal,
            QuestPrerequisiteService prerequisites,
            TextService text,
            Executor configExecutor) {
        this.plugin = plugin;
        this.delegate = delegate;
        this.journal = journal;
        this.prerequisites = prerequisites;
        this.text = text;
        this.configExecutor = configExecutor;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("journal") || args[0].equalsIgnoreCase("overview")) {
            if (sender instanceof org.bukkit.entity.Player player && sender.hasPermission("plexonquests.use")) {
                journal.openOverview(player);
                return true;
            }
            return delegate.onCommand(sender, command, label, args);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (sender instanceof org.bukkit.entity.Player player && sender.hasPermission("plexonquests.use")) {
            switch (root) {
                case "active" -> { journal.openActive(player); return true; }
                case "available" -> { journal.openAvailable(player, 0, null); return true; }
                case "categories" -> { journal.openCategories(player); return true; }
                case "tracked" -> { journal.openTracked(player); return true; }
                case "completed" -> { journal.openCompleted(player); return true; }
                case "statistics", "stats" -> { journal.openStatistics(player); return true; }
                case "help" -> { journal.openHelp(player); return true; }
                default -> { }
            }
        }
        if (root.equals("reload") && sender.hasPermission("plexonquests.admin.reload")) {
            validateThenReload(sender, command, label, args);
            return true;
        }
        String destructivePermission = switch (root) {
            case "complete" -> "plexonquests.admin.complete";
            case "reset" -> "plexonquests.admin.reset";
            case "cancel" -> "plexonquests.admin.cancel";
            default -> null;
        };
        if (destructivePermission != null && args.length >= 3 && sender.hasPermission(destructivePermission)) {
            if (confirmations.check(sender, args) == AdminConfirmationGate.Decision.STAGED) {
                sender.sendMessage(text.parse(
                        "<gold><bold>Confirmation required.</bold> <gray>Repeat the exact same command within <white>30 seconds</white> to confirm."
                                + " <dark_gray>The authoritative handler will re-check target state before mutation."));
                return true;
            }
        }
        return delegate.onCommand(sender, command, label, args);
    }

    private void validateThenReload(CommandSender sender, Command command, String label, String[] args) {
        java.util.concurrent.CompletableFuture
                .supplyAsync(prerequisites::validateCandidate, configExecutor)
                .whenComplete((result, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) {
                        sender.sendMessage(text.parse("<red>Prerequisite graph validation failed unexpectedly; reload was not attempted."));
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Phase 2 prerequisite preflight failed", failure);
                        return;
                    }
                    if (!result.valid()) {
                        sender.sendMessage(text.parse("<red><bold>Reload blocked:</bold> <gray>quest prerequisite graph is invalid."));
                        result.errors().stream().limit(12).forEach(error ->
                                sender.sendMessage(text.parse("<red>• <white>" + escape(error))));
                        return;
                    }
                    delegate.onCommand(sender, command, label, args);
                }));
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        List<String> delegated = delegate.onTabComplete(sender, command, alias, args);
        if (args.length != 1) {
            return delegated;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Set<String> merged = new LinkedHashSet<>();
        if (delegated != null) {
            merged.addAll(delegated);
        }
        JOURNAL_ROOTS.stream().filter(value -> value.startsWith(prefix)).forEach(merged::add);
        return new ArrayList<>(merged);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>");
    }
}

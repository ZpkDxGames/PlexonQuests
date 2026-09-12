package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.gui.Phase2JournalService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 4.2 player command surface backed by the join-based quest board. */
public final class Phase2QuestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> JOURNAL_ROOTS = List.of(
            "current", "active", "available", "daily", "weekly", "milestones",
            "completed", "history", "skills", "stats", "help", "reroll", "track", "untrack");

    private final QuestCommand delegate;
    private final Phase2JournalService journal;
    private final ProfileService profiles;
    private final QuestTrackingService tracking;
    private final Phase2Diagnostics diagnostics;
    private final TextService text;
    private final AdminConfirmationGate confirmations = new AdminConfirmationGate(Duration.ofSeconds(30));

    public Phase2QuestCommand(
            QuestCommand delegate,
            Phase2JournalService journal,
            ProfileService profiles,
            QuestTrackingService tracking,
            Phase2Diagnostics diagnostics,
            TextService text) {
        this.delegate = delegate;
        this.journal = journal;
        this.profiles = profiles;
        this.tracking = tracking;
        this.diagnostics = diagnostics;
        this.text = text;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("journal") || args[0].equalsIgnoreCase("overview")) {
            if (sender instanceof Player player && sender.hasPermission("plexonquests.use")) {
                journal.openOverview(player);
                return true;
            }
            return delegate.onCommand(sender, command, label, args);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (sender instanceof Player player && sender.hasPermission("plexonquests.use")) {
            switch (root) {
                case "current", "active", "tracked", "pinned" -> { journal.openActive(player); return true; }
                case "available", "eligible", "categories" -> { journal.openAvailable(player, 0, null); return true; }
                case "daily" -> { journal.openAvailable(player, 0, null, com.zpkdxgames.plexonquests.quest.QuestScope.DAILY); return true; }
                case "weekly" -> { journal.openAvailable(player, 0, null, com.zpkdxgames.plexonquests.quest.QuestScope.WEEKLY); return true; }
                case "milestones", "milestone" -> { journal.openAvailable(player, 0, null, com.zpkdxgames.plexonquests.quest.QuestScope.MILESTONE); return true; }
                case "completed", "history" -> { journal.openCompleted(player); return true; }
                case "skills", "statistics", "stats" -> { journal.openStatistics(player); return true; }
                case "help" -> { journal.openHelp(player); return true; }
                case "reroll" -> {
                    player.sendMessage(text.parse("<gray>Rerolls are no longer needed — choose a quest from <green>Available Quests</green>."));
                    journal.openAvailable(player, 0, null);
                    return true;
                }
                case "track" -> { return track(player, args); }
                case "untrack" -> { return untrack(player); }
                default -> { }
            }
        } else if (root.equals("track") || root.equals("untrack") || root.equals("reroll")) {
            sender.sendMessage(text.parse("<red>You do not have permission to use that quest action."));
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
        boolean handled = delegate.onCommand(sender, command, label, args);
        if (root.equals("diagnostics") && sender.hasPermission("plexonquests.admin.diagnostics")) diagnostics.append(sender);
        return handled;
    }

    private boolean track(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(text.parse("<yellow>Usage: <white>/quests track <assignment|quest>"));
            return true;
        }
        report(player, tracking.track(player, args[1]));
        return true;
    }

    private boolean untrack(Player player) {
        report(player, tracking.untrack(player));
        return true;
    }

    private void report(Player player, QuestTrackingService.Result result) {
        switch (result) {
            case TRACKED -> player.sendMessage(text.parse("<yellow>Quest is now tracked."));
            case UNTRACKED -> player.sendMessage(text.parse("<gray>Tracked quest cleared."));
            case UNCHANGED -> player.sendMessage(text.parse("<gray>Quest tracking is already in that state."));
            case NO_PERMISSION -> player.sendMessage(text.parse("<red>You do not have permission to change quest tracking."));
            case NOT_FOUND -> player.sendMessage(text.parse("<red>No matching active quest was found."));
            case NOT_ACTIVE -> player.sendMessage(text.parse("<red>Only active quests can be tracked."));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("track") && sender instanceof Player player) {
            return filter(player, args[1]);
        }
        List<String> delegated = delegate.onTabComplete(sender, command, alias, args);
        if (args.length != 1) return delegated;
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Set<String> merged = new LinkedHashSet<>();
        if (delegated != null) merged.addAll(delegated);
        merged.remove("pinned");
        merged.remove("eligible");
        merged.remove("statistics");
        JOURNAL_ROOTS.stream().filter(value -> value.startsWith(prefix)).forEach(merged::add);
        return new ArrayList<>(merged);
    }

    private List<String> filter(Player player, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return player.hasPermission("plexonquests.pin")
                ? journal.activeQuestIds(player).stream().filter(id -> id.startsWith(lowered)).toList()
                : List.of();
    }
}

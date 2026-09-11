package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Phase2Diagnostics {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final StorageService storage;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final QuestTrackingService tracking;
    private final TextService text;

    public Phase2Diagnostics(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            StorageService storage,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            QuestTrackingService tracking,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.storage = storage;
        this.prerequisites = prerequisites;
        this.history = history;
        this.tracking = tracking;
        this.text = text;
    }

    public void append(CommandSender sender) {
        var snapshot = configs.snapshot();
        long enabled = snapshot.registry().quests().values().stream().filter(q -> q.enabled()).count();
        long disabled = snapshot.registry().quests().size() - enabled;
        Set<String> objectiveTypes = snapshot.registry().quests().values().stream()
                .flatMap(q -> q.objectives().values().stream())
                .map(o -> o.type().name())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<String> rewardTypes = snapshot.registry().quests().values().stream()
                .flatMap(q -> q.rewards().entries().stream())
                .map(r -> r.type().name())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        CompletionHistoryCache.Diagnostics cache = history.diagnostics();
        var persistence = storage.diagnostics();
        List<String> lines = List.of(
                "<gray>Candidate version <white>" + plugin.getPluginMeta().getVersion(),
                "<gray>Loaded quests enabled/disabled <white>" + snapshot.registry().quests().size()
                        + " <dark_gray>(</dark_gray><green>" + enabled + "</green><dark_gray>/</dark_gray><red>" + disabled + "</red><dark_gray>)",
                "<gray>Prerequisite graph <green>VALID <dark_gray>• <white>" + prerequisites.snapshot().questCount() + " quest(s)",
                "<gray>Completion cache entries loaded/loading/truncated <white>" + cache.entries()
                        + "<dark_gray>/</dark_gray><white>" + cache.loaded() + "<dark_gray>/</dark_gray><white>" + cache.loading()
                        + "<dark_gray>/</dark_gray><white>" + cache.truncated(),
                "<gray>Completion cache failed/stale results <white>" + cache.failedLoads()
                        + "<dark_gray>/</dark_gray><white>" + cache.staleResults(),
                "<gray>Tracked online quests <white>" + tracking.trackedOnlineCount()
                        + " <dark_gray>across " + profiles.onlineCount() + " loaded profile(s)",
                "<gray>Persistence <white>" + (persistence.open() ? "OPEN" : "CLOSED")
                        + " <dark_gray>schema v" + StorageService.CURRENT_SCHEMA_VERSION,
                "<gray>Pending persistence queue/dirty <white>" + persistence.queueDepth()
                        + "<dark_gray>/</dark_gray><white>" + persistence.dirtyAssignments(),
                "<gray>Configured objective types <white>" + compact(objectiveTypes),
                "<gray>Configured reward types <white>" + compact(rewardTypes),
                "<gray>PlaceholderAPI <white>" + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "ENABLED" : "ABSENT"),
                "<gray>Registry validation errors <white>" + snapshot.registry().errorCount(),
                "<gray>Last rejected configuration errors <white>" + configs.lastActivationErrors().size());
        lines.forEach(line -> sender.sendMessage(text.parse(line)));
        configs.lastActivationErrors().stream().limit(5)
                .forEach(error -> sender.sendMessage(text.parse("<red>Rejected config: <white>" + safe(error))));
    }

    private static String compact(Set<String> values) {
        if (values.isEmpty()) return "none";
        return values.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>");
    }
}

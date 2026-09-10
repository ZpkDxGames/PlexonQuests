package com.zpkdxgames.plexonquests.api;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.service.JournalStateResolver;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonQuestsJournalAPIImpl implements PlexonQuestsJournalAPI {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final JournalStateResolver states;
    private final QuestPrerequisiteService prerequisites;

    public PlexonQuestsJournalAPIImpl(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            JournalStateResolver states,
            QuestPrerequisiteService prerequisites) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.states = states;
        this.prerequisites = prerequisites;
    }

    @Override
    public CompletableFuture<List<JournalQuestView>> journal(UUID playerId) {
        return onPrimary(playerId, (player, profile) -> configs.snapshot().registry().quests().values().stream()
                .sorted(Comparator.comparing(QuestDefinition::id))
                .map(definition -> view(player, profile, definition))
                .toList(), List.of());
    }

    @Override
    public CompletableFuture<Optional<JournalQuestView>> quest(UUID playerId, String questId) {
        return onPrimary(playerId, (player, profile) -> Optional.ofNullable(
                        configs.snapshot().registry().quests().get(questId == null ? "" : questId.toLowerCase()))
                .map(definition -> view(player, profile, definition)), Optional.empty());
    }

    @Override
    public CompletableFuture<Optional<JournalQuestView>> tracked(UUID playerId) {
        return onPrimary(playerId, (player, profile) -> profile.pinnedAssignment()
                .flatMap(profile::assignment)
                .map(QuestAssignment::definition)
                .map(definition -> view(player, profile, definition)), Optional.empty());
    }

    @Override
    public Map<String, Set<String>> prerequisiteGraph() {
        return prerequisites.snapshot().prerequisites();
    }

    private JournalQuestView view(Player player, PlayerProfile profile, QuestDefinition definition) {
        Optional<QuestAssignment> assignment = states.latest(profile, definition.id());
        long current = assignment.map(value -> value.displayProgress().current()).orElse(0L);
        long required = assignment.map(value -> value.displayProgress().required())
                .orElseGet(() -> definition.objectives().values().stream().mapToLong(value -> value.amount()).sum());
        boolean tracked = assignment.flatMap(value -> profile.pinnedAssignment()
                        .filter(value.id()::equals))
                .isPresent();
        return new JournalQuestView(
                definition.id(),
                definition.category(),
                definition.scope().name(),
                states.resolve(player, profile, definition),
                current,
                required,
                tracked,
                prerequisites.prerequisites(definition.id()),
                states.missingPrerequisites(player.getUniqueId(), definition.id()));
    }

    private <T> CompletableFuture<T> onPrimary(
            UUID playerId, Resolver<T> resolver, T offlineValue) {
        if (Bukkit.isPrimaryThread()) {
            Player player = Bukkit.getPlayer(playerId);
            PlayerProfile profile = player == null ? null : profiles.profile(playerId).orElse(null);
            return CompletableFuture.completedFuture(player == null || profile == null
                    ? offlineValue
                    : resolver.resolve(player, profile));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> onPrimary(playerId, resolver, offlineValue)
                .whenComplete((value, failure) -> {
                    if (failure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(failure);
                    }
                }));
        return result;
    }

    @FunctionalInterface
    private interface Resolver<T> {
        T resolve(Player player, PlayerProfile profile);
    }
}

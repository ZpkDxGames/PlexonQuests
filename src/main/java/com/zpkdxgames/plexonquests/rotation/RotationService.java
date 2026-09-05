package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.AssignmentService;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.SlotResolver;
import com.zpkdxgames.plexonquests.util.Hashing;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RotationService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final AssignmentService assignments;
    private final ProgressService progress;
    private final QuestEligibilityService eligibility;
    private final SlotResolver slots = new SlotResolver();
    private final WeightedSelector selector = new WeightedSelector();
    private volatile long serverSeed;
    private volatile CompletableFuture<Long> seedFuture;

    public RotationService(
            JavaPlugin plugin,
            ConfigManager configs,
            StorageService storage,
            AssignmentService assignments,
            ProgressService progress,
            QuestEligibilityService eligibility) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.assignments = assignments;
        this.progress = progress;
        this.eligibility = eligibility;
        this.seedFuture = storage.serverSeed().whenComplete((seed, failure) -> {
            if (failure == null) {
                serverSeed = seed;
            }
        });
    }

    public void ensure(Player player, PlayerProfile profile) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> ensure(player, profile));
            return;
        }
        var snapshot = configs.snapshot();
        UUID pinnedBeforeExpiry = profile.pinnedAssignment().orElse(null);
        List<QuestAssignment> expired = profile.expirePast(
                Instant.now(), snapshot.settings().rotation().claimGrace());
        for (QuestAssignment assignment : expired) {
            storage.archive(assignment, com.zpkdxgames.plexonquests.quest.AssignmentState.EXPIRED)
                    .exceptionally(failure -> {
                        plugin.getLogger().log(Level.SEVERE, "Could not archive expired quest", failure);
                        return null;
                    });
            Bukkit.getPluginManager().callEvent(new QuestExpireEvent(
                    player, assignment.id(), assignment.definition().id()));
        }
        if (!expired.isEmpty()) {
            if (pinnedBeforeExpiry != null && profile.pinnedAssignment().isEmpty()) {
                storage.savePreferences(
                                profile.playerId(), profile.latestName(), profile.preferences(), null)
                        .exceptionally(failure -> {
                            plugin.getLogger().log(Level.WARNING, "Could not persist expired quest pin removal", failure);
                            return null;
                        });
            }
            progress.reindex(profile);
        }
        seedFuture.whenComplete((seed, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null || !player.isOnline()) {
                if (failure != null) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load the quest rotation seed", failure);
                }
                return;
            }
            ensureRotating(player, profile, QuestScope.DAILY);
            ensureRotating(player, profile, QuestScope.WEEKLY);
            ensureMilestones(player, profile);
        }));
    }

    public void forceRotate(Player player, PlayerProfile profile, QuestScope scope) {
        if (!scope.rotating()) {
            throw new IllegalArgumentException("Only daily and weekly assignments can rotate");
        }
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> forceRotate(player, profile, scope));
            return;
        }
        for (QuestAssignment assignment : profile.assignments(scope)) {
            assignments.cancel(profile, assignment.id());
        }
        ensureRotating(player, profile, scope);
    }

    private void ensureRotating(Player player, PlayerProfile profile, QuestScope scope) {
        var snapshot = configs.snapshot();
        PeriodKeyService periods = new PeriodKeyService(snapshot.settings().rotation());
        RotationPeriod period = periods.period(scope, Instant.now());
        PoolDefinition pool = snapshot.registry().pools().values().stream()
                .filter(PoolDefinition::enabled)
                .filter(candidate -> candidate.scope() == scope)
                .filter(candidate -> eligibility.evaluate(player, profile, candidate).eligible())
                .findFirst()
                .orElse(null);
        if (pool == null) {
            return;
        }
        int limit = slots.resolve(
                player, scope, profile.rankCategory(), snapshot.settings(), pool.baseAssignments());
        List<QuestAssignment> current = profile.assignments(scope, period.key());
        int missing = Math.max(0, limit - current.size());
        if (missing == 0) {
            return;
        }
        Instant historyCutoff = Instant.now().minus(pool.recentHistoryExclusion());
        CompletableFuture<Set<String>> recentFuture = storage.recentQuestIds(profile.playerId(), historyCutoff);
        CompletableFuture<Set<String>> periodFuture = storage.questIdsForPeriod(profile.playerId(), period.key());
        recentFuture.thenCombine(periodFuture, SelectionHistory::new).whenComplete((history, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || failure != null) {
                        if (failure != null) {
                            plugin.getLogger().log(Level.WARNING, "Could not evaluate recent quest history", failure);
                        }
                        return;
                    }
                    List<QuestAssignment> liveCurrent = profile.assignments(scope, period.key());
                    int liveMissing = Math.max(0, limit - liveCurrent.size());
                    if (liveMissing == 0) {
                        return;
                    }
                    Set<String> liveCurrentIds = liveCurrent.stream()
                            .map(assignment -> assignment.definition().id())
                            .collect(java.util.stream.Collectors.toSet());
                    Set<String> exclusions = new HashSet<>(history.recentQuestIds());
                    exclusions.addAll(history.periodQuestIds());
                    exclusions.addAll(liveCurrentIds);
                    long seed = Hashing.stableLong(
                            serverSeed + "|" + profile.playerId() + "|" + period.key() + "|" + pool.id());
                    List<QuestDefinition> chosen = selector.select(
                            pool,
                            snapshot.registry().quests(),
                            liveMissing,
                            seed,
                            exclusions,
                            liveCurrent.stream().map(QuestAssignment::definition).toList(),
                            quest -> eligibility.evaluate(player, profile, quest).eligible());
                    if (chosen.size() < liveMissing) {
                        Set<String> fallbackExclusions = new HashSet<>(history.periodQuestIds());
                        fallbackExclusions.addAll(liveCurrentIds);
                        chosen = selector.select(
                                pool,
                                snapshot.registry().quests(),
                                liveMissing,
                                seed,
                                fallbackExclusions,
                                liveCurrent.stream().map(QuestAssignment::definition).toList(),
                                quest -> eligibility.evaluate(player, profile, quest).eligible());
                    }
                    for (QuestDefinition quest : chosen) {
                        assignments.add(
                                        player, profile, quest, pool.id(), period.key(), Instant.now(), period.endsAt())
                                .exceptionally(insertFailure -> {
                                    plugin.getLogger().log(Level.WARNING, "Could not persist assignment " + quest.id(), insertFailure);
                                    return false;
                                });
                    }
                }));
    }

    private void ensureMilestones(Player player, PlayerProfile profile) {
        Set<String> existing = profile.assignments(QuestScope.MILESTONE).stream()
                .map(assignment -> assignment.definition().id())
                .collect(java.util.stream.Collectors.toSet());
        for (QuestDefinition quest : configs.snapshot().registry().quests().values()) {
            if (quest.scope() != QuestScope.MILESTONE || existing.contains(quest.id())) {
                continue;
            }
            if (!eligibility.evaluate(player, profile, quest).eligible()) {
                continue;
            }
            Map<String, Long> initialProgress = new LinkedHashMap<>();
            quest.objectives().forEach((objectiveId, objective) -> {
                if (objective.type() == ObjectiveType.QUESTS_CLAIMED) {
                    initialProgress.put(objectiveId, profile.completedTotal());
                }
            });
            assignments.add(player, profile, quest, "", "milestone", Instant.now(), null, initialProgress)
                    .exceptionally(failure -> {
                        plugin.getLogger().log(Level.FINE, "Milestone assignment already exists or could not load", failure);
                        return false;
                    });
        }
    }

    private record SelectionHistory(Set<String> recentQuestIds, Set<String> periodQuestIds) {}
}

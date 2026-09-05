package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.event.QuestObjectiveCompleteEvent;
import com.zpkdxgames.plexonquests.event.QuestProgressEvent;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.matcher.ObjectiveMatcher;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.ProgressResult;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProgressService {
    private final JavaPlugin plugin;
    private final ProfileService profiles;
    private final StorageService storage;
    private final ConfigManager configs;
    private final Map<UUID, PlayerObjectiveIndex> indexes = new ConcurrentHashMap<>();
    private final Map<UUID, SourceTokens> sourceTokens = new ConcurrentHashMap<>();
    private volatile ProgressObserver observer = (player, assignment, result) -> {};

    public ProgressService(
            JavaPlugin plugin,
            ProfileService profiles,
            StorageService storage,
            ConfigManager configs) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.storage = storage;
        this.configs = configs;
    }

    public void observer(ProgressObserver observer) {
        this.observer = observer;
    }

    public void reindex(PlayerProfile profile) {
        indexes.put(profile.playerId(), PlayerObjectiveIndex.build(profile));
    }

    public void removeIndex(UUID playerId) {
        indexes.remove(playerId);
        sourceTokens.remove(playerId);
    }

    public void contribute(Player player, Contribution contribution) {
        contributeAsync(player, contribution).exceptionally(failure -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Quest contribution failed closed", failure);
            return false;
        });
    }

    public CompletableFuture<Boolean> contributeAsync(Player player, Contribution contribution) {
        if (!Bukkit.isPrimaryThread()) {
            UUID playerId = player.getUniqueId();
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null) {
                    contributeAsync(current, contribution).whenComplete((accepted, failure) -> {
                        if (failure == null) {
                            future.complete(accepted);
                        } else {
                            future.completeExceptionally(failure);
                        }
                    });
                } else {
                    future.complete(false);
                }
            });
            return future;
        }
        PlayerObjectiveIndex index = indexes.get(player.getUniqueId());
        if (index == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (!contribution.sourceToken().isBlank()) {
            String memoryToken = contribution.type() + ":" + contribution.sourceToken();
            if (!sourceTokens.computeIfAbsent(player.getUniqueId(), ignored -> new SourceTokens())
                    .accept(memoryToken, System.nanoTime())) {
                return CompletableFuture.completedFuture(false);
            }
            UUID playerId = player.getUniqueId();
            SourceTokens tokens = sourceTokens.get(player.getUniqueId());
            return storage.reserveContributionToken(
                            playerId, contribution.type().name(), contribution.sourceToken())
                    .thenCompose(reserved -> {
                        if (!reserved) {
                            return CompletableFuture.completedFuture(false);
                        }
                        CompletableFuture<Boolean> applied = new CompletableFuture<>();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player current = Bukkit.getPlayer(playerId);
                            applied.complete(current != null && apply(current, withoutSourceToken(contribution)));
                        });
                        return applied;
                    }).whenComplete((accepted, failure) -> {
                        if (failure != null && tokens != null) {
                            tokens.forget(memoryToken);
                        }
                    });
        }
        return CompletableFuture.completedFuture(apply(player, contribution));
    }

    private boolean apply(Player player, Contribution contribution) {
        PlayerObjectiveIndex index = indexes.get(player.getUniqueId());
        if (index == null) {
            return false;
        }
        var settings = configs.snapshot().settings();
        boolean multiQuest = settings.assignments().multiQuestProgress();
        long globalCooldownMillis = settings.tracking().contributionCooldownMillis();
        boolean[] acceptedAny = {false};
        boolean[] reindex = {false};
        index.forEachCandidate(contribution, handle -> {
            if (acceptedAny[0] && !multiQuest) {
                return;
            }
            QuestAssignment assignment = handle.assignment();
            if (assignment.state() != AssignmentState.ACTIVE) {
                return;
            }
            long accepted = ObjectiveMatcher.acceptedAmount(
                    player, handle.objective(), contribution, settings.tracking());
            if (accepted <= 0L || !index.cooldownAllows(handle, System.nanoTime(), globalCooldownMillis)) {
                return;
            }
            if (QuestProgressEvent.getHandlerList().getRegisteredListeners().length != 0) {
                QuestProgressEvent event = new QuestProgressEvent(
                        player, assignment.id(), assignment.definition().id(), handle.objective().id(), accepted);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled() || event.acceptedDelta() <= 0L) {
                    return;
                }
                accepted = event.acceptedDelta();
            }
            ProgressResult result = assignment.addProgress(handle.objective().id(), accepted, Instant.now());
            if (!result.accepted()) {
                return;
            }
            acceptedAny[0] = true;
            index.recordCooldown(handle, System.nanoTime(), globalCooldownMillis);
            storage.markDirty(assignment);
            observer.onProgress(player, assignment, result);
            if (result.objectiveCompleted()) {
                reindex[0] = true;
                Bukkit.getPluginManager().callEvent(new QuestObjectiveCompleteEvent(
                        player, assignment.id(), assignment.definition().id(), result.objectiveId()));
            }
            if (result.questCompleted()) {
                Bukkit.getPluginManager().callEvent(
                        new QuestCompleteEvent(player, assignment.id(), assignment.definition().id()));
            }
        });
        if (reindex[0]) {
            profiles.profile(player).ifPresent(this::reindex);
        }
        return acceptedAny[0];
    }

    private static Contribution withoutSourceToken(Contribution value) {
        return new Contribution(
                value.type(), value.amount(), value.material(), value.entityType(), value.damageCause(), value.spawnReason(),
                value.world(), value.worldEnvironment(), value.gameMode(), value.originKnown(), value.natural(),
                value.mature(), value.hostile(), value.teleport(), value.unique(), value.movementType(),
                value.advancementKey(), "");
    }

    private static final class SourceTokens {
        private static final int MAXIMUM = 2_048;
        private static final long RETENTION_NANOS = java.time.Duration.ofHours(1).toNanos();
        private final java.util.LinkedHashMap<String, Long> seen = new java.util.LinkedHashMap<>();

        private synchronized boolean accept(String token, long now) {
            Long previous = seen.get(token);
            if (previous != null && now - previous <= RETENTION_NANOS) {
                return false;
            }
            seen.put(token, now);
            while (seen.size() > MAXIMUM) {
                seen.remove(seen.keySet().iterator().next());
            }
            if ((seen.size() & 127) == 0) {
                seen.entrySet().removeIf(entry -> now - entry.getValue() > RETENTION_NANOS);
            }
            return true;
        }

        private synchronized void forget(String token) {
            seen.remove(token);
        }
    }

    public ProgressResult administrativeProgress(
            PlayerProfile profile, QuestAssignment assignment, String objectiveId, boolean set, long amount) {
        ProgressResult result = set
                ? assignment.setProgress(objectiveId, amount, Instant.now())
                : assignment.addProgress(objectiveId, amount, Instant.now());
        if (result.accepted()) {
            storage.markDirty(assignment);
            reindex(profile);
        }
        return result;
    }

    private record Handle(QuestAssignment assignment, ObjectiveDefinition objective) {
        private String key() {
            return assignment.id() + ":" + objective.id();
        }
    }

    private static final class PlayerObjectiveIndex {
        private final EnumMap<ObjectiveType, Bucket> buckets = new EnumMap<>(ObjectiveType.class);
        private final Map<String, Long> cooldowns = new HashMap<>();

        private static PlayerObjectiveIndex build(PlayerProfile profile) {
            PlayerObjectiveIndex index = new PlayerObjectiveIndex();
            for (QuestAssignment assignment : profile.assignments()) {
                if (assignment.state() != AssignmentState.ACTIVE || assignment.rerollReserved()) {
                    continue;
                }
                for (ObjectiveProgress progress : assignment.objectives()) {
                    Handle handle = new Handle(assignment, progress.definition());
                    index.buckets.computeIfAbsent(progress.definition().type(), ignored -> new Bucket()).add(handle);
                }
            }
            return index;
        }

        private void forEachCandidate(Contribution contribution, Consumer<Handle> consumer) {
            Bucket bucket = buckets.get(contribution.type());
            if (bucket != null) {
                bucket.forEach(contribution, consumer);
            }
        }

        private boolean cooldownAllows(Handle handle, long nowNanos, long globalCooldownMillis) {
            long cooldownMillis = Math.max(
                    handle.objective().filters().cooldownMillis(), globalCooldownMillis);
            if (cooldownMillis <= 0L) {
                return true;
            }
            long last = cooldowns.getOrDefault(handle.key(), Long.MIN_VALUE);
            return last == Long.MIN_VALUE
                    || nowNanos - last >= TimeUnit.MILLISECONDS.toNanos(cooldownMillis);
        }

        private void recordCooldown(Handle handle, long nowNanos, long globalCooldownMillis) {
            if (Math.max(handle.objective().filters().cooldownMillis(), globalCooldownMillis) > 0L) {
                cooldowns.put(handle.key(), nowNanos);
            }
        }
    }

    private static final class Bucket {
        private final List<Handle> wildcard = new ArrayList<>();
        private final Map<Material, List<Handle>> materials = new EnumMap<>(Material.class);
        private final Map<EntityType, List<Handle>> entities = new EnumMap<>(EntityType.class);

        private void add(Handle handle) {
            var filters = handle.objective().filters();
            if (!filters.materials().isEmpty()) {
                filters.materials().forEach(material ->
                        materials.computeIfAbsent(material, ignored -> new ArrayList<>()).add(handle));
            } else if (!filters.caughtMaterials().isEmpty()) {
                filters.caughtMaterials().forEach(material ->
                        materials.computeIfAbsent(material, ignored -> new ArrayList<>()).add(handle));
            } else if (!filters.entityTypes().isEmpty()) {
                filters.entityTypes().forEach(entity ->
                        entities.computeIfAbsent(entity, ignored -> new ArrayList<>()).add(handle));
            } else {
                wildcard.add(handle);
            }
        }

        private void forEach(Contribution contribution, Consumer<Handle> consumer) {
            if (contribution.material() != null) {
                List<Handle> matching = materials.get(contribution.material());
                if (matching != null) {
                    matching.forEach(consumer);
                }
            }
            if (contribution.entityType() != null) {
                List<Handle> matching = entities.get(contribution.entityType());
                if (matching != null) {
                    matching.forEach(consumer);
                }
            }
            wildcard.forEach(consumer);
        }
    }
}

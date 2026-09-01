package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.event.QuestAssignEvent;
import com.zpkdxgames.plexonquests.event.QuestAssignedEvent;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AssignmentService {
    private final JavaPlugin plugin;
    private final StorageService storage;
    private final ProgressService progress;

    public AssignmentService(JavaPlugin plugin, StorageService storage, ProgressService progress) {
        this.plugin = plugin;
        this.storage = storage;
        this.progress = progress;
    }

    public CompletableFuture<Boolean> add(
            Player player,
            PlayerProfile profile,
            QuestDefinition definition,
            String poolId,
            String periodKey,
            Instant assignedAt,
            Instant expiresAt) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> add(
                            player, profile, definition, poolId, periodKey, assignedAt, expiresAt)
                    .whenComplete((added, failure) -> {
                        if (failure == null) {
                            result.complete(added);
                        } else {
                            result.completeExceptionally(failure);
                        }
                    }));
            return result;
        }
        QuestAssignment assignment = QuestAssignment.create(
                profile.playerId(), definition, poolId, periodKey, assignedAt, expiresAt);
        QuestAssignEvent event = new QuestAssignEvent(
                player, assignment.id(), definition.id(), assignment.poolId(), assignment.periodKey());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CompletableFuture.completedFuture(false);
        }
        return storage.insertAssignment(assignment, player.getName()).thenCompose(inserted -> {
            if (!inserted) {
                return CompletableFuture.completedFuture(false);
            }
            CompletableFuture<Boolean> installed = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    installed.complete(true);
                    return;
                }
                profile.add(assignment);
                progress.reindex(profile);
                Bukkit.getPluginManager().callEvent(new QuestAssignedEvent(
                        player, assignment.id(), definition.id(), assignment.poolId(), assignment.periodKey()));
                installed.complete(true);
            });
            return installed;
        });
    }

    public boolean cancel(PlayerProfile profile, UUID assignmentId) {
        QuestAssignment assignment = profile.assignment(assignmentId).orElse(null);
        if (assignment == null || !assignment.cancel()) {
            return false;
        }
        storage.markDirty(assignment);
        progress.reindex(profile);
        return true;
    }

    public boolean forceComplete(PlayerProfile profile, UUID assignmentId) {
        QuestAssignment assignment = profile.assignment(assignmentId).orElse(null);
        if (assignment == null || !assignment.forceComplete(Instant.now())) {
            return false;
        }
        storage.markDirty(assignment);
        progress.reindex(profile);
        return true;
    }
}

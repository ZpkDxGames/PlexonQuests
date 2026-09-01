package com.zpkdxgames.plexonquests.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public PlexonQuests service. Obtain it from Bukkit's {@code ServicesManager}.
 *
 * <p>Player-state operations are marshalled to the primary server thread and complete there. The returned
 * records are immutable and safe to retain. Manual assignment currently requires the target player to be online.
 */
public interface PlexonQuestsAPI {
    CompletableFuture<List<AssignmentView>> activeAssignments(UUID playerId);

    CompletableFuture<Optional<AssignmentView>> assignment(UUID playerId, UUID assignmentId);

    Optional<QuestDefinitionView> questDefinition(String questId);

    CompletableFuture<Boolean> assignManual(UUID playerId, String questId);

    CompletableFuture<Void> submitProgress(UUID playerId, ExternalProgressContribution contribution);

    CompletableFuture<Boolean> isComplete(UUID playerId, UUID assignmentId);

    CompletableFuture<Boolean> isClaimable(UUID playerId, UUID assignmentId);

    CompletableFuture<Boolean> pin(UUID playerId, UUID assignmentId);

    CompletableFuture<Boolean> unpin(UUID playerId);

    CompletableFuture<Void> openJournal(UUID playerId, String scope);

    Map<String, IntegrationView> integrationStates();
}

package com.zpkdxgames.plexonquests.quest;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class QuestAssignment {
    private final UUID id;
    private final UUID playerId;
    private final QuestDefinition definition;
    private final String poolId;
    private final String periodKey;
    private final Instant assignedAt;
    private final Instant expiresAt;
    private final LinkedHashMap<String, ObjectiveProgress> objectives;
    private AssignmentState state;
    private Instant completedAt;
    private Instant claimedAt;
    private boolean rerollReserved;

    public QuestAssignment(
            UUID id,
            UUID playerId,
            QuestDefinition definition,
            String poolId,
            String periodKey,
            Instant assignedAt,
            Instant expiresAt,
            AssignmentState state,
            Instant completedAt,
            Instant claimedAt,
            Map<String, Long> progress) {
        this.id = Objects.requireNonNull(id, "id");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.poolId = Objects.requireNonNullElse(poolId, "");
        this.periodKey = Objects.requireNonNullElse(periodKey, "");
        this.assignedAt = Objects.requireNonNull(assignedAt, "assignedAt");
        this.expiresAt = expiresAt;
        this.state = Objects.requireNonNull(state, "state");
        this.completedAt = completedAt;
        this.claimedAt = claimedAt;
        this.objectives = new LinkedHashMap<>();
        definition.objectives().forEach((objectiveId, objective) ->
                objectives.put(objectiveId, new ObjectiveProgress(objective, progress.getOrDefault(objectiveId, 0L))));
    }

    public static QuestAssignment create(
            UUID playerId,
            QuestDefinition definition,
            String poolId,
            String periodKey,
            Instant assignedAt,
            Instant expiresAt) {
        return create(playerId, definition, poolId, periodKey, assignedAt, expiresAt, Map.of());
    }

    public static QuestAssignment create(
            UUID playerId,
            QuestDefinition definition,
            String poolId,
            String periodKey,
            Instant assignedAt,
            Instant expiresAt,
            Map<String, Long> initialProgress) {
        QuestAssignment assignment = new QuestAssignment(
                UUID.randomUUID(), playerId, definition, poolId, periodKey, assignedAt, expiresAt,
                AssignmentState.ACTIVE, null, null, initialProgress);
        if (assignment.isCompletionSatisfied()) {
            assignment.transition(AssignmentState.COMPLETED);
            assignment.completedAt = assignedAt;
        }
        return assignment;
    }

    public synchronized ProgressResult addProgress(String objectiveId, long delta, Instant now) {
        ObjectiveProgress objective = objectives.get(objectiveId);
        if (objective == null
                || state != AssignmentState.ACTIVE
                || rerollReserved
                || delta <= 0L
                || !sequenceAllows(objectiveId)) {
            return ProgressResult.rejected(objectiveId, objective == null ? 0L : objective.current());
        }
        long old = objective.current();
        boolean wasComplete = objective.complete();
        long updated = objective.add(delta);
        if (updated == old) {
            return ProgressResult.rejected(objectiveId, old);
        }
        boolean objectiveCompleted = !wasComplete && objective.complete();
        boolean questCompleted = false;
        if (isCompletionSatisfied()) {
            transition(AssignmentState.COMPLETED);
            completedAt = now;
            questCompleted = true;
        }
        return new ProgressResult(true, objectiveId, old, updated, objectiveCompleted, questCompleted);
    }

    public synchronized ProgressResult setProgress(String objectiveId, long value, Instant now) {
        ObjectiveProgress objective = objectives.get(objectiveId);
        if (objective == null || state != AssignmentState.ACTIVE || rerollReserved || !sequenceAllows(objectiveId)) {
            return ProgressResult.rejected(objectiveId, objective == null ? 0L : objective.current());
        }
        long old = objective.current();
        boolean wasComplete = objective.complete();
        long updated = objective.set(value);
        boolean objectiveCompleted = !wasComplete && objective.complete();
        boolean questCompleted = false;
        if (isCompletionSatisfied()) {
            transition(AssignmentState.COMPLETED);
            completedAt = now;
            questCompleted = true;
        }
        return new ProgressResult(updated != old, objectiveId, old, updated, objectiveCompleted, questCompleted);
    }

    public synchronized boolean forceComplete(Instant now) {
        if (state != AssignmentState.ACTIVE || rerollReserved) {
            return false;
        }
        objectives.values().forEach(objective -> objective.set(objective.required()));
        transition(AssignmentState.COMPLETED);
        completedAt = now;
        return true;
    }

    public synchronized boolean markClaiming() {
        if (state != AssignmentState.COMPLETED || rerollReserved) {
            return false;
        }
        transition(AssignmentState.CLAIMING);
        return true;
    }

    public synchronized boolean rollbackClaim() {
        if (state != AssignmentState.CLAIMING) {
            return false;
        }
        transition(AssignmentState.COMPLETED);
        return true;
    }

    public synchronized boolean markClaimed(Instant now) {
        if (state != AssignmentState.CLAIMING) {
            return false;
        }
        transition(AssignmentState.CLAIMED);
        claimedAt = now;
        return true;
    }

    public synchronized boolean expire() {
        if (rerollReserved || (state != AssignmentState.ACTIVE && state != AssignmentState.COMPLETED)) {
            return false;
        }
        transition(AssignmentState.EXPIRED);
        return true;
    }

    public synchronized boolean cancel() {
        if (rerollReserved || (state != AssignmentState.ACTIVE && state != AssignmentState.COMPLETED)) {
            return false;
        }
        transition(AssignmentState.CANCELLED);
        return true;
    }

    public synchronized boolean reserveReroll() {
        if (state != AssignmentState.ACTIVE || rerollReserved) {
            return false;
        }
        rerollReserved = true;
        return true;
    }

    public synchronized boolean releaseReroll() {
        if (!rerollReserved) {
            return false;
        }
        rerollReserved = false;
        return true;
    }

    public synchronized boolean finishReroll() {
        if (state != AssignmentState.ACTIVE || !rerollReserved) {
            return false;
        }
        rerollReserved = false;
        transition(AssignmentState.CANCELLED);
        return true;
    }

    public synchronized boolean rerollReserved() {
        return rerollReserved;
    }

    private boolean sequenceAllows(String objectiveId) {
        if (definition.completionMode() != CompletionMode.SEQUENCE) {
            return true;
        }
        for (Map.Entry<String, ObjectiveProgress> entry : objectives.entrySet()) {
            if (!entry.getValue().complete()) {
                return entry.getKey().equals(objectiveId);
            }
        }
        return false;
    }

    private boolean isCompletionSatisfied() {
        return switch (definition.completionMode()) {
            case ALL, SEQUENCE -> objectives.values().stream().allMatch(ObjectiveProgress::complete);
            case ANY -> objectives.values().stream().anyMatch(ObjectiveProgress::complete);
        };
    }

    private void transition(AssignmentState target) {
        AssignmentStateMachine.require(state, target);
        state = target;
    }

    public UUID id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public QuestDefinition definition() {
        return definition;
    }

    public String poolId() {
        return poolId;
    }

    public String periodKey() {
        return periodKey;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Optional<Instant> expiresAt() {
        return Optional.ofNullable(expiresAt);
    }

    public synchronized AssignmentState state() {
        return state;
    }

    public synchronized Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public synchronized Optional<Instant> claimedAt() {
        return Optional.ofNullable(claimedAt);
    }

    public synchronized List<ObjectiveProgress> objectives() {
        return List.copyOf(objectives.values());
    }

    public synchronized Optional<ObjectiveProgress> objective(String id) {
        return Optional.ofNullable(objectives.get(id));
    }

    public synchronized Map<String, Long> progressSnapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        objectives.forEach((id, progress) -> snapshot.put(id, progress.current()));
        return Map.copyOf(snapshot);
    }

    public synchronized long currentTotal() {
        return objectives.values().stream().mapToLong(ObjectiveProgress::current).sum();
    }

    public synchronized long requiredTotal() {
        return objectives.values().stream().mapToLong(ObjectiveProgress::required).sum();
    }

    public synchronized double percentage() {
        return displayProgress().percentage();
    }

    public synchronized ProgressSummary displayProgress() {
        if (definition.completionMode() == CompletionMode.ANY) {
            ObjectiveProgress best = null;
            double bestRatio = -1D;
            for (ObjectiveProgress progress : objectives.values()) {
                double ratio = Math.min(1D, (double) progress.current() / progress.required());
                if (ratio > bestRatio) {
                    best = progress;
                    bestRatio = ratio;
                }
            }
            return best == null
                    ? new ProgressSummary(0L, 0L, 0D)
                    : new ProgressSummary(best.current(), best.required(), bestRatio * 100D);
        }
        long required = requiredTotal();
        long current = currentTotal();
        double percentage = required == 0L ? 0D : Math.min(100D, (double) current * 100D / required);
        return new ProgressSummary(current, required, percentage);
    }

    public record ProgressSummary(long current, long required, double percentage) {}
}

package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerProfile {
    private final UUID playerId;
    private final LinkedHashMap<UUID, QuestAssignment> assignments = new LinkedHashMap<>();
    private String latestName;
    private FeedbackPreferences preferences;
    private UUID pinnedAssignment;
    private long completedTotal;
    private String rankCategory = "default";

    public PlayerProfile(
            UUID playerId,
            String latestName,
            FeedbackPreferences preferences,
            UUID pinnedAssignment,
            long completedTotal,
            List<QuestAssignment> assignments) {
        this.playerId = playerId;
        this.latestName = latestName;
        this.preferences = preferences;
        this.pinnedAssignment = pinnedAssignment;
        this.completedTotal = completedTotal;
        assignments.forEach(assignment -> this.assignments.put(assignment.id(), assignment));
        if (pinnedAssignment != null && !this.assignments.containsKey(pinnedAssignment)) {
            this.pinnedAssignment = null;
        }
    }

    public UUID playerId() {
        return playerId;
    }

    public synchronized String latestName() {
        return latestName;
    }

    public synchronized void latestName(String latestName) {
        this.latestName = latestName;
    }

    public synchronized FeedbackPreferences preferences() {
        return preferences;
    }

    public synchronized void preferences(FeedbackPreferences preferences) {
        this.preferences = preferences;
    }

    public synchronized Optional<UUID> pinnedAssignment() {
        return Optional.ofNullable(pinnedAssignment);
    }

    public synchronized void pinnedAssignment(UUID assignmentId) {
        this.pinnedAssignment = assignmentId;
    }

    public synchronized long completedTotal() {
        return completedTotal;
    }

    public synchronized void incrementCompletedTotal() {
        completedTotal++;
    }

    public synchronized String rankCategory() {
        return rankCategory;
    }

    public synchronized void rankCategory(String rankCategory) {
        this.rankCategory = rankCategory;
    }

    public synchronized void add(QuestAssignment assignment) {
        assignments.put(assignment.id(), assignment);
    }

    public synchronized QuestAssignment remove(UUID assignmentId) {
        if (assignmentId.equals(pinnedAssignment)) {
            pinnedAssignment = null;
        }
        return assignments.remove(assignmentId);
    }

    public synchronized Optional<QuestAssignment> assignment(UUID assignmentId) {
        return Optional.ofNullable(assignments.get(assignmentId));
    }

    public synchronized List<QuestAssignment> assignments() {
        return List.copyOf(assignments.values());
    }

    public synchronized List<QuestAssignment> visibleAssignments() {
        return assignments.values().stream()
                .filter(assignment -> !assignment.state().terminal())
                .toList();
    }

    public synchronized List<QuestAssignment> assignments(QuestScope scope) {
        return assignments.values().stream()
                .filter(assignment -> assignment.definition().scope() == scope)
                .toList();
    }

    public synchronized List<QuestAssignment> assignments(QuestScope scope, String periodKey) {
        return assignments.values().stream()
                .filter(assignment -> assignment.definition().scope() == scope)
                .filter(assignment -> assignment.periodKey().equals(periodKey))
                .filter(assignment -> assignment.state().occupiesRotationSlot())
                .toList();
    }

    public synchronized long claimableCount() {
        return assignments.values().stream().filter(assignment -> assignment.state() == AssignmentState.COMPLETED).count();
    }

    public synchronized List<QuestAssignment> expirePast(java.time.Instant now, java.time.Duration grace) {
        List<QuestAssignment> expired = new ArrayList<>();
        for (QuestAssignment assignment : assignments.values()) {
            assignment.expiresAt().ifPresent(expiry -> {
                java.time.Instant deadline = assignment.state() == AssignmentState.COMPLETED ? expiry.plus(grace) : expiry;
                if (now.isAfter(deadline) && assignment.expire()) {
                    expired.add(assignment);
                    if (assignment.id().equals(pinnedAssignment)) {
                        pinnedAssignment = null;
                    }
                }
            });
        }
        return List.copyOf(expired);
    }
}

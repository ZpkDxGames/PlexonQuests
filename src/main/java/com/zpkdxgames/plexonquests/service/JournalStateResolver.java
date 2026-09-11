package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Side-effect-free derivation of the 4.x player-facing quest state. */
public final class JournalStateResolver {
    private final QuestEligibilityService eligibility;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache completionHistory;

    public JournalStateResolver(
            QuestEligibilityService eligibility,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache completionHistory) {
        this.eligibility = Objects.requireNonNull(eligibility, "eligibility");
        this.prerequisites = Objects.requireNonNull(prerequisites, "prerequisites");
        this.completionHistory = Objects.requireNonNull(completionHistory, "completionHistory");
    }

    public JournalState resolve(Player player, PlayerProfile profile, QuestDefinition definition) {
        if (!definition.enabled()) {
            return JournalState.DISABLED;
        }
        Optional<QuestAssignment> current = latest(profile, definition.id());
        if (current.isPresent()) {
            QuestAssignment assignment = current.get();
            AssignmentState state = assignment.state();
            if (state == AssignmentState.ACTIVE) {
                return profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()
                        ? JournalState.TRACKED
                        : JournalState.ACTIVE;
            }
            if (state == AssignmentState.COMPLETED || state == AssignmentState.CLAIMING) {
                return JournalState.COMPLETABLE;
            }
            if (state == AssignmentState.CLAIMED) {
                return definition.scope().rotating() ? JournalState.COOLDOWN : JournalState.COMPLETED;
            }
            if (state == AssignmentState.EXPIRED) {
                return JournalState.EXPIRED;
            }
        }

        UUID playerId = player.getUniqueId();
        if (!definition.scope().rotating()) {
            if (!completionHistory.loaded(playerId)) {
                return JournalState.LOCKED;
            }
            if (completionHistory.completed(playerId, definition.id())) {
                return JournalState.COMPLETED;
            }
        }

        Set<String> required = prerequisites.prerequisites(definition.id());
        if (!required.isEmpty()) {
            if (!completionHistory.loaded(playerId)) {
                return JournalState.LOCKED;
            }
            for (String requiredQuest : required) {
                if (!completionHistory.completed(playerId, requiredQuest)) {
                    return JournalState.LOCKED;
                }
            }
        }

        if (!eligibility.evaluate(player, profile, definition).eligible()) {
            return JournalState.LOCKED;
        }
        return JournalState.AVAILABLE;
    }

    public Set<String> missingPrerequisites(UUID playerId, String questId) {
        return prerequisites.missing(id -> completionHistory.completed(playerId, id), questId);
    }

    public Optional<QuestAssignment> latest(PlayerProfile profile, String questId) {
        return profile.assignments().stream()
                .filter(assignment -> assignment.definition().id().equals(questId))
                .max(Comparator.comparing(QuestAssignment::assignedAt));
    }
}

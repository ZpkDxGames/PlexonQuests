package com.zpkdxgames.plexonquests.presentation;

import com.zpkdxgames.plexonquests.event.QuestAssignedEvent;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.event.QuestObjectiveCompleteEvent;
import com.zpkdxgames.plexonquests.quest.ClaimMode;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.service.ProfileService;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class QuestNotificationListener implements Listener {
    private final ProfileService profiles;
    private final RewardService rewards;
    private final TextService text;

    public QuestNotificationListener(ProfileService profiles, RewardService rewards, TextService text) {
        this.profiles = profiles;
        this.rewards = rewards;
        this.text = text;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAssigned(QuestAssignedEvent event) {
        assignment(event.getPlayer().getUniqueId(), event.assignmentId()).ifPresent(assignment ->
                event.getPlayer().sendMessage(text.message("quests.assigned", Map.of(
                        "scope", assignment.definition().scope().name().toLowerCase(java.util.Locale.ROOT),
                        "quest_name", plainName(assignment)))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onObjective(QuestObjectiveCompleteEvent event) {
        assignment(event.getPlayer().getUniqueId(), event.assignmentId()).flatMap(assignment ->
                        assignment.objective(event.objectiveId()))
                .ifPresent(objective -> event.getPlayer().sendMessage(text.message(
                        "quests.objective-complete", Map.of("objective", objective.definition().display()))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onComplete(QuestCompleteEvent event) {
        assignment(event.getPlayer().getUniqueId(), event.assignmentId()).ifPresent(assignment -> {
            if (assignment.definition().claimMode() == ClaimMode.AUTOMATIC) {
                rewards.claim(event.getPlayer(), assignment);
            } else {
                event.getPlayer().sendMessage(text.message(
                        "quests.complete", Map.of("quest_name", plainName(assignment))));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpire(QuestExpireEvent event) {
        assignment(event.getPlayer().getUniqueId(), event.assignmentId()).ifPresent(assignment ->
                event.getPlayer().sendMessage(text.message(
                        "quests.expired", Map.of("quest_name", plainName(assignment)))));
    }

    private java.util.Optional<QuestAssignment> assignment(java.util.UUID playerId, java.util.UUID assignmentId) {
        return profiles.profile(playerId).flatMap(profile -> profile.assignment(assignmentId));
    }

    private String plainName(QuestAssignment assignment) {
        return text.plain(text.parse(assignment.definition().display().name()));
    }
}

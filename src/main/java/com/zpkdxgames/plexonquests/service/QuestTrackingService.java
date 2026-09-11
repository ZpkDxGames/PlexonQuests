package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.event.QuestTrackEvent;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Single authority for player-facing quest tracking and permission-independent lifecycle cleanup. */
public final class QuestTrackingService implements Listener {
    private final ConfigManager configs;
    private final ProfileService profiles;

    public QuestTrackingService(ConfigManager configs, ProfileService profiles) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public Result toggle(Player player, UUID assignmentId) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (assignment == null) {
            return Result.NOT_FOUND;
        }
        if (profile.pinnedAssignment().filter(assignmentId::equals).isPresent()) {
            return clear(player, profile, assignment) ? Result.UNTRACKED : Result.UNCHANGED;
        }
        if (assignment.state() != AssignmentState.ACTIVE) {
            return Result.NOT_ACTIVE;
        }
        set(player, profile, assignment);
        return Result.TRACKED;
    }

    public Result track(Player player, String token) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        QuestAssignment assignment = resolveActive(profile, token);
        if (assignment == null) {
            return Result.NOT_FOUND;
        }
        if (profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()) {
            return Result.UNCHANGED;
        }
        set(player, profile, assignment);
        return Result.TRACKED;
    }

    public Result untrack(Player player) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        UUID trackedId = profile.pinnedAssignment().orElse(null);
        if (trackedId == null) {
            return Result.UNCHANGED;
        }
        QuestAssignment assignment = profile.assignment(trackedId).orElse(null);
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return Result.UNTRACKED;
    }

    public boolean reconcile(Player player, PlayerProfile profile) {
        requirePrimary();
        UUID trackedId = profile.pinnedAssignment().orElse(null);
        if (trackedId == null) {
            return false;
        }
        QuestAssignment assignment = profile.assignment(trackedId).orElse(null);
        boolean valid = assignment != null && assignment.state() == AssignmentState.ACTIVE;
        if (valid) {
            var currentDefinition = configs.snapshot().registry().quests().get(assignment.definition().id());
            valid = currentDefinition != null && currentDefinition.enabled();
        }
        if (valid) {
            return false;
        }
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return true;
    }

    public int trackedOnlineCount() {
        int count = 0;
        for (PlayerProfile profile : profiles.onlineProfiles()) {
            UUID trackedId = profile.pinnedAssignment().orElse(null);
            QuestAssignment assignment = trackedId == null ? null : profile.assignment(trackedId).orElse(null);
            if (assignment != null && assignment.state() == AssignmentState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onComplete(QuestCompleteEvent event) {
        profiles.profile(event.getPlayer()).ifPresent(profile -> clearIfMatches(event.getPlayer(), profile, event.assignmentId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpire(QuestExpireEvent event) {
        profiles.profile(event.getPlayer()).ifPresent(profile -> clearIfMatches(event.getPlayer(), profile, event.assignmentId()));
    }

    private boolean clearIfMatches(Player player, PlayerProfile profile, UUID assignmentId) {
        if (profile.pinnedAssignment().filter(assignmentId::equals).isEmpty()) {
            return false;
        }
        QuestAssignment assignment = profile.assignment(assignmentId).orElse(null);
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return true;
    }

    private boolean clear(Player player, PlayerProfile profile, QuestAssignment assignment) {
        if (profile.pinnedAssignment().filter(assignment.id()::equals).isEmpty()) {
            return false;
        }
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), false));
        return true;
    }

    private void set(Player player, PlayerProfile profile, QuestAssignment assignment) {
        UUID previousId = profile.pinnedAssignment().orElse(null);
        if (previousId != null && !previousId.equals(assignment.id())) {
            profile.assignment(previousId).ifPresent(previous -> Bukkit.getPluginManager().callEvent(
                    new QuestTrackEvent(player, previous.id(), previous.definition().id(), false)));
        }
        profile.pinnedAssignment(assignment.id());
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), true));
    }

    private static QuestAssignment resolveActive(PlayerProfile profile, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String lowered = token.trim().toLowerCase(Locale.ROOT);
        List<QuestAssignment> matches = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)
                .filter(assignment -> assignment.id().toString().startsWith(lowered)
                        || assignment.definition().id().equals(lowered))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static void requirePrimary() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Quest tracking mutations must run on the primary thread");
        }
    }

    public enum Result { TRACKED, UNTRACKED, UNCHANGED, NO_PERMISSION, NOT_FOUND, NOT_ACTIVE }
}

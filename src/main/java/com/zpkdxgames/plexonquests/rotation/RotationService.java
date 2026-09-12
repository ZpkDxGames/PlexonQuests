package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.service.AssignmentService;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Rotation lifecycle authority. Since 4.2 this service expires stale assignments only;
 * normal Daily, Weekly and Milestone quests are never auto-created here.
 */
public final class RotationService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final AssignmentService assignments;
    private final ProgressService progress;

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
        // Eligibility remains in the constructor for source/binary wiring compatibility; catalog owns offer eligibility in 4.2.
    }

    public void ensure(Player player, PlayerProfile profile) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> ensure(player, profile));
            return;
        }
        var snapshot = configs.snapshot();
        UUID pinnedBeforeExpiry = profile.pinnedAssignment().orElse(null);
        List<QuestAssignment> expired = profile.expirePast(Instant.now(), snapshot.settings().rotation().claimGrace());
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
                storage.savePreferences(profile.playerId(), profile.latestName(), profile.preferences(), null)
                        .exceptionally(failure -> {
                            plugin.getLogger().log(Level.WARNING, "Could not persist expired quest pin removal", failure);
                            return null;
                        });
            }
            progress.reindex(profile);
        }
    }

    /**
     * Administrative compatibility action. It cancels current assignments in the selected period/scope,
     * but deliberately does not auto-fill replacements; players choose new offers from the catalog.
     */
    public void forceRotate(Player player, PlayerProfile profile, QuestScope scope) {
        if (!scope.rotating()) throw new IllegalArgumentException("Only daily and weekly assignments can rotate");
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> forceRotate(player, profile, scope));
            return;
        }
        for (QuestAssignment assignment : profile.assignments(scope)) {
            if (assignment.state() == com.zpkdxgames.plexonquests.quest.AssignmentState.ACTIVE
                    || assignment.state() == com.zpkdxgames.plexonquests.quest.AssignmentState.COMPLETED) {
                assignments.cancel(profile, assignment.id());
            }
        }
        progress.reindex(profile);
    }
}

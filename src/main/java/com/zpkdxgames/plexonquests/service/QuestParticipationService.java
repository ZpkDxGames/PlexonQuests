package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.rotation.PeriodKeyService;
import com.zpkdxgames.plexonquests.rotation.QuestOffer;
import com.zpkdxgames.plexonquests.rotation.RotationPeriod;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Authoritative normal-player join/abandon boundary for PlexonQuests 4.2. */
public final class QuestParticipationService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final ProfileService profiles;
    private final AssignmentService assignments;
    private final ProgressService progress;
    private final QuestEligibilityService eligibility;
    private final QuestTrackingService tracking;
    private final SlotResolver budgets = new SlotResolver();
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public QuestParticipationService(
            JavaPlugin plugin,
            ConfigManager configs,
            StorageService storage,
            ProfileService profiles,
            AssignmentService assignments,
            ProgressService progress,
            QuestEligibilityService eligibility,
            QuestTrackingService tracking) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.profiles = profiles;
        this.assignments = assignments;
        this.progress = progress;
        this.eligibility = eligibility;
        this.tracking = tracking;
    }

    public CompletableFuture<JoinResult> join(Player player, QuestOffer offer) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<JoinResult> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> join(player, offer)
                    .whenComplete((value, failure) -> complete(result, value, failure)));
            return result;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        JoinResult validation = validate(player, profile, offer, null);
        if (validation != JoinResult.JOINED) return CompletableFuture.completedFuture(validation);
        if (!inFlight.add(player.getUniqueId())) return CompletableFuture.completedFuture(JoinResult.IN_FLIGHT);

        CompletableFuture<Set<String>> periodHistory = storage.questIdsForPeriod(player.getUniqueId(), offer.periodKey());
        CompletableFuture<JoinResult> result = new CompletableFuture<>();
        periodHistory.whenComplete((usedIds, historyFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (historyFailure != null) {
                inFlight.remove(player.getUniqueId());
                result.complete(JoinResult.STORAGE_ERROR);
                return;
            }
            PlayerProfile liveProfile = profiles.profile(player).orElse(null);
            JoinResult liveValidation = validate(player, liveProfile, offer, usedIds);
            if (liveValidation != JoinResult.JOINED) {
                inFlight.remove(player.getUniqueId());
                result.complete(liveValidation);
                return;
            }
            QuestDefinition definition = configs.snapshot().registry().quests().get(offer.definition().id());
            assignments.add(player, liveProfile, definition, offer.poolId(), offer.periodKey(), Instant.now(), offer.expiresAt())
                    .whenComplete((added, insertFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (insertFailure != null) {
                                result.complete(JoinResult.STORAGE_ERROR);
                            } else if (!Boolean.TRUE.equals(added)) {
                                result.complete(JoinResult.DUPLICATE);
                            } else {
                                progress.reindex(liveProfile);
                                if (player.isOnline() && player.hasPermission("plexonquests.pin")) {
                                    tracking.track(player, definition.id());
                                }
                                result.complete(JoinResult.JOINED);
                            }
                        } finally {
                            inFlight.remove(player.getUniqueId());
                        }
                    }));
        }));
        return result;
    }

    public LeaveResult abandon(Player player, UUID assignmentId) {
        requirePrimary();
        if (!configs.snapshot().settings().participation().allowAbandon()) return LeaveResult.DISABLED;
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) return LeaveResult.PROFILE_NOT_READY;
        QuestAssignment assignment = profile.assignment(assignmentId).orElse(null);
        if (assignment == null) return LeaveResult.NOT_FOUND;
        if (assignment.state() != AssignmentState.ACTIVE) return LeaveResult.NOT_ACTIVE;
        if (!assignments.cancel(profile, assignmentId)) return LeaveResult.NOT_ACTIVE;
        tracking.reconcile(player, profile);
        progress.reindex(profile);
        return LeaveResult.ABANDONED;
    }

    public int activeCount(PlayerProfile profile) {
        return profile.activeAssignments().size();
    }

    public boolean legacyOverflow(PlayerProfile profile) {
        return activeCount(profile) > configs.snapshot().settings().participation().maximumActiveQuests();
    }

    public boolean inFlight(UUID playerId) {
        return inFlight.contains(playerId);
    }

    /** JOINED is the validation-success sentinel and the final success result. */
    private JoinResult validate(Player player, PlayerProfile profile, QuestOffer offer, Set<String> persistedPeriodIds) {
        if (profile == null || profiles.profile(player).orElse(null) != profile) return JoinResult.PROFILE_NOT_READY;
        if (!player.isOnline()) return JoinResult.PLAYER_OFFLINE;
        QuestDefinition definition = configs.snapshot().registry().quests().get(offer.definition().id());
        if (definition == null || !definition.enabled()) return JoinResult.UNAVAILABLE;
        if (definition.scope() != offer.scope()) return JoinResult.STALE_OFFER;

        String expectedPeriod = "milestone";
        RotationPeriod period = null;
        if (definition.scope().rotating()) {
            period = new PeriodKeyService(configs.snapshot().settings().rotation())
                    .period(definition.scope(), Instant.now());
            expectedPeriod = period.key();
            if (!expectedPeriod.equals(offer.periodKey()) || !Instant.now().isBefore(period.endsAt())) {
                return JoinResult.STALE_OFFER;
            }
        } else if (definition.scope() != com.zpkdxgames.plexonquests.quest.QuestScope.MILESTONE) {
            return JoinResult.UNAVAILABLE;
        }
        if (!expectedPeriod.equals(offer.periodKey())) return JoinResult.STALE_OFFER;

        QuestEligibilityService.EligibilityResult questEligibility = eligibility.evaluate(player, profile, definition);
        if (!questEligibility.eligible()) return JoinResult.INELIGIBLE;
        PoolDefinition pool = null;
        if (definition.scope().rotating()) {
            pool = configs.snapshot().registry().pools().get(offer.poolId());
            if (pool == null || pool.scope() != definition.scope() || !pool.enabled()) return JoinResult.STALE_OFFER;
            if (!eligibility.evaluate(player, profile, pool).eligible()) return JoinResult.INELIGIBLE;
        }

        boolean bypassActive = player.hasPermission("plexonquests.bypass.active-limit");
        int maximumActive = configs.snapshot().settings().participation().maximumActiveQuests();
        if (!bypassActive && profile.activeAssignments().size() >= maximumActive) return JoinResult.ACTIVE_LIMIT;

        boolean localDuplicate = profile.periodAssignments(definition.scope(), expectedPeriod).stream()
                .anyMatch(assignment -> assignment.definition().id().equals(definition.id()));
        if (localDuplicate || persistedPeriodIds != null && persistedPeriodIds.contains(definition.id())) {
            return JoinResult.DUPLICATE;
        }

        if (definition.scope().rotating() && pool != null) {
            int budget = budgets.resolveParticipationBudget(
                    player, definition.scope(), profile.rankCategory(), configs.snapshot().settings(), pool.baseAssignments());
            int localUsage = profile.periodParticipationCount(definition.scope(), expectedPeriod);
            int persistedUsage = persistedPeriodIds == null ? localUsage : persistedPeriodIds.size();
            if (Math.max(localUsage, persistedUsage) >= budget) return JoinResult.PERIOD_LIMIT;
        }
        return JoinResult.JOINED;
    }

    private static void requirePrimary() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Quest participation mutations must run on the primary thread");
        }
    }

    private static <T> void complete(CompletableFuture<T> target, T value, Throwable failure) {
        if (failure == null) target.complete(value); else target.completeExceptionally(failure);
    }

    public enum JoinResult {
        JOINED,
        PROFILE_NOT_READY,
        PLAYER_OFFLINE,
        UNAVAILABLE,
        STALE_OFFER,
        INELIGIBLE,
        ACTIVE_LIMIT,
        PERIOD_LIMIT,
        DUPLICATE,
        IN_FLIGHT,
        STORAGE_ERROR
    }

    public enum LeaveResult { ABANDONED, DISABLED, PROFILE_NOT_READY, NOT_FOUND, NOT_ACTIVE }
}

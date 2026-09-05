package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestRerollEvent;
import com.zpkdxgames.plexonquests.integration.EconomyBridge;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.util.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class RerollService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final ProfileService profiles;
    private final ProgressService progress;
    private final QuestEligibilityService eligibility;
    private final TextService text;
    private final EconomyBridge economy = new EconomyBridge();
    private final WeightedSelector selector = new WeightedSelector();
    private final Map<UUID, PendingReroll> pending = new ConcurrentHashMap<>();
    private final Map<String, Integer> usedCounts = new ConcurrentHashMap<>();
    private final Set<UUID> preparing = ConcurrentHashMap.newKeySet();

    public RerollService(
            JavaPlugin plugin,
            ConfigManager configs,
            StorageService storage,
            ProfileService profiles,
            ProgressService progress,
            QuestEligibilityService eligibility,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.profiles = profiles;
        this.progress = progress;
        this.eligibility = eligibility;
        this.text = text;
    }

    public void prepare(Player player, QuestAssignment assignment, Consumer<PendingReroll> ready) {
        if (!configs.snapshot().settings().rerolls().enabled()) {
            player.sendMessage(text.message("rerolls.disabled", Map.of()));
            return;
        }
        if (!player.hasPermission("plexonquests.reroll")
                || assignment.state() != AssignmentState.ACTIVE
                || assignment.rerollReserved()
                || expired(assignment, Instant.now())
                || !assignment.definition().scope().rotating()) {
            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
            return;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        PendingReroll existing = pending.get(playerId);
        if (existing != null) {
            if (Instant.now().isBefore(existing.expiresAt())) {
                player.sendMessage(text.message("rerolls.preparing", Map.of()));
                return;
            }
            pending.remove(playerId, existing);
        }
        if (!preparing.add(playerId)) {
            player.sendMessage(text.message("rerolls.preparing", Map.of()));
            return;
        }

        storage.countRerolls(profile.playerId(), assignment.periodKey()).whenComplete((used, countFailure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    boolean awaitingHistory = false;
                    try {
                        if (countFailure != null) {
                            plugin.getLogger().log(Level.WARNING, "Could not count quest rerolls", countFailure);
                            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
                            return;
                        }
                        if (!player.isOnline()
                                || assignment.state() != AssignmentState.ACTIVE
                                || assignment.rerollReserved()
                                || expired(assignment, Instant.now())
                                || profile.assignment(assignment.id()).isEmpty()) {
                            return;
                        }
                        usedCounts.put(countKey(profile.playerId(), assignment.periodKey()), used);
                        if (used >= configs.snapshot().settings().rerolls().maximumPerPeriod()) {
                            player.sendMessage(text.message("rerolls.limit", Map.of()));
                            return;
                        }
                        double cost = cost(player, assignment.definition().scope(), used);
                        if (Double.isNaN(cost)) {
                            player.sendMessage(text.message("rerolls.limit", Map.of()));
                            return;
                        }
                        PoolDefinition pool = pool(assignment);
                        if (pool == null || !eligibility.evaluate(player, profile, pool).eligible()) {
                            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
                            return;
                        }
                        Instant cutoff = Instant.now().minus(pool.recentHistoryExclusion());
                        CompletableFuture<Set<String>> recentFuture = storage.recentQuestIds(profile.playerId(), cutoff);
                        CompletableFuture<Set<String>> periodFuture = storage.questIdsForPeriod(
                                profile.playerId(), assignment.periodKey());
                        recentFuture.thenCombine(periodFuture, SelectionHistory::new).whenComplete((history, historyFailure) ->
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    try {
                                        if (historyFailure != null) {
                                            plugin.getLogger().log(
                                                    Level.WARNING, "Could not load recent quests for reroll", historyFailure);
                                            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
                                            return;
                                        }
                                        if (!player.isOnline()
                                                || assignment.state() != AssignmentState.ACTIVE
                                                || assignment.rerollReserved()
                                                || expired(assignment, Instant.now())
                                                || profile.assignment(assignment.id()).isEmpty()) {
                                            return;
                                        }
                                        Set<String> excluded = new HashSet<>(history.recentQuestIds());
                                        excluded.addAll(history.periodQuestIds());
                                        excluded.add(assignment.definition().id());
                                        List<QuestDefinition> existingDefinitions = profile
                                                .assignments(assignment.definition().scope(), assignment.periodKey())
                                                .stream()
                                                .filter(current -> !current.id().equals(assignment.id()))
                                                .map(QuestAssignment::definition)
                                                .toList();
                                        Set<String> occupiedIds = existingDefinitions.stream()
                                                .map(QuestDefinition::id)
                                                .collect(java.util.stream.Collectors.toSet());
                                        excluded.addAll(occupiedIds);
                                        long seed = Hashing.stableLong(assignment.id() + "|reroll|" + used);
                                        List<QuestDefinition> replacements = selector.select(
                                                pool,
                                                configs.snapshot().registry().quests(),
                                                1,
                                                seed,
                                                excluded,
                                                existingDefinitions,
                                                quest -> eligibility.evaluate(player, profile, quest).eligible());
                                        if (replacements.isEmpty()) {
                                            Set<String> fallbackExclusions = new HashSet<>(history.periodQuestIds());
                                            fallbackExclusions.addAll(occupiedIds);
                                            fallbackExclusions.add(assignment.definition().id());
                                            replacements = selector.select(
                                                    pool,
                                                    configs.snapshot().registry().quests(),
                                                    1,
                                                    seed,
                                                    fallbackExclusions,
                                                    existingDefinitions,
                                                    quest -> eligibility.evaluate(player, profile, quest).eligible());
                                        }
                                        if (replacements.isEmpty()) {
                                            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
                                            return;
                                        }
                                        if (cost > 0D && (!economy.available() || economy.balance(player) < cost)) {
                                            player.sendMessage(text.message(
                                                    "rerolls.insufficient-funds",
                                                    Map.of("cost", String.format(java.util.Locale.US, "%.2f", cost))));
                                            return;
                                        }
                                        QuestAssignment replacement = QuestAssignment.create(
                                                profile.playerId(),
                                                replacements.getFirst(),
                                                assignment.poolId(),
                                                assignment.periodKey(),
                                                Instant.now(),
                                                assignment.expiresAt().orElse(null));
                                        Instant confirmationExpiry = Instant.now().plusSeconds(60);
                                        if (assignment.expiresAt().isPresent()
                                                && assignment.expiresAt().orElseThrow().isBefore(confirmationExpiry)) {
                                            confirmationExpiry = assignment.expiresAt().orElseThrow();
                                        }
                                        PendingReroll reservation = new PendingReroll(
                                                UUID.randomUUID().toString(),
                                                assignment,
                                                replacement,
                                                cost,
                                                confirmationExpiry);
                                        pending.put(playerId, reservation);
                                        ready.accept(reservation);
                                    } finally {
                                        preparing.remove(playerId);
                                    }
                                }));
                        awaitingHistory = true;
                    } finally {
                        if (!awaitingHistory) {
                            preparing.remove(playerId);
                        }
                    }
                }));
    }

    public void confirm(Player player) {
        PendingReroll reservation = pending.remove(player.getUniqueId());
        PlayerProfile profile = profiles.profile(player).orElse(null);
        Instant now = Instant.now();
        if (reservation == null
                || profile == null
                || !now.isBefore(reservation.expiresAt())
                || expired(reservation.previous(), now)
                || reservation.previous().state() != AssignmentState.ACTIVE
                || profile.assignment(reservation.previous().id()).orElse(null) != reservation.previous()
                || !reservation.previous().reserveReroll()) {
            player.sendMessage(text.message("rerolls.unavailable", Map.of()));
            return;
        }
        progress.reindex(profile);
        storage.persistReroll(
                        reservation.transactionId(),
                        reservation.previous(),
                        reservation.replacement(),
                        reservation.cost(),
                        "replacement reserved")
                .whenComplete((persisted, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null || !Boolean.TRUE.equals(persisted)) {
                        releaseReservation(profile, reservation);
                        Player current = Bukkit.getPlayer(profile.playerId());
                        if (current != null) {
                            current.sendMessage(text.message("rerolls.unavailable", Map.of()));
                        }
                        return;
                    }
                    Player current = Bukkit.getPlayer(profile.playerId());
                    if (current == null
                            || profiles.profile(profile.playerId()).orElse(null) != profile
                            || expired(reservation.previous(), Instant.now())) {
                        rollbackPersisted(
                                profile, reservation, "player unavailable or rotation ended", null);
                        return;
                    }
                    EconomyBridge.Result payment = reservation.cost() <= 0D
                            ? new EconomyBridge.Result(true, 0D, "free")
                            : economy.withdraw(current, reservation.cost());
                    if (!payment.success()) {
                        rollbackPersisted(
                                profile,
                                reservation,
                                "payment failed: " + payment.detail(),
                                "rerolls.payment-failed");
                        return;
                    }
                    storage.finishReroll(reservation.transactionId(), "SUCCESS", "activated")
                            .whenComplete((ignored, finishFailure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                                if (finishFailure != null) {
                                    compensate(current, profile, reservation, payment);
                                    return;
                                }
                                if (!reservation.previous().finishReroll()) {
                                    plugin.getLogger().severe(
                                            "Reroll completed durably but local reservation was lost: "
                                                    + reservation.transactionId());
                                }
                                PlayerProfile liveProfile = profiles.profile(profile.playerId()).orElse(null);
                                if (liveProfile == profile) {
                                    profile.remove(reservation.previous().id());
                                    profile.add(reservation.replacement());
                                    progress.reindex(profile);
                                }
                                usedCounts.merge(
                                        countKey(profile.playerId(), reservation.previous().periodKey()), 1, Integer::sum);
                                Player online = Bukkit.getPlayer(profile.playerId());
                                if (online != null && liveProfile == profile) {
                                    online.sendMessage(text.message("rerolls.success", Map.of(
                                            "old_quest", plainName(reservation.previous()),
                                            "new_quest", plainName(reservation.replacement()))));
                                    Bukkit.getPluginManager().callEvent(new QuestRerollEvent(
                                            online,
                                            reservation.previous().id(),
                                            reservation.replacement().id(),
                                            reservation.previous().definition().id(),
                                            reservation.replacement().definition().id(),
                                            reservation.transactionId()));
                                }
                            }));
                }));
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
        preparing.remove(player.getUniqueId());
    }

    public PendingReroll pending(Player player) {
        return pending.get(player.getUniqueId());
    }

    public void warm(Player player) {
        PeriodKeyService periods = new PeriodKeyService(configs.snapshot().settings().rotation());
        for (QuestScope scope : List.of(QuestScope.DAILY, QuestScope.WEEKLY)) {
            String periodKey = periods.period(scope, Instant.now()).key();
            storage.countRerolls(player.getUniqueId(), periodKey).thenAccept(used ->
                    usedCounts.put(countKey(player.getUniqueId(), periodKey), used));
        }
    }

    public int freeRemaining(Player player, QuestScope scope) {
        String periodKey = new PeriodKeyService(configs.snapshot().settings().rotation())
                .period(scope, Instant.now()).key();
        int free = scope == QuestScope.DAILY
                ? configs.snapshot().settings().rerolls().freeDaily()
                : configs.snapshot().settings().rerolls().freeWeekly();
        return Math.max(0, free - usedCounts.getOrDefault(countKey(player.getUniqueId(), periodKey), 0));
    }

    private static String countKey(UUID playerId, String periodKey) {
        return playerId + ":" + periodKey;
    }

    private void compensate(
            Player player,
            PlayerProfile profile,
            PendingReroll reservation,
            EconomyBridge.Result payment) {
        if (payment.amount() > 0D) {
            EconomyBridge.Result refund = economy.deposit(player, payment.amount());
            if (!refund.success()) {
                plugin.getLogger().severe("Reroll compensation failed for " + reservation.transactionId());
            }
        }
        rollbackPersisted(profile, reservation, "activation commit failed", "rerolls.payment-failed");
    }

    private void rollbackPersisted(
            PlayerProfile profile,
            PendingReroll reservation,
            String detail,
            String messagePath) {
        storage.rollbackReroll(
                        reservation.transactionId(),
                        reservation.previous(),
                        reservation.replacement(),
                        AssignmentState.ACTIVE,
                        detail)
                .whenComplete((ignored, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(profile.playerId());
                    if (failure == null) {
                        releaseReservation(profile, reservation);
                        if (current != null && messagePath != null) {
                            current.sendMessage(text.message(messagePath, Map.of()));
                        }
                    } else {
                        plugin.getLogger().log(
                                Level.SEVERE,
                                "Could not roll back reroll " + reservation.transactionId(),
                                failure);
                        if (current != null) {
                            current.sendMessage(text.message("general.internal-error", Map.of()));
                        }
                    }
                }));
    }

    private void releaseReservation(PlayerProfile profile, PendingReroll reservation) {
        if (reservation.previous().releaseReroll()
                && profiles.profile(profile.playerId()).orElse(null) == profile) {
            progress.reindex(profile);
        }
    }

    private static boolean expired(QuestAssignment assignment, Instant now) {
        return assignment.expiresAt().map(expiry -> !now.isBefore(expiry)).orElse(false);
    }

    private double cost(Player player, QuestScope scope, int used) {
        int free = scope == QuestScope.DAILY
                ? configs.snapshot().settings().rerolls().freeDaily()
                : configs.snapshot().settings().rerolls().freeWeekly();
        if (used < free || player.hasPermission("plexonquests.bypass.reroll-cost")) {
            return 0D;
        }
        if (!configs.snapshot().settings().rerolls().paidEnabled()) {
            return Double.NaN;
        }
        return configs.snapshot().settings().rerolls().paidCost();
    }

    private PoolDefinition pool(QuestAssignment assignment) {
        if (!assignment.poolId().isBlank()) {
            PoolDefinition exact = configs.snapshot().registry().pools().get(assignment.poolId());
            if (exact != null) {
                return exact;
            }
        }
        return configs.snapshot().registry().pools().values().stream()
                .filter(candidate -> candidate.scope() == assignment.definition().scope())
                .findFirst()
                .orElse(null);
    }

    private String plainName(QuestAssignment assignment) {
        return text.plain(text.parse(assignment.definition().display().name()));
    }

    public record PendingReroll(
            String transactionId,
            QuestAssignment previous,
            QuestAssignment replacement,
            double cost,
            Instant expiresAt) {}

    private record SelectionHistory(Set<String> recentQuestIds, Set<String> periodQuestIds) {}
}

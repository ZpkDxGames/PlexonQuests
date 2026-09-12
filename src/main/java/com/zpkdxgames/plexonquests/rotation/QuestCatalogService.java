package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.PoolDefinition;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.SlotResolver;
import com.zpkdxgames.plexonquests.util.Hashing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Side-effect-free catalog builder for join-based quest participation. */
public final class QuestCatalogService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final QuestEligibilityService eligibility;
    private final SlotResolver budgets = new SlotResolver();
    private final WeightedSelector selector = new WeightedSelector();
    private final CompletableFuture<Long> seedFuture;

    public QuestCatalogService(
            JavaPlugin plugin,
            ConfigManager configs,
            StorageService storage,
            QuestEligibilityService eligibility) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.eligibility = eligibility;
        this.seedFuture = storage.serverSeed();
    }

    public CompletableFuture<List<QuestOffer>> offers(Player player, PlayerProfile profile, QuestScope scope) {
        if (!Bukkit.isPrimaryThread()) {
            CompletableFuture<List<QuestOffer>> result = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> offers(player, profile, scope)
                    .whenComplete((offers, failure) -> complete(result, offers, failure)));
            return result;
        }
        if (scope == QuestScope.MANUAL) return CompletableFuture.completedFuture(List.of());
        if (scope == QuestScope.MILESTONE) return CompletableFuture.completedFuture(milestoneOffers(player, profile));

        var snapshot = configs.snapshot();
        PeriodKeyService periods = new PeriodKeyService(snapshot.settings().rotation());
        RotationPeriod period = periods.period(scope, Instant.now());
        PoolDefinition pool = snapshot.registry().pools().values().stream()
                .filter(PoolDefinition::enabled)
                .filter(candidate -> candidate.scope() == scope)
                .findFirst()
                .orElse(null);
        if (pool == null) return CompletableFuture.completedFuture(List.of());

        int offerCount = scope == QuestScope.DAILY
                ? snapshot.settings().catalog().dailyOffers()
                : snapshot.settings().catalog().weeklyOffers();
        List<QuestAssignment> periodAssignments = profile.periodAssignments(scope, period.key());
        List<QuestDefinition> existing = periodAssignments.stream()
                .map(QuestAssignment::definition)
                .distinct()
                .limit(offerCount)
                .toList();

        Instant historyCutoff = Instant.now().minus(pool.recentHistoryExclusion());
        CompletableFuture<Set<String>> recent = storage.recentQuestIds(profile.playerId(), historyCutoff);
        CompletableFuture<Set<String>> used = storage.questIdsForPeriod(profile.playerId(), period.key());
        CompletableFuture<SelectionHistory> history = recent.thenCombine(used, SelectionHistory::new);

        return seedFuture.thenCombine(history, (seed, selectionHistory) -> {
            Set<String> exclusions = new HashSet<>(selectionHistory.recentQuestIds());
            exclusions.addAll(selectionHistory.periodQuestIds());
            existing.forEach(definition -> exclusions.remove(definition.id()));
            int remaining = Math.max(0, offerCount - existing.size());
            long stableSeed = Hashing.stableLong(
                    seed + "|" + profile.playerId() + "|" + period.key() + "|" + pool.id());
            List<QuestDefinition> selected = selector.select(
                    pool,
                    snapshot.registry().quests(),
                    remaining,
                    stableSeed,
                    exclusions,
                    existing,
                    definition -> true);
            Map<String, QuestDefinition> combined = new LinkedHashMap<>();
            existing.forEach(definition -> combined.put(definition.id(), definition));
            selected.forEach(definition -> combined.put(definition.id(), definition));
            return List.copyOf(combined.values());
        }).thenCompose(definitions -> onPrimary(() -> classify(player, profile, pool, period, definitions)))
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.WARNING, "Could not build quest catalog", failure);
                    return List.of();
                });
    }

    private List<QuestOffer> milestoneOffers(Player player, PlayerProfile profile) {
        List<QuestOffer> offers = new ArrayList<>();
        for (QuestDefinition definition : configs.snapshot().registry().quests().values()) {
            if (definition.scope() != QuestScope.MILESTONE || !definition.enabled()) continue;
            offers.add(classifyOne(player, profile, null,
                    new RotationPeriod("milestone", Instant.EPOCH, Instant.MAX), definition));
        }
        return List.copyOf(offers);
    }

    private List<QuestOffer> classify(
            Player player,
            PlayerProfile profile,
            PoolDefinition pool,
            RotationPeriod period,
            List<QuestDefinition> definitions) {
        List<QuestOffer> offers = new ArrayList<>(definitions.size());
        for (QuestDefinition definition : definitions) {
            offers.add(classifyOne(player, profile, pool, period, definition));
        }
        return List.copyOf(offers);
    }

    private QuestOffer classifyOne(
            Player player,
            PlayerProfile profile,
            PoolDefinition pool,
            RotationPeriod period,
            QuestDefinition definition) {
        QuestAssignment existing = profile.periodAssignments(definition.scope(), period.key()).stream()
                .filter(assignment -> assignment.definition().id().equals(definition.id()))
                .findFirst().orElse(null);
        if (existing != null) {
            return switch (existing.state()) {
                case ACTIVE -> offer(definition, pool, period, QuestOffer.State.ACTIVE, "");
                case COMPLETED, CLAIMING -> offer(definition, pool, period, QuestOffer.State.READY_TO_CLAIM, "");
                case CLAIMED -> offer(definition, pool, period, QuestOffer.State.COMPLETED, "");
                case EXPIRED -> offer(definition, pool, period, QuestOffer.State.EXPIRED, "This offer expired");
                case CANCELLED -> offer(definition, pool, period, QuestOffer.State.LOCKED,
                        "Already participated in this quest during this period");
            };
        }

        QuestEligibilityService.EligibilityResult questEligibility = eligibility.evaluate(player, profile, definition);
        if (!questEligibility.eligible()) {
            return offer(definition, pool, period, QuestOffer.State.LOCKED, questEligibility.reason());
        }
        if (pool != null) {
            QuestEligibilityService.EligibilityResult poolEligibility = eligibility.evaluate(player, profile, pool);
            if (!poolEligibility.eligible()) {
                return offer(definition, pool, period, QuestOffer.State.LOCKED, poolEligibility.reason());
            }
        }

        int maximumActive = configs.snapshot().settings().participation().maximumActiveQuests();
        if (!player.hasPermission("plexonquests.bypass.active-limit")
                && profile.activeAssignments().size() >= maximumActive) {
            return offer(definition, pool, period, QuestOffer.State.ACTIVE_SLOT_OCCUPIED,
                    "Finish or abandon the current quest first");
        }
        if (definition.scope().rotating() && pool != null) {
            int budget = budgets.resolveParticipationBudget(
                    player, definition.scope(), profile.rankCategory(), configs.snapshot().settings(), pool.baseAssignments());
            if (profile.periodParticipationCount(definition.scope(), period.key()) >= budget) {
                return offer(definition, pool, period, QuestOffer.State.PERIOD_LIMIT_REACHED,
                        "Participation budget reached for this period");
            }
        }
        return offer(definition, pool, period, QuestOffer.State.AVAILABLE, "");
    }

    private static QuestOffer offer(
            QuestDefinition definition, PoolDefinition pool, RotationPeriod period, QuestOffer.State state, String reason) {
        return new QuestOffer(definition, definition.scope(), pool == null ? "" : pool.id(), period.key(),
                period.endsAt().equals(Instant.MAX) ? null : period.endsAt(), state, reason);
    }

    private <T> CompletableFuture<T> onPrimary(java.util.concurrent.Callable<T> supplier) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { result.complete(supplier.call()); }
            catch (Exception exception) { result.completeExceptionally(exception); }
        });
        return result;
    }

    private static <T> void complete(CompletableFuture<T> target, T value, Throwable failure) {
        if (failure == null) target.complete(value); else target.completeExceptionally(failure);
    }

    private record SelectionHistory(Set<String> recentQuestIds, Set<String> periodQuestIds) {}
}

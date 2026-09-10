package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JournalStateResolverTest {
    private final UUID playerId = UUID.randomUUID();
    private QuestEligibilityService eligibility;
    private QuestPrerequisiteService prerequisites;
    private CompletionHistoryCache history;
    private Player player;
    private JournalStateResolver resolver;

    @BeforeEach
    void setup() {
        eligibility = mock(QuestEligibilityService.class);
        prerequisites = mock(QuestPrerequisiteService.class);
        history = mock(CompletionHistoryCache.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(eligibility.evaluate(org.mockito.ArgumentMatchers.eq(player), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(QuestDefinition.class)))
                .thenReturn(QuestEligibilityService.EligibilityResult.allowed());
        when(prerequisites.prerequisites(org.mockito.ArgumentMatchers.anyString())).thenReturn(Set.of());
        when(history.loaded(playerId)).thenReturn(true);
        resolver = new JournalStateResolver(eligibility, prerequisites, history);
    }

    @Test
    void resolvesAvailableLockedDisabledAndHistoricalCompletedPrecedence() {
        QuestDefinition available = TestFixtures.quest("available", "general", QuestScope.DAILY, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2);
        PlayerProfile empty = profile(List.of());
        assertEquals(JournalState.AVAILABLE, resolver.resolve(player, empty, available));

        when(prerequisites.prerequisites("available")).thenReturn(Set.of("base"));
        when(history.completed(playerId, "base")).thenReturn(false);
        assertEquals(JournalState.LOCKED, resolver.resolve(player, empty, available));

        QuestDefinition disabled = copyEnabled(available, false);
        assertEquals(JournalState.DISABLED, resolver.resolve(player, empty, disabled));

        QuestDefinition milestone = TestFixtures.quest("milestone", "general", QuestScope.MILESTONE, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2);
        when(history.completed(playerId, "milestone")).thenReturn(true);
        when(eligibility.evaluate(org.mockito.ArgumentMatchers.eq(player), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(milestone)))
                .thenReturn(QuestEligibilityService.EligibilityResult.denied("new gate"));
        assertEquals(JournalState.COMPLETED, resolver.resolve(player, empty, milestone));
    }

    @Test
    void resolvesActiveTrackedCompletableCooldownAndExpiredWithStablePrecedence() {
        QuestDefinition quest = TestFixtures.quest("daily", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 1);
        QuestAssignment active = QuestAssignment.create(playerId, quest, "daily", "p", Instant.now(), Instant.now().plusSeconds(60));
        PlayerProfile profile = profile(List.of(active));
        assertEquals(JournalState.ACTIVE, resolver.resolve(player, profile, quest));

        profile.pinnedAssignment(active.id());
        assertEquals(JournalState.TRACKED, resolver.resolve(player, profile, quest));

        active.addProgress("objective_1", 1, Instant.now());
        assertEquals(AssignmentState.COMPLETED, active.state());
        assertEquals(JournalState.COMPLETABLE, resolver.resolve(player, profile, quest));

        active.markClaiming();
        active.markClaimed(Instant.now());
        assertEquals(JournalState.COOLDOWN, resolver.resolve(player, profile, quest));

        QuestAssignment expired = QuestAssignment.create(playerId, TestFixtures.quest("expired", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), Instant.now());
        expired.expire();
        PlayerProfile expiredProfile = profile(List.of(expired));
        assertEquals(JournalState.EXPIRED, resolver.resolve(player, expiredProfile, expired.definition()));
    }

    @Test
    void coldHistoryFailsClosedForNonRotatingCompletionKnowledge() {
        QuestDefinition milestone = TestFixtures.quest("cold", "general", QuestScope.MILESTONE, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 1);
        when(history.loaded(playerId)).thenReturn(false);
        assertEquals(JournalState.LOCKED, resolver.resolve(player, profile(List.of()), milestone));
    }

    private PlayerProfile profile(List<QuestAssignment> assignments) {
        return new PlayerProfile(playerId, "Tester", FeedbackPreferences.defaults(), null, 0, assignments);
    }

    private static QuestDefinition copyEnabled(QuestDefinition q, boolean enabled) {
        return new QuestDefinition(q.id(), q.revision(), enabled, q.scope(), q.category(), q.rarity(), q.weight(), q.eligibility(),
                q.display(), q.completionMode(), q.claimMode(), q.objectives(), q.rewards(), q.completeEffect(), q.claimEffect(), q.fingerprint(), q.source());
    }
}

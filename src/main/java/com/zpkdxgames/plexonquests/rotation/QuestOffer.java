package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.Objects;

/** Immutable, side-effect-free player-facing quest catalog entry. */
public record QuestOffer(
        QuestDefinition definition,
        QuestScope scope,
        String poolId,
        String periodKey,
        Instant expiresAt,
        State state,
        String lockedReason) {

    public QuestOffer {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(scope, "scope");
        poolId = Objects.requireNonNullElse(poolId, "");
        periodKey = Objects.requireNonNullElse(periodKey, "");
        state = Objects.requireNonNull(state, "state");
        lockedReason = Objects.requireNonNullElse(lockedReason, "");
    }

    public boolean joinable() {
        return state == State.AVAILABLE;
    }

    public enum State {
        AVAILABLE,
        ACTIVE,
        READY_TO_CLAIM,
        COMPLETED,
        LOCKED,
        PERIOD_LIMIT_REACHED,
        ACTIVE_SLOT_OCCUPIED,
        EXPIRED
    }
}

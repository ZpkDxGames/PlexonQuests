package com.zpkdxgames.plexonquests.quest;

/**
 * Player-facing state derived from a quest definition plus the player's durable assignment/history state.
 * This enum is intentionally not persisted in the assignments table.
 */
public enum JournalState {
    LOCKED,
    AVAILABLE,
    ACTIVE,
    TRACKED,
    COMPLETABLE,
    COMPLETED,
    COOLDOWN,
    EXPIRED,
    DISABLED
}

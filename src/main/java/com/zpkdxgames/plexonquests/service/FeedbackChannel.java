package com.zpkdxgames.plexonquests.service;

public enum FeedbackChannel {
    ACTIONBAR(1 << 0),
    BOSSBAR(1 << 1),
    PROGRESS_SOUNDS(1 << 2),
    COMPLETION_SOUNDS(1 << 3),
    PARTICLES(1 << 4),
    TITLES(1 << 5),
    JOIN_REMINDERS(1 << 6),
    REDUCED_MOTION(1 << 7);

    private final int bit;

    FeedbackChannel(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}


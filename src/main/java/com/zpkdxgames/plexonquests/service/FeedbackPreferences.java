package com.zpkdxgames.plexonquests.service;

public record FeedbackPreferences(int mask) {
    private static final int DEFAULT_MASK = FeedbackChannel.ACTIONBAR.bit()
            | FeedbackChannel.BOSSBAR.bit()
            | FeedbackChannel.PROGRESS_SOUNDS.bit()
            | FeedbackChannel.COMPLETION_SOUNDS.bit()
            | FeedbackChannel.PARTICLES.bit()
            | FeedbackChannel.TITLES.bit()
            | FeedbackChannel.JOIN_REMINDERS.bit();

    public static FeedbackPreferences defaults() {
        return new FeedbackPreferences(DEFAULT_MASK);
    }

    public boolean enabled(FeedbackChannel channel) {
        return (mask & channel.bit()) != 0;
    }

    public FeedbackPreferences toggle(FeedbackChannel channel) {
        return new FeedbackPreferences(mask ^ channel.bit());
    }

    public FeedbackPreferences with(FeedbackChannel channel, boolean enabled) {
        return enabled
                ? new FeedbackPreferences(mask | channel.bit())
                : new FeedbackPreferences(mask & ~channel.bit());
    }
}


package com.zpkdxgames.plexonquests.gui.player;

/** Pure presentation snapshot for a 10-segment progress bar and clamped percentage. */
public record QuestProgressPresentation(long current, long required, int percentage, String bar) {
    private static final int SEGMENTS = 10;

    public static QuestProgressPresentation of(long current, long required) {
        long safeRequired = Math.max(0L, required);
        long safeCurrent = Math.max(0L, current);
        double raw = safeRequired == 0L ? 0D : (double) safeCurrent * 100D / safeRequired;
        int percentage = (int) Math.floor(Math.max(0D, Math.min(100D, raw)));
        int filled = (int) Math.floor(percentage * SEGMENTS / 100D);
        String bar = "■".repeat(filled) + "□".repeat(SEGMENTS - filled);
        return new QuestProgressPresentation(safeCurrent, safeRequired, percentage, bar);
    }

    public String ratio() {
        return current + " / " + required;
    }
}

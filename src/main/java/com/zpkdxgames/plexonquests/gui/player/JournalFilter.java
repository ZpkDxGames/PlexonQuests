package com.zpkdxgames.plexonquests.gui.player;

/** Bounded player-facing discovery filters. */
public enum JournalFilter {
    ALL("All"),
    ELIGIBLE("Eligible"),
    LOCKED("Locked");

    private final String label;

    JournalFilter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public JournalFilter next() {
        JournalFilter[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

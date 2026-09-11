package com.zpkdxgames.plexonquests.gui.player;

/** Product-level priority for the Journal Home primary action. */
public enum JournalNextAction {
    CLAIM_READY("Claim completed quest"),
    CONTINUE_TRACKED("Continue tracked quest"),
    VIEW_ACTIVE("View active quests"),
    BROWSE_ELIGIBLE("Browse eligible quests");

    private final String label;

    JournalNextAction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static JournalNextAction resolve(int readyToClaim, boolean hasTrackedActive, int active) {
        if (readyToClaim > 0) return CLAIM_READY;
        if (hasTrackedActive) return CONTINUE_TRACKED;
        if (active > 0) return VIEW_ACTIVE;
        return BROWSE_ELIGIBLE;
    }
}

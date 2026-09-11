package com.zpkdxgames.plexonquests.gui.player;

import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;

/** Central translation from backend states to player product language. */
public final class QuestStatePresentation {
    private QuestStatePresentation() {}

    public static String label(JournalState state) {
        return switch (state) {
            case AVAILABLE -> "ELIGIBLE";
            case ACTIVE, TRACKED -> "ACTIVE";
            case COMPLETABLE -> "READY TO CLAIM";
            case LOCKED -> "LOCKED";
            case COMPLETED -> "COMPLETED";
            case COOLDOWN -> "COMPLETED · RETURNS NEXT ROTATION";
            case EXPIRED -> "EXPIRED";
            case DISABLED -> "UNAVAILABLE";
        };
    }

    public static String label(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "ACTIVE";
            case COMPLETED -> "READY TO CLAIM";
            case CLAIMING -> "CLAIM PROCESSING";
            case CLAIMED -> "COMPLETED";
            case EXPIRED -> "EXPIRED";
            case CANCELLED -> "UNAVAILABLE";
        };
    }

    public static String tracking(boolean tracked) {
        return tracked ? "TRACKED" : "NOT TRACKED";
    }
}

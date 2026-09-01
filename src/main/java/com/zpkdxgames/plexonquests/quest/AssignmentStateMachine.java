package com.zpkdxgames.plexonquests.quest;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

public final class AssignmentStateMachine {
    private static final Map<AssignmentState, EnumSet<AssignmentState>> ALLOWED = new EnumMap<>(AssignmentState.class);

    static {
        ALLOWED.put(AssignmentState.ACTIVE, EnumSet.of(AssignmentState.COMPLETED, AssignmentState.EXPIRED, AssignmentState.CANCELLED));
        ALLOWED.put(AssignmentState.COMPLETED, EnumSet.of(AssignmentState.CLAIMING, AssignmentState.EXPIRED, AssignmentState.CANCELLED));
        ALLOWED.put(AssignmentState.CLAIMING, EnumSet.of(AssignmentState.CLAIMED, AssignmentState.COMPLETED));
        ALLOWED.put(AssignmentState.CLAIMED, EnumSet.noneOf(AssignmentState.class));
        ALLOWED.put(AssignmentState.EXPIRED, EnumSet.noneOf(AssignmentState.class));
        ALLOWED.put(AssignmentState.CANCELLED, EnumSet.noneOf(AssignmentState.class));
    }

    private AssignmentStateMachine() {}

    public static boolean allowed(AssignmentState from, AssignmentState to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void require(AssignmentState from, AssignmentState to) {
        if (!allowed(from, to)) {
            throw new IllegalStateException("Invalid assignment transition " + from + " -> " + to);
        }
    }
}


package com.zpkdxgames.plexonquests.quest;

public record ProgressResult(
        boolean accepted,
        String objectiveId,
        long oldValue,
        long newValue,
        boolean objectiveCompleted,
        boolean questCompleted) {

    public static ProgressResult rejected(String objectiveId, long current) {
        return new ProgressResult(false, objectiveId, current, current, false, false);
    }
}


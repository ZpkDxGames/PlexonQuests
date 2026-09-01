package com.zpkdxgames.plexonquests.reward;

import java.util.List;
import java.util.Objects;

public record RewardBundle(RewardMode mode, List<RewardDefinition> entries) {
    public RewardBundle {
        mode = Objects.requireNonNullElse(mode, RewardMode.ALL);
        entries = List.copyOf(entries);
    }
}


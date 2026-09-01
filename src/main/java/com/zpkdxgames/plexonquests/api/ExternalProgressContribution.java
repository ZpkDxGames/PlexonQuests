package com.zpkdxgames.plexonquests.api;

import java.util.Objects;

/** A validated external contribution. Source tokens should be stable transaction/event IDs when available. */
public record ExternalProgressContribution(
        ExternalObjectiveType type, long amount, boolean unique, String sourceToken) {
    public ExternalProgressContribution {
        type = Objects.requireNonNull(type, "type");
        if (amount <= 0L) {
            throw new IllegalArgumentException("Contribution amount must be positive");
        }
        sourceToken = Objects.requireNonNullElse(sourceToken, "");
        if (sourceToken.length() > 256) {
            throw new IllegalArgumentException("Source token cannot exceed 256 characters");
        }
    }

    public static ExternalProgressContribution of(ExternalObjectiveType type, long amount) {
        return new ExternalProgressContribution(type, amount, true, "");
    }
}

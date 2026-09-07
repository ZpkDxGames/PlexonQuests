package com.zpkdxgames.plexonquests.api;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** A validated external contribution. Source tokens should be stable transaction/event IDs when available. */
public record ExternalProgressContribution(
        ExternalObjectiveType type,
        long amount,
        boolean unique,
        Map<String, String> metadata,
        String sourceToken) {
    public ExternalProgressContribution {
        type = Objects.requireNonNull(type, "type");
        if (amount <= 0L) {
            throw new IllegalArgumentException("Contribution amount must be positive");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    normalized.put(
                            key.trim().toLowerCase(Locale.ROOT),
                            Objects.requireNonNullElse(value, "").trim());
                }
            });
        }
        metadata = Map.copyOf(normalized);
        sourceToken = Objects.requireNonNullElse(sourceToken, "");
        if (sourceToken.length() > 256) {
            throw new IllegalArgumentException("Source token cannot exceed 256 characters");
        }
    }

    public ExternalProgressContribution(
            ExternalObjectiveType type, long amount, boolean unique, String sourceToken) {
        this(type, amount, unique, Map.of(), sourceToken);
    }

    public static ExternalProgressContribution of(ExternalObjectiveType type, long amount) {
        return new ExternalProgressContribution(type, amount, true, Map.of(), "");
    }
}

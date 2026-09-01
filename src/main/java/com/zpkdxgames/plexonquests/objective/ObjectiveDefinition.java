package com.zpkdxgames.plexonquests.objective;

import java.util.Objects;

public record ObjectiveDefinition(
        String id, ObjectiveType type, long amount, String display, ObjectiveFilters filters) {
    public ObjectiveDefinition {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        if (amount <= 0L) {
            throw new IllegalArgumentException("Objective amount must be positive");
        }
        display = Objects.requireNonNullElse(display, id);
        filters = Objects.requireNonNullElseGet(filters, ObjectiveFilters::empty);
    }
}


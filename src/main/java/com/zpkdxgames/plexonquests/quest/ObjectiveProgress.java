package com.zpkdxgames.plexonquests.quest;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import java.util.Objects;

public final class ObjectiveProgress {
    private final ObjectiveDefinition definition;
    private long current;

    public ObjectiveProgress(ObjectiveDefinition definition, long current) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.current = Math.max(0L, Math.min(definition.amount(), current));
    }

    public ObjectiveDefinition definition() {
        return definition;
    }

    public long current() {
        return current;
    }

    public long required() {
        return definition.amount();
    }

    public boolean complete() {
        return current >= definition.amount();
    }

    long add(long delta) {
        if (delta <= 0L || complete()) {
            return current;
        }
        long next;
        try {
            next = Math.addExact(current, delta);
        } catch (ArithmeticException ignored) {
            next = Long.MAX_VALUE;
        }
        current = Math.min(definition.amount(), next);
        return current;
    }

    long set(long value) {
        current = Math.max(0L, Math.min(definition.amount(), value));
        return current;
    }
}


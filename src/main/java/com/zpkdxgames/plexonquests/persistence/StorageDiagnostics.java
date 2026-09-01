package com.zpkdxgames.plexonquests.persistence;

import java.time.Duration;
import java.time.Instant;

public record StorageDiagnostics(
        boolean open,
        int queueDepth,
        int queueCapacity,
        int dirtyAssignments,
        Duration lastFlushDuration,
        String lastFlushResult,
        Instant lastFlushAt,
        long rejectedOperations,
        long uncertainClaims) {}


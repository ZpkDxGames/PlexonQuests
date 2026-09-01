package com.zpkdxgames.plexonquests.rotation;

import java.time.Instant;

public record RotationPeriod(String key, Instant startsAt, Instant endsAt) {}


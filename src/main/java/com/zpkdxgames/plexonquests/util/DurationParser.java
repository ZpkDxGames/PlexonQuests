package com.zpkdxgames.plexonquests.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern STRICT = Pattern.compile("([0-9]+)(ms|s|m|h|d)");

    private DurationParser() {}

    public static Duration parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Duration is missing");
        }
        Matcher matcher = STRICT.matcher(input.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Expected a duration such as 10s, 5m, 24h, or 7d");
        }
        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Duration is too large", exception);
        }
        return switch (matcher.group(2)) {
            case "ms" -> Duration.ofMillis(value);
            case "s" -> Duration.ofSeconds(value);
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            default -> throw new IllegalStateException("Unreachable duration unit");
        };
    }
}


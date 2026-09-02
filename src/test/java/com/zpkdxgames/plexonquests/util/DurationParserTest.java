package com.zpkdxgames.plexonquests.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {
    @Test
    void parsesEverySupportedUnit() {
        assertEquals(Duration.ofMillis(250), DurationParser.parse("250ms"));
        assertEquals(Duration.ofSeconds(10), DurationParser.parse("10s"));
        assertEquals(Duration.ofMinutes(5), DurationParser.parse("5m"));
        assertEquals(Duration.ofHours(24), DurationParser.parse("24h"));
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d"));
    }

    @Test
    void rejectsAmbiguousOrNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("1.5h"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("-1s"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(null));
    }
}

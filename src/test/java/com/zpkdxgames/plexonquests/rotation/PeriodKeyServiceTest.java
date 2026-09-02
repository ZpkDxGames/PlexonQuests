package com.zpkdxgames.plexonquests.rotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class PeriodKeyServiceTest {
    @Test
    void dailyResetUsesPreviousLocalDateBeforeBoundary() {
        ZoneId zone = ZoneId.of("Europe/London");
        RotationPeriod before = PeriodKeyService.daily(
                Instant.parse("2026-09-02T02:59:59Z"), zone, LocalTime.of(4, 0));
        RotationPeriod after = PeriodKeyService.daily(
                Instant.parse("2026-09-02T03:00:00Z"), zone, LocalTime.of(4, 0));

        assertEquals("daily:2026-09-01", before.key());
        assertEquals("daily:2026-09-02", after.key());
        assertEquals(before.endsAt(), after.startsAt());
    }

    @Test
    void dailyPeriodsRemainContiguousAcrossSpringDstGap() {
        ZoneId zone = ZoneId.of("America/New_York");
        RotationPeriod gapDay = PeriodKeyService.daily(
                Instant.parse("2026-03-08T08:00:00Z"), zone, LocalTime.of(2, 30));
        RotationPeriod nextDay = PeriodKeyService.daily(
                Instant.parse("2026-03-09T08:00:00Z"), zone, LocalTime.of(2, 30));

        assertEquals("daily:2026-03-08", gapDay.key());
        assertEquals(gapDay.endsAt(), nextDay.startsAt());
        assertTrue(gapDay.startsAt().isBefore(gapDay.endsAt()));
        assertEquals(Duration.ofHours(23), Duration.between(gapDay.startsAt(), gapDay.endsAt()));
    }

    @Test
    void weeklyBoundaryUsesConfiguredDayAndTime() {
        ZoneId zone = ZoneId.of("UTC");
        RotationPeriod before = PeriodKeyService.weekly(
                Instant.parse("2026-09-07T03:59:59Z"), zone, DayOfWeek.MONDAY, LocalTime.of(4, 0));
        RotationPeriod after = PeriodKeyService.weekly(
                Instant.parse("2026-09-07T04:00:00Z"), zone, DayOfWeek.MONDAY, LocalTime.of(4, 0));

        assertEquals("weekly:2026-08-31", before.key());
        assertEquals("weekly:2026-09-07", after.key());
        assertEquals(before.endsAt(), after.startsAt());
    }
}

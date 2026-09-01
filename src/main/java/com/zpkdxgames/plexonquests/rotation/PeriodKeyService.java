package com.zpkdxgames.plexonquests.rotation;

import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

public final class PeriodKeyService {
    private final PluginSettings.Rotation settings;

    public PeriodKeyService(PluginSettings.Rotation settings) {
        this.settings = settings;
    }

    public RotationPeriod period(QuestScope scope, Instant instant) {
        return switch (scope) {
            case DAILY -> daily(instant, settings.zone(), settings.dailyReset());
            case WEEKLY -> weekly(
                    instant, settings.zone(), settings.weeklyResetDay(), settings.weeklyResetTime());
            case MILESTONE -> new RotationPeriod("milestone", Instant.EPOCH, Instant.MAX);
            case MANUAL -> throw new IllegalArgumentException("Manual quests do not use a shared rotation period");
        };
    }

    static RotationPeriod daily(Instant instant, ZoneId zone, LocalTime reset) {
        ZonedDateTime local = instant.atZone(zone);
        LocalDate periodDate = local.toLocalDate();
        ZonedDateTime candidate = boundary(periodDate, reset, zone);
        if (local.isBefore(candidate)) {
            periodDate = periodDate.minusDays(1);
        }
        ZonedDateTime start = boundary(periodDate, reset, zone);
        ZonedDateTime end = boundary(periodDate.plusDays(1), reset, zone);
        return new RotationPeriod("daily:" + periodDate, start.toInstant(), end.toInstant());
    }

    static RotationPeriod weekly(
            Instant instant, ZoneId zone, DayOfWeek resetDay, LocalTime resetTime) {
        ZonedDateTime local = instant.atZone(zone);
        LocalDate candidateDate = local.toLocalDate().with(TemporalAdjusters.previousOrSame(resetDay));
        ZonedDateTime candidate = boundary(candidateDate, resetTime, zone);
        if (local.isBefore(candidate)) {
            candidateDate = candidateDate.minusWeeks(1);
        }
        ZonedDateTime start = boundary(candidateDate, resetTime, zone);
        ZonedDateTime end = boundary(candidateDate.plusWeeks(1), resetTime, zone);
        return new RotationPeriod("weekly:" + candidateDate, start.toInstant(), end.toInstant());
    }

    private static ZonedDateTime boundary(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone);
    }
}


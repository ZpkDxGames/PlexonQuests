package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.util.DurationParser;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public record PluginSettings(
        Rotation rotation,
        Assignments assignments,
        RankProgression rankProgression,
        Rerolls rerolls,
        Tracking tracking,
        Storage storage,
        Claims claims,
        Feedback feedback,
        Security security,
        Diagnostics diagnostics) {

    public record Rotation(
            ZoneId zone,
            LocalTime dailyReset,
            DayOfWeek weeklyResetDay,
            LocalTime weeklyResetTime,
            Duration claimGrace,
            Duration recentHistoryExclusion) {}

    public record Assignments(
            int baseDailySlots,
            int baseWeeklySlots,
            int maximumDailySlots,
            int maximumWeeklySlots,
            int maximumActiveManual,
            boolean multiQuestProgress) {}

    public record RankProgression(
            boolean enabled,
            String provider,
            boolean fallbackPermissions,
            int bonusPerCategory,
            Map<String, Integer> categories) {
        public RankProgression {
            categories = Map.copyOf(categories);
        }
    }

    public record Rerolls(
            boolean enabled,
            int freeDaily,
            int freeWeekly,
            int maximumPerPeriod,
            boolean paidEnabled,
            double paidCost,
            String paidProvider) {}

    public record Tracking(
            boolean creativeCounts,
            boolean spectatorCounts,
            BlockOriginMode originMode,
            int maximumOriginsPerChunk,
            long travelSampleTicks,
            double travelMaximumDelta,
            long actionbarThrottleTicks,
            long bossbarThrottleTicks,
            Duration bossbarQuietPeriod,
            Duration afkTimeout,
            long contributionCooldownMillis) {}

    public record Storage(
            String file,
            int busyTimeoutMillis,
            Duration flushInterval,
            Duration checkpointInterval,
            int queueCapacity,
            Duration shutdownTimeout,
            int historyRetentionDays,
            int historyMaximumPerPlayer) {}

    public record Claims(
            boolean manualByDefault,
            OverflowPolicy overflowPolicy,
            boolean auditLog,
            Duration reservationTimeout) {}

    public record Feedback(
            long joinReminderDelayTicks,
            Duration failureMessageCooldown,
            PinnedDisplay pinnedDisplay,
            int maximumPinned,
            List<Integer> progressThresholds) {
        public Feedback {
            progressThresholds = List.copyOf(progressThresholds);
        }
    }

    public record Security(
            long commandCooldownMillis,
            long guiClickCooldownMillis,
            int maximumNumberedPermission,
            int maximumSerializedItemBytes) {}

    public record Diagnostics(boolean debugTiming, int timingSampleRate) {}

    public static PluginSettings parse(YamlConfiguration yaml) {
        Rotation rotation = new Rotation(
                ZoneId.of(yaml.getString("rotation.timezone", "UTC")),
                LocalTime.parse(yaml.getString("rotation.daily-reset", "04:00")),
                enumValue(DayOfWeek.class, yaml.getString("rotation.weekly-reset-day"), DayOfWeek.MONDAY),
                LocalTime.parse(yaml.getString("rotation.weekly-reset-time", "04:00")),
                duration(yaml, "rotation.completed-claim-grace", "24h"),
                duration(yaml, "rotation.recent-history-exclusion", "7d"));

        Assignments assignments = new Assignments(
                positive(yaml, "assignments.base-daily-slots", 3),
                positive(yaml, "assignments.base-weekly-slots", 2),
                positive(yaml, "assignments.maximum-daily-slots", 9),
                positive(yaml, "assignments.maximum-weekly-slots", 5),
                positive(yaml, "assignments.maximum-active-manual", 20),
                yaml.getBoolean("assignments.allow-one-action-to-progress-multiple-quests", true));

        Map<String, Integer> categories = new LinkedHashMap<>();
        ConfigurationSection categorySection = yaml.getConfigurationSection("rank-progression.categories");
        if (categorySection != null) {
            for (String key : categorySection.getKeys(false)) {
                categories.put(key.toLowerCase(Locale.ROOT), Math.max(0, categorySection.getInt(key)));
            }
        }
        RankProgression rank = new RankProgression(
                yaml.getBoolean("rank-progression.enabled", true),
                yaml.getString("rank-progression.provider", "PLEXON_RANKS"),
                yaml.getBoolean("rank-progression.fallback-permissions", true),
                positive(yaml, "rank-progression.bonus-per-category", 1),
                categories);

        Rerolls rerolls = new Rerolls(
                yaml.getBoolean("rerolls.enabled", true),
                Math.max(0, yaml.getInt("rerolls.free-daily", 1)),
                Math.max(0, yaml.getInt("rerolls.free-weekly", 1)),
                Math.max(0, yaml.getInt("rerolls.maximum-per-period", 3)),
                yaml.getBoolean("rerolls.paid.enabled", false),
                Math.max(0D, yaml.getDouble("rerolls.paid.cost", 2500D)),
                yaml.getString("rerolls.paid.provider", "VAULT"));

        Tracking tracking = new Tracking(
                yaml.getBoolean("tracking.creative-counts", false),
                yaml.getBoolean("tracking.spectator-counts", false),
                enumValue(BlockOriginMode.class, yaml.getString("tracking.natural-block-mode"), BlockOriginMode.PERSISTENT_CHUNK),
                positive(yaml, "tracking.natural-block-maximum-positions-per-chunk", 65536),
                positive(yaml, "tracking.travel-sample-ticks", 20),
                Math.max(1D, yaml.getDouble("tracking.travel-maximum-delta", 32D)),
                positive(yaml, "tracking.actionbar-throttle-ticks", 10),
                positive(yaml, "tracking.bossbar-throttle-ticks", 10),
                duration(yaml, "tracking.bossbar-quiet-period", "4s"),
                duration(yaml, "tracking.afk-timeout", "5m"),
                Math.max(0L, yaml.getLong("tracking.contribution-cooldown-ms", 0L)));

        Storage storage = new Storage(
                yaml.getString("storage.file", "plexonquests.db"),
                positive(yaml, "storage.busy-timeout-ms", 5000),
                duration(yaml, "storage.flush-interval", "5s"),
                duration(yaml, "storage.checkpoint-interval", "5m"),
                positive(yaml, "storage.queue-capacity", 4096),
                duration(yaml, "storage.shutdown-timeout", "15s"),
                positive(yaml, "storage.history-retention-days", 180),
                positive(yaml, "storage.history-maximum-per-player", 500));

        Claims claims = new Claims(
                yaml.getBoolean("claims.manual-by-default", true),
                enumValue(OverflowPolicy.class, yaml.getString("claims.overflow-policy"), OverflowPolicy.CANCEL),
                yaml.getBoolean("claims.audit-log", true),
                duration(yaml, "claims.reservation-timeout", "30s"));

        Feedback feedback = new Feedback(
                ticks(yaml.getString("feedback.join-reminder-delay", "40t")),
                duration(yaml, "feedback.failure-message-cooldown", "2s"),
                enumValue(PinnedDisplay.class, yaml.getString("feedback.pinned-display"), PinnedDisplay.AUTO),
                positive(yaml, "feedback.maximum-pinned", 1),
                yaml.getIntegerList("feedback.progress-thresholds"));

        Security security = new Security(
                Math.max(0L, yaml.getLong("security.command-cooldown-ms", 250L)),
                Math.max(0L, yaml.getLong("security.gui-click-cooldown-ms", 120L)),
                positive(yaml, "security.maximum-numbered-permission", 20),
                positive(yaml, "security.maximum-serialized-item-bytes", 65536));

        Diagnostics diagnostics = new Diagnostics(
                yaml.getBoolean("diagnostics.debug-timing", false),
                positive(yaml, "diagnostics.timing-sample-rate", 1000));

        if (assignments.maximumDailySlots() < assignments.baseDailySlots()
                || assignments.maximumWeeklySlots() < assignments.baseWeeklySlots()) {
            throw new IllegalArgumentException("Assignment maximum slots cannot be lower than base slots");
        }
        return new PluginSettings(rotation, assignments, rank, rerolls, tracking, storage, claims, feedback, security, diagnostics);
    }

    private static Duration duration(YamlConfiguration yaml, String path, String fallback) {
        return DurationParser.parse(yaml.getString(path, fallback));
    }

    private static int positive(YamlConfiguration yaml, String path, int fallback) {
        int value = yaml.getInt(path, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException(path + " must be positive");
        }
        return value;
    }

    private static long ticks(String value) {
        if (value == null || !value.matches("[0-9]+t")) {
            throw new IllegalArgumentException("Tick duration must look like 40t");
        }
        return Long.parseLong(value.substring(0, value.length() - 1));
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);
        }
    }
}


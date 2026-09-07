package com.zpkdxgames.plexonquests.objective.matcher;

import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validation and matching for integration-only scalar filters stored in ObjectiveFilters.extras(). */
public final class IntegrationObjectiveFilters {
    private static final Set<String> RANK = Set.of("from-rank", "to-rank", "from-category", "to-category");
    private static final Set<String> TOOL = Set.of("tool-id", "tool-category", "minimum-level", "progress-type", "material");
    private static final Set<String> KEY = Set.of("key-category", "source");
    private static final Set<String> CRATE = Set.of("crate-id", "crate-category", "key-id", "reward-id");
    private static final Set<String> SHOP = Set.of("shop-id", "shop-type", "minimum-rating");
    private static final Set<String> DAILY = Set.of("tier-id", "day");

    private IntegrationObjectiveFilters() {}

    public static void validate(ObjectiveType type, Map<String, String> filters) {
        if (filters == null || filters.isEmpty() || !integrationType(type)) {
            return;
        }
        Set<String> supported = supported(type);
        for (String key : filters.keySet()) {
            String normalized = normalizeKey(key);
            if (!supported.contains(normalized)) {
                throw new IllegalArgumentException("Unsupported filter '" + key + "' for objective type " + type);
            }
        }
    }

    public static boolean matches(ObjectiveType type, Map<String, String> filters, Map<String, String> metadata) {
        if (filters == null || filters.isEmpty() || !integrationType(type)) {
            return true;
        }
        Map<String, String> safeMetadata = metadata == null ? Map.of() : metadata;
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            String key = normalizeKey(entry.getKey());
            String expected = normalizeValue(entry.getValue());
            if (key.equals("minimum-level")) {
                if (!minimumLong(safeMetadata.get("tool.level.new"), expected)) return false;
                continue;
            }
            if (key.equals("minimum-rating")) {
                if (!minimumDouble(safeMetadata.get("shop.rating"), expected)) return false;
                continue;
            }
            String metadataKey = metadataKey(key);
            String actual = safeMetadata.get(metadataKey);
            if (actual == null) {
                return false;
            }
            if (key.equals("reward-id")) {
                boolean any = Arrays.stream(actual.split(","))
                        .map(IntegrationObjectiveFilters::normalizeValue)
                        .anyMatch(expected::equals);
                if (!any) return false;
            } else if (!expected.equals(normalizeValue(actual))) {
                return false;
            }
        }
        return true;
    }

    private static boolean integrationType(ObjectiveType type) {
        return type != null && switch (type) {
            case PLEXON_RANK_UP,
                    PLEXON_TOOL_LEVEL_UP,
                    PLEXON_TOOL_PROGRESS,
                    PLEXON_KEY_EARN,
                    PLEXON_KEY_CLAIM,
                    PLEXON_CRATE_OPEN,
                    PLEXON_SHOP_VISIT,
                    PLEXON_SHOP_RATE,
                    PLEXON_SHOP_CREATE,
                    PLEXON_DAILY_REWARD_CLAIM -> true;
            default -> false;
        };
    }

    private static Set<String> supported(ObjectiveType type) {
        return switch (type) {
            case PLEXON_RANK_UP -> RANK;
            case PLEXON_TOOL_LEVEL_UP, PLEXON_TOOL_PROGRESS -> TOOL;
            case PLEXON_KEY_EARN, PLEXON_KEY_CLAIM -> KEY;
            case PLEXON_CRATE_OPEN -> CRATE;
            case PLEXON_SHOP_VISIT, PLEXON_SHOP_RATE, PLEXON_SHOP_CREATE -> SHOP;
            case PLEXON_DAILY_REWARD_CLAIM -> DAILY;
            default -> Set.of();
        };
    }

    private static String metadataKey(String filter) {
        return switch (filter) {
            case "from-rank" -> "rank.from";
            case "to-rank" -> "rank.to";
            case "from-category" -> "rank.category.from";
            case "to-category" -> "rank.category.to";
            case "tool-id" -> "tool.id";
            case "tool-category" -> "tool.category";
            case "progress-type" -> "tool.progress.type";
            case "material" -> "tool.material";
            case "key-category" -> "key.category";
            case "source" -> "key.source";
            case "crate-id" -> "crate.id";
            case "crate-category" -> "crate.category";
            case "key-id" -> "crate.key";
            case "reward-id" -> "crate.reward";
            case "shop-id" -> "shop.id";
            case "shop-type" -> "shop.type";
            case "tier-id" -> "daily.tier";
            case "day" -> "daily.day";
            default -> "";
        };
    }

    private static boolean minimumLong(String actual, String expected) {
        if (actual == null) return false;
        try {
            return Long.parseLong(actual.trim()) >= Long.parseLong(expected.trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean minimumDouble(String actual, String expected) {
        if (actual == null) return false;
        try {
            return Double.parseDouble(actual.trim()) >= Double.parseDouble(expected.trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }
}

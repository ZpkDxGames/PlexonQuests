package com.zpkdxgames.plexonquests.integration;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

final class PlexonIntegrationAdapters {
    private PlexonIntegrationAdapters() {}

    static List<IntegrationAdapter> all() {
        return List.of(
                new RanksAdapter(),
                new ToolsAdapter(),
                new KeysAdapter(),
                new CratesAdapter(),
                new ShopsAdapter(),
                new DailyRewardsAdapter());
    }

    private abstract static class ReflectiveAdapter implements IntegrationAdapter {
        private final String id;
        private final String pluginName;

        ReflectiveAdapter(String id, String pluginName) {
            this.id = id;
            this.pluginName = pluginName;
        }

        @Override
        public final String id() {
            return id;
        }

        final Plugin provider() {
            Plugin provider = Bukkit.getPluginManager().getPlugin(pluginName);
            return provider != null && provider.isEnabled() ? provider : null;
        }

        final Class<? extends Event> eventType(Plugin provider, String className) throws ClassNotFoundException {
            Class<?> raw = Class.forName(className, false, provider.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) {
                throw new IllegalArgumentException(className + " is not a Bukkit event");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) raw;
            return eventType;
        }

        final void listen(
                IntegrationContext context,
                Class<? extends Event> eventType,
                EventConsumer consumer) {
            Listener listener = new Listener() {};
            Bukkit.getPluginManager().registerEvent(
                    eventType,
                    listener,
                    EventPriority.MONITOR,
                    (ignored, event) -> {
                        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                            return;
                        }
                        try {
                            consumer.accept(event);
                        } catch (ReflectiveOperationException | RuntimeException exception) {
                            context.plugin().getLogger().log(
                                    Level.WARNING,
                                    "PlexonQuests integration event conversion failed closed for " + id,
                                    exception);
                        }
                    },
                    context.plugin(),
                    true);
        }

        final Method required(Class<?> type, String... names) throws NoSuchMethodException {
            Method method = optional(type, names);
            if (method == null) {
                throw new NoSuchMethodException(type.getName() + " does not expose any of " + String.join(", ", names));
            }
            return method;
        }

        final Method optional(Class<?> type, String... names) {
            for (String name : names) {
                try {
                    return type.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                    // Try the next supported public accessor name.
                }
            }
            return null;
        }

        final Object value(Method method, Object owner) throws InvocationTargetException, IllegalAccessException {
            return method == null ? null : method.invoke(owner);
        }

        final String text(Method method, Object owner) throws InvocationTargetException, IllegalAccessException {
            Object value = value(method, owner);
            return value == null ? "" : String.valueOf(value);
        }

        final long number(Method method, Object owner, long fallback)
                throws InvocationTargetException, IllegalAccessException {
            Object value = value(method, owner);
            return value instanceof Number number ? number.longValue() : fallback;
        }

        final Player player(Method method, Event event) throws InvocationTargetException, IllegalAccessException {
            Object value = method.invoke(event);
            return value instanceof Player player ? player : null;
        }

        final void warnUnavailable(IntegrationContext context, Throwable exception) {
            context.plugin().getLogger().log(
                    Level.WARNING,
                    "Supported public API for " + id + " could not be registered; integration remains fail-closed",
                    exception);
        }

        @FunctionalInterface
        interface EventConsumer {
            void accept(Event event) throws ReflectiveOperationException;
        }
    }

    private static final class RanksAdapter extends ReflectiveAdapter {
        RanksAdapter() {
            super("PLEXON_RANKS", "PlexonRanks");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) {
                return;
            }
            try {
                Class<? extends Event> type = eventType(provider, "com.zpkdxgames.plexonranks.event.PlexonRankupEvent");
                Method getPlayer = required(type, "getPlayer");
                Method from = required(type, "from");
                Method to = required(type, "to");
                Method transactionId = required(type, "transactionId");
                Method fromId = required(from.getReturnType(), "id");
                Method toId = required(to.getReturnType(), "id");
                listen(context, type, event -> {
                    Player player = player(getPlayer, event);
                    if (player == null) {
                        return;
                    }
                    Object fromRank = value(from, event);
                    Object toRank = value(to, event);
                    String previous = normalize(text(fromId, fromRank));
                    String current = normalize(text(toId, toRank));
                    String transaction = text(transactionId, event).trim();
                    Set<String> categories = context.configs().snapshot().settings().rankProgression().categories().keySet();
                    Map<String, String> metadata = Map.of(
                            "rank.from", previous,
                            "rank.to", current,
                            "rank.category.from", category(previous, categories),
                            "rank.category.to", category(current, categories),
                            "rank.transaction", transaction);
                    String token = transaction.isBlank() ? "" : "rankup:" + transaction;
                    context.progress().contribute(
                            player,
                            Contribution.integration(ObjectiveType.PLEXON_RANK_UP, 1L, player, metadata, token));
                    context.profiles().profile(player).ifPresent(profile -> refreshRank(context, player, profile));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }

        private static void refreshRank(IntegrationContext context, Player player, PlayerProfile profile) {
            context.profiles().refreshRankCategory(profile);
            context.rotations().ensure(player, profile);
        }
    }

    private static final class CratesAdapter extends ReflectiveAdapter {
        CratesAdapter() {
            super("PLEXON_CRATES", "PlexonCrates");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) {
                return;
            }
            try {
                Class<? extends Event> type = eventType(provider, "com.antondev.crates.api.event.CrateOpenEvent");
                Method player = required(type, "player");
                Method plan = required(type, "plan");
                Class<?> planType = plan.getReturnType();
                Method transactionId = required(planType, "transactionId");
                Method crateId = required(planType, "crateId");
                Method keyId = required(planType, "keyId");
                Method openingCount = required(planType, "openingCount");
                Method rewardIds = optional(planType, "rewardIds");
                Method source = optional(planType, "source");
                listen(context, type, event -> {
                    Player target = player(player, event);
                    Object opening = value(plan, event);
                    if (target == null || opening == null) {
                        return;
                    }
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("crate.id", normalize(text(crateId, opening)));
                    put(metadata, "crate.key", normalize(text(keyId, opening)));
                    put(metadata, "crate.source", normalize(text(source, opening)));
                    Object rewards = value(rewardIds, opening);
                    if (rewards instanceof List<?> list && !list.isEmpty()) {
                        List<String> ids = new ArrayList<>();
                        list.forEach(item -> ids.add(normalize(String.valueOf(item))));
                        metadata.put("crate.reward", String.join(",", ids));
                    }
                    String transaction = text(transactionId, opening).trim();
                    String token = transaction.isBlank() ? "" : "crateopen:" + transaction;
                    long amount = Math.max(1L, number(openingCount, opening, 1L));
                    context.progress().contribute(
                            target,
                            Contribution.integration(ObjectiveType.PLEXON_CRATE_OPEN, amount, target, metadata, token));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }
    }

    private static final class DailyRewardsAdapter extends ReflectiveAdapter {
        DailyRewardsAdapter() {
            super("PLEXON_DAILY_REWARDS", "PlexonDailyRewards");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) {
                return;
            }
            try {
                Class<? extends Event> type = eventType(
                        provider, "com.zpkdxgames.plexondailyrewards.event.DailyRewardClaimedEvent");
                Method getPlayer = required(type, "getPlayer");
                Method tier = required(type, "getTierId");
                Method day = required(type, "getDay");
                Method forced = required(type, "isForced");
                listen(context, type, event -> {
                    Player player = player(getPlayer, event);
                    if (player == null) {
                        return;
                    }
                    String tierId = normalize(text(tier, event));
                    long rewardDay = number(day, event, 0L);
                    Map<String, String> metadata = Map.of(
                            "daily.tier", tierId,
                            "daily.day", Long.toString(rewardDay),
                            "daily.forced", text(forced, event));
                    LocalDate date = LocalDate.now(context.configs().snapshot().settings().rotation().zone());
                    String token = "dailyclaim:" + player.getUniqueId() + ":" + date + ":" + tierId + ":" + rewardDay;
                    context.progress().contribute(
                            player,
                            Contribution.integration(
                                    ObjectiveType.PLEXON_DAILY_REWARD_CLAIM, 1L, player, metadata, token));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }
    }

    private static final class ToolsAdapter extends ReflectiveAdapter {
        ToolsAdapter() {
            super("PLEXON_TOOLS", "PlexonTools");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) {
                return;
            }
            registerLevel(context, provider);
            registerProgress(context, provider);
        }

        private void registerLevel(IntegrationContext context, Plugin provider) {
            try {
                Class<? extends Event> type = eventType(provider, "com.plexon.tools.event.PlexonToolLevelUpEvent");
                Method player = required(type, "getPlayer", "player");
                Method toolId = required(type, "toolId", "getToolId");
                Method oldLevel = required(type, "oldLevel", "getOldLevel");
                Method newLevel = required(type, "newLevel", "getNewLevel");
                Method category = optional(type, "toolCategory", "getToolCategory", "category");
                Method eventId = optional(type, "eventId", "transactionId", "getEventId");
                listen(context, type, event -> {
                    Player target = player(player, event);
                    if (target == null) return;
                    long oldValue = number(oldLevel, event, 0L);
                    long newValue = number(newLevel, event, oldValue + 1L);
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("tool.id", normalize(text(toolId, event)));
                    put(metadata, "tool.category", normalize(text(category, event)));
                    metadata.put("tool.level.old", Long.toString(oldValue));
                    metadata.put("tool.level.new", Long.toString(newValue));
                    String id = text(eventId, event).trim();
                    context.progress().contribute(target, Contribution.integration(
                            ObjectiveType.PLEXON_TOOL_LEVEL_UP,
                            Math.max(1L, newValue - oldValue),
                            target,
                            metadata,
                            id.isBlank() ? "" : "toollevel:" + id));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }

        private void registerProgress(IntegrationContext context, Plugin provider) {
            try {
                Class<? extends Event> type = eventType(provider, "com.plexon.tools.event.PlexonToolProgressEvent");
                Method player = required(type, "getPlayer", "player");
                Method toolId = required(type, "toolId", "getToolId");
                Method amount = optional(type, "amount", "delta", "progressDelta", "getAmount");
                Method level = optional(type, "level", "newLevel", "getLevel");
                Method category = optional(type, "toolCategory", "getToolCategory", "category");
                Method progressType = optional(type, "progressType", "getProgressType", "type");
                Method material = optional(type, "material", "getMaterial");
                Method eventId = optional(type, "eventId", "transactionId", "getEventId");
                listen(context, type, event -> {
                    Player target = player(player, event);
                    if (target == null) return;
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("tool.id", normalize(text(toolId, event)));
                    put(metadata, "tool.category", normalize(text(category, event)));
                    put(metadata, "tool.progress.type", normalize(text(progressType, event)));
                    Object materialValue = value(material, event);
                    if (materialValue instanceof Material bukkitMaterial) {
                        metadata.put("tool.material", bukkitMaterial.name().toLowerCase(Locale.ROOT));
                    } else if (materialValue != null) {
                        metadata.put("tool.material", normalize(String.valueOf(materialValue)));
                    }
                    long levelValue = number(level, event, 0L);
                    if (levelValue > 0L) metadata.put("tool.level.new", Long.toString(levelValue));
                    String id = text(eventId, event).trim();
                    context.progress().contribute(target, Contribution.integration(
                            ObjectiveType.PLEXON_TOOL_PROGRESS,
                            Math.max(1L, number(amount, event, 1L)),
                            target,
                            metadata,
                            id.isBlank() ? "" : "toolprogress:" + id));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }
    }

    private static final class KeysAdapter extends ReflectiveAdapter {
        KeysAdapter() {
            super("PLEXON_KEYS", "PlexonKeys");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) return;
            registerKeyEvent(context, provider, "com.antondev.keys.event.PlexonKeyEarnedEvent", ObjectiveType.PLEXON_KEY_EARN, "keyearn:");
            registerKeyEvent(context, provider, "com.antondev.keys.event.PlexonKeyClaimedEvent", ObjectiveType.PLEXON_KEY_CLAIM, "keyclaim:");
        }

        private void registerKeyEvent(
                IntegrationContext context,
                Plugin provider,
                String className,
                ObjectiveType objective,
                String prefix) {
            try {
                Class<? extends Event> type = eventType(provider, className);
                Method player = required(type, "getPlayer", "player");
                Method category = required(type, "category", "getCategory", "tier", "getTier");
                Method amount = optional(type, "amount", "getAmount");
                Method source = optional(type, "source", "getSource");
                Method eventId = optional(type, "eventId", "transactionId", "getEventId");
                listen(context, type, event -> {
                    Player target = player(player, event);
                    if (target == null) return;
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put("key.category", normalize(text(category, event)));
                    put(metadata, "key.source", normalize(text(source, event)));
                    String id = text(eventId, event).trim();
                    context.progress().contribute(target, Contribution.integration(
                            objective,
                            Math.max(1L, number(amount, event, 1L)),
                            target,
                            metadata,
                            id.isBlank() ? "" : prefix + id));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }
    }

    private static final class ShopsAdapter extends ReflectiveAdapter {
        ShopsAdapter() {
            super("PLEXON_SHOPS", "PlexonShops");
        }

        @Override
        public void register(IntegrationContext context) {
            Plugin provider = provider();
            if (provider == null) return;
            registerShopEvent(context, provider, "com.plexon.shops.event.PlexonShopVisitedEvent", ObjectiveType.PLEXON_SHOP_VISIT, "shopvisit:");
            registerShopEvent(context, provider, "com.plexon.shops.event.PlexonShopRatedEvent", ObjectiveType.PLEXON_SHOP_RATE, "shoprate:");
            registerShopEvent(context, provider, "com.plexon.shops.event.PlexonShopCreatedEvent", ObjectiveType.PLEXON_SHOP_CREATE, "shopcreate:");
        }

        private void registerShopEvent(
                IntegrationContext context,
                Plugin provider,
                String className,
                ObjectiveType objective,
                String prefix) {
            try {
                Class<? extends Event> type = eventType(provider, className);
                Method player = required(type, "getPlayer", "player");
                Method shopId = optional(type, "shopId", "getShopId", "id");
                Method shopType = optional(type, "shopType", "getShopType", "type");
                Method rating = optional(type, "rating", "getRating");
                Method eventId = optional(type, "eventId", "transactionId", "getEventId");
                listen(context, type, event -> {
                    Player target = player(player, event);
                    if (target == null) return;
                    Map<String, String> metadata = new LinkedHashMap<>();
                    put(metadata, "shop.id", normalize(text(shopId, event)));
                    put(metadata, "shop.type", normalize(text(shopType, event)));
                    Object ratingValue = value(rating, event);
                    if (ratingValue != null) metadata.put("shop.rating", String.valueOf(ratingValue));
                    String id = text(eventId, event).trim();
                    context.progress().contribute(target, Contribution.integration(
                            objective,
                            1L,
                            target,
                            metadata,
                            id.isBlank() ? "" : prefix + id));
                });
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnUnavailable(context, exception);
            }
        }
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private static String category(String rankId, Set<String> configured) {
        if (rankId == null || rankId.isBlank()) {
            return "default";
        }
        return configured.stream()
                .map(PlexonIntegrationAdapters::normalize)
                .filter(category -> rankId.equals(category)
                        || rankId.startsWith(category + "-")
                        || rankId.startsWith(category + "_"))
                .max(java.util.Comparator.comparingInt(String::length))
                .orElse("default");
    }
}

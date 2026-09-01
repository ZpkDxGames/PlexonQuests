package com.zpkdxgames.plexonquests.integration;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class IntegrationManager {
    private static final Map<String, Descriptor> DESCRIPTORS = descriptors();

    private final JavaPlugin plugin;
    private volatile Map<String, IntegrationState> states = Map.of();
    private final Listener bridgeListener = new Listener() {};

    public IntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void detect() {
        Map<String, IntegrationState> detected = new LinkedHashMap<>();
        DESCRIPTORS.forEach((id, descriptor) -> detected.put(id, inspect(id, descriptor)));
        states = Map.copyOf(detected);
    }

    public boolean available(String id) {
        IntegrationState state = states.get(normalize(id));
        return state != null && state.status() == IntegrationStatus.AVAILABLE;
    }

    public IntegrationState state(String id) {
        return states.getOrDefault(
                normalize(id),
                new IntegrationState(normalize(id), id, IntegrationStatus.MISSING, "", "Unknown integration"));
    }

    public Map<String, IntegrationState> states() {
        return states;
    }

    public String rankCategory(UUID playerId, Set<String> configuredCategories) {
        IntegrationState rankState = state("PLEXON_RANKS");
        if (rankState.status() != IntegrationStatus.AVAILABLE) {
            return "default";
        }
        Plugin provider = Bukkit.getPluginManager().getPlugin("PlexonRanks");
        if (provider == null) {
            return "default";
        }
        try {
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> apiType = Class.forName("com.zpkdxgames.plexonranks.api.PlexonRanksAPI", false, loader);
            @SuppressWarnings({"rawtypes", "unchecked"})
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) apiType);
            if (registration == null) {
                return "default";
            }
            Object optionalRank = apiType.getMethod("getRank", UUID.class).invoke(registration.getProvider(), playerId);
            if (!(optionalRank instanceof Optional<?> optional) || optional.isEmpty()) {
                return "default";
            }
            Object rank = optional.get();
            String rankId = String.valueOf(rank.getClass().getMethod("id").invoke(rank)).toLowerCase(Locale.ROOT);
            return configuredCategories.stream()
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .filter(category -> rankId.equals(category)
                            || rankId.startsWith(category + "-")
                            || rankId.startsWith(category + "_"))
                    .max(java.util.Comparator.comparingInt(String::length))
                    .orElse("default");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "PlexonRanks public API became unavailable", exception);
            return "default";
        }
    }

    public void registerProgressBridges(ProgressService progress) {
        registerPlayerEvent(
                "PlexonRanks",
                "com.zpkdxgames.plexonranks.event.PlexonRankupEvent",
                ObjectiveType.PLEXON_RANK_UP,
                progress);
        registerPlayerEvent(
                "PlexonDailyRewards",
                "com.zpkdxgames.plexondailyrewards.event.DailyRewardClaimedEvent",
                ObjectiveType.PLEXON_DAILY_REWARD_CLAIM,
                progress);
    }

    private void registerPlayerEvent(
            String pluginName, String eventClassName, ObjectiveType type, ProgressService progress) {
        Plugin provider = Bukkit.getPluginManager().getPlugin(pluginName);
        if (provider == null || !provider.isEnabled()) {
            return;
        }
        try {
            Class<?> raw = Class.forName(eventClassName, false, provider.getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(raw)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventType = (Class<? extends Event>) raw;
            Method getPlayer = raw.getMethod("getPlayer");
            Bukkit.getPluginManager().registerEvent(
                    eventType,
                    bridgeListener,
                    EventPriority.MONITOR,
                    (listener, event) -> {
                        if (event instanceof Cancellable cancellable && cancellable.isCancelled()) {
                            return;
                        }
                        try {
                            Object playerValue = getPlayer.invoke(event);
                            if (playerValue instanceof Player player) {
                                progress.contribute(player, Contribution.simple(type, 1L, player));
                            }
                        } catch (IllegalAccessException | InvocationTargetException exception) {
                            plugin.getLogger().log(Level.WARNING, "Could not read public integration event " + eventClassName, exception);
                        }
                    },
                    plugin,
                    true);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Could not register public integration event " + eventClassName, exception);
        }
    }

    private IntegrationState inspect(String id, Descriptor descriptor) {
        Plugin provider = Bukkit.getPluginManager().getPlugin(descriptor.pluginName());
        if (provider == null) {
            return new IntegrationState(id, descriptor.pluginName(), IntegrationStatus.MISSING, "", "Plugin is not installed");
        }
        if (!provider.isEnabled()) {
            return new IntegrationState(
                    id, descriptor.pluginName(), IntegrationStatus.DISABLED,
                    provider.getPluginMeta().getVersion(), "Plugin is installed but disabled");
        }
        for (String requiredClass : descriptor.requiredClasses()) {
            try {
                Class.forName(requiredClass, false, provider.getClass().getClassLoader());
            } catch (ClassNotFoundException | LinkageError exception) {
                return new IntegrationState(
                        id,
                        descriptor.pluginName(),
                        IntegrationStatus.UNAVAILABLE_MISSING_API,
                        provider.getPluginMeta().getVersion(),
                        "Missing supported public API signal: " + requiredClass);
            }
        }
        return new IntegrationState(
                id,
                descriptor.pluginName(),
                IntegrationStatus.AVAILABLE,
                provider.getPluginMeta().getVersion(),
                "Ready");
    }

    private static String normalize(String id) {
        return id.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static Map<String, Descriptor> descriptors() {
        Map<String, Descriptor> descriptors = new LinkedHashMap<>();
        descriptors.put("PLACEHOLDERAPI", new Descriptor("PlaceholderAPI", Set.of("me.clip.placeholderapi.expansion.PlaceholderExpansion")));
        descriptors.put("VAULT", new Descriptor("Vault", Set.of("net.milkbowl.vault.economy.Economy")));
        descriptors.put("LUCKPERMS", new Descriptor("LuckPerms", Set.of("net.luckperms.api.LuckPerms")));
        descriptors.put("PLEXON_RANKS", new Descriptor("PlexonRanks", Set.of(
                "com.zpkdxgames.plexonranks.api.PlexonRanksAPI",
                "com.zpkdxgames.plexonranks.event.PlexonRankupEvent")));
        descriptors.put("PLEXON_TOOLS", new Descriptor("PlexonTools", Set.of(
                "com.plexon.tools.event.PlexonToolLevelUpEvent",
                "com.plexon.tools.event.PlexonToolProgressEvent")));
        descriptors.put("PLEXON_KEYS", new Descriptor("PlexonKeys", Set.of(
                "com.antondev.keys.event.PlexonKeyEarnedEvent",
                "com.antondev.keys.event.PlexonKeyClaimedEvent")));
        descriptors.put("PLEXON_CRATES", new Descriptor("PlexonCrates", Set.of(
                "com.antondev.crates.event.PlexonCrateOpenedEvent")));
        descriptors.put("PLEXON_SHOPS", new Descriptor("PlexonShops", Set.of(
                "com.plexon.shops.event.PlexonShopVisitedEvent",
                "com.plexon.shops.event.PlexonShopRatedEvent",
                "com.plexon.shops.event.PlexonShopCreatedEvent")));
        descriptors.put("PLEXON_DAILY_REWARDS", new Descriptor("PlexonDailyRewards", Set.of(
                "com.zpkdxgames.plexondailyrewards.event.DailyRewardClaimedEvent")));
        return Map.copyOf(descriptors);
    }

    private record Descriptor(String pluginName, Set<String> requiredClasses) {}
}


package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.persistence.StoredProfile;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProfileService {
    private final JavaPlugin plugin;
    private final StorageService storage;
    private final ConfigManager configs;
    private final IntegrationManager integrations;
    private final Map<UUID, PlayerProfile> online = new ConcurrentHashMap<>();
    private volatile BiConsumer<Player, PlayerProfile> readyHandler = (player, profile) -> {};

    public ProfileService(
            JavaPlugin plugin,
            StorageService storage,
            ConfigManager configs,
            IntegrationManager integrations) {
        this.plugin = plugin;
        this.storage = storage;
        this.configs = configs;
        this.integrations = integrations;
    }

    public void readyHandler(BiConsumer<Player, PlayerProfile> handler) {
        this.readyHandler = handler;
    }

    public void load(Player player) {
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        storage.loadProfile(playerId, name).whenComplete((stored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(playerId);
                    if (current == null || !current.isOnline()) {
                        return;
                    }
                    if (failure != null) {
                        plugin.getLogger().log(Level.SEVERE, "Could not load quest profile for " + playerId, failure);
                        return;
                    }
                    PlayerProfile profile = fromStored(stored);
                    refreshRankCategory(profile);
                    online.put(playerId, profile);
                    readyHandler.accept(current, profile);
                }));
    }

    public void unload(Player player) {
        PlayerProfile profile = online.remove(player.getUniqueId());
        if (profile == null) {
            return;
        }
        storage.savePreferences(
                        profile.playerId(), profile.latestName(), profile.preferences(), profile.pinnedAssignment().orElse(null))
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not save quest preferences for " + profile.playerId(), failure);
                    return null;
                });
        storage.flushDirty();
    }

    public Optional<PlayerProfile> profile(UUID playerId) {
        return Optional.ofNullable(online.get(playerId));
    }

    public Optional<PlayerProfile> profile(Player player) {
        return profile(player.getUniqueId());
    }

    public int onlineCount() {
        return online.size();
    }

    public List<PlayerProfile> onlineProfiles() {
        return List.copyOf(online.values());
    }

    public void refreshRankCategory(PlayerProfile profile) {
        String category = integrations.rankCategory(
                profile.playerId(), configs.snapshot().settings().rankProgression().categories().keySet());
        profile.rankCategory(category);
    }

    public void persistPreferences(PlayerProfile profile) {
        storage.savePreferences(
                        profile.playerId(), profile.latestName(), profile.preferences(), profile.pinnedAssignment().orElse(null))
                .exceptionally(failure -> {
                    plugin.getLogger().log(Level.WARNING, "Could not save quest preferences for " + profile.playerId(), failure);
                    return null;
                });
    }

    private static PlayerProfile fromStored(StoredProfile stored) {
        return new PlayerProfile(
                stored.playerId(), stored.latestName(), stored.preferences(), stored.pinnedAssignment(),
                stored.completedTotal(), stored.assignments());
    }
}

package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.event.CoreBlockSubscription;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Core 2-only adapter. This class must never be loaded on Core 1.x or standalone paths. */
public final class PlexonCore2Runtime implements CoreRuntime {
    private final PlexonCoreAPI core;

    public PlexonCore2Runtime(JavaPlugin plugin) {
        RegisteredServiceProvider<PlexonCoreAPI> registration =
                Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        this.core = registration.getProvider();
        if (core.version().apiMajor() < 2) {
            throw new IllegalStateException("PlexonCore Runtime requires API 2.x");
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String detail() {
        return "PlexonCore " + core.version().pluginVersion() + " Runtime API " + core.version().apiVersion();
    }

    @Override
    public Subscription subscribeBlockBreak(
            Set<Material> materials,
            boolean requiresNaturalOrigin,
            Consumer<BlockFact> handler) {
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(handler, "handler");
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("Core block subscription requires at least one material");
        }

        CoreBlockSubscription subscription = CoreBlockSubscription.builder()
                .materials(materials)
                .requiresNaturalOrigin(requiresNaturalOrigin)
                .build();
        AutoCloseable closeable = core.events().subscribeBlockBreak(
                CoreBridge.MODULE_ID,
                subscription,
                context -> handler.accept(new BlockFact(
                        context.eventId(),
                        context.playerId(),
                        context.worldId(),
                        context.worldName(),
                        context.x(),
                        context.y(),
                        context.z(),
                        context.material(),
                        map(context.origin()),
                        context.gameTick())));
        return () -> {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Could not close PlexonCore block subscription", exception);
            }
        };
    }

    private static OriginState map(BlockOrigin origin) {
        return switch (origin) {
            case NATURAL -> OriginState.NATURAL;
            case PLAYER_PLACED -> OriginState.PLAYER_PLACED;
            case UNKNOWN -> OriginState.UNKNOWN;
        };
    }
}

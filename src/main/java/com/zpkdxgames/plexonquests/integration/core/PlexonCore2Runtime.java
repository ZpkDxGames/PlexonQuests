package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.event.CoreBlockSubscription;
import com.zpkdxgames.plexoncore.origin.BlockOriginService;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** Core 2-only adapter. This class must never be loaded on Core 1.x or standalone paths. */
public final class PlexonCore2Runtime implements CoreRuntime {
    private final PlexonCoreAPI core;
    private final boolean originImportAvailable;

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
        this.originImportAvailable = detectOriginImportApi();
    }

    private static boolean detectOriginImportApi() {
        try {
            BlockOriginService.class.getMethod(
                    "importComplete", UUID.class, int.class, int.class, String.class, int.class);
            BlockOriginService.class.getMethod(
                    "importPlayerPlacedChunk",
                    UUID.class,
                    int.class,
                    int.class,
                    String.class,
                    int.class,
                    Collection.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String detail() {
        return "PlexonCore " + core.version().pluginVersion() + " Runtime API " + core.version().apiVersion()
                + (originImportAvailable ? " + origin import" : " + legacy origin fallback");
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

    @Override
    public boolean originImportAvailable() {
        return originImportAvailable;
    }

    @Override
    public CompletableFuture<Boolean> originImportComplete(
            UUID worldId, int chunkX, int chunkZ, String source, int sourceVersion) {
        if (!originImportAvailable) {
            return CoreRuntime.super.originImportComplete(worldId, chunkX, chunkZ, source, sourceVersion);
        }
        return core.blockOrigins().importComplete(worldId, chunkX, chunkZ, source, sourceVersion);
    }

    @Override
    public CompletableFuture<Integer> importPlayerPlacedChunk(
            UUID worldId,
            int chunkX,
            int chunkZ,
            String source,
            int sourceVersion,
            Collection<BlockPosition> positions) {
        if (!originImportAvailable) {
            return CoreRuntime.super.importPlayerPlacedChunk(
                    worldId, chunkX, chunkZ, source, sourceVersion, positions);
        }
        var converted = positions.stream()
                .map(position -> new BlockOriginService.BlockPosition(position.x(), position.y(), position.z()))
                .toList();
        return core.blockOrigins().importPlayerPlacedChunk(
                worldId, chunkX, chunkZ, source, sourceVersion, converted);
    }

    private static OriginState map(BlockOrigin origin) {
        return switch (origin) {
            case NATURAL -> OriginState.NATURAL;
            case PLAYER_PLACED -> OriginState.PLAYER_PLACED;
            case UNKNOWN -> OriginState.UNKNOWN;
        };
    }
}

package com.zpkdxgames.plexonquests.integration.core;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Material;

/**
 * PlexonQuests-owned abstraction over the optional PlexonCore 2 runtime.
 *
 * <p>No PlexonCore classes appear in this interface so it remains safe to load when Core is absent
 * or when a Core 1.x compatibility installation is present.</p>
 */
public interface CoreRuntime {
    boolean available();

    String detail();

    Subscription subscribeBlockBreak(
            Set<Material> materials,
            boolean requiresNaturalOrigin,
            Consumer<BlockFact> handler);

    default boolean originImportAvailable() {
        return false;
    }

    default CompletableFuture<Boolean> originImportComplete(
            UUID worldId, int chunkX, int chunkZ, String source, int sourceVersion) {
        return CompletableFuture.failedFuture(new IllegalStateException("Core origin import is unavailable"));
    }

    default CompletableFuture<Integer> importPlayerPlacedChunk(
            UUID worldId,
            int chunkX,
            int chunkZ,
            String source,
            int sourceVersion,
            Collection<BlockPosition> positions) {
        return CompletableFuture.failedFuture(new IllegalStateException("Core origin import is unavailable"));
    }

    static CoreRuntime unavailable(String detail) {
        return new CoreRuntime() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String detail() {
                return detail == null || detail.isBlank() ? "Core Runtime is unavailable" : detail;
            }

            @Override
            public Subscription subscribeBlockBreak(
                    Set<Material> materials,
                    boolean requiresNaturalOrigin,
                    Consumer<BlockFact> handler) {
                throw new IllegalStateException(detail());
            }
        };
    }

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    record BlockFact(
            long eventId,
            UUID playerId,
            UUID worldId,
            String worldName,
            int x,
            int y,
            int z,
            Material material,
            OriginState origin,
            long gameTick) {}

    record BlockPosition(int x, int y, int z) {}

    enum OriginState {
        NATURAL,
        PLAYER_PLACED,
        UNKNOWN
    }
}

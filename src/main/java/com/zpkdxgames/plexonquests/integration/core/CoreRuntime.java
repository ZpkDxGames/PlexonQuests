package com.zpkdxgames.plexonquests.integration.core;

import java.util.Set;
import java.util.UUID;
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

    enum OriginState {
        NATURAL,
        PLAYER_PLACED,
        UNKNOWN
    }
}

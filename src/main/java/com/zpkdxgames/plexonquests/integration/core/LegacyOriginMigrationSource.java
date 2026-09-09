package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexonquests.config.BlockOriginMode;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Reads the immutable legacy PlexonQuests chunk-PDC provenance used as the Core migration source. */
final class LegacyOriginMigrationSource {
    private static final int FORMAT_VERSION = 1;
    private final ConfigManager configs;
    private final NamespacedKey placedKey;
    private final NamespacedKey sessionUnknownKey;

    LegacyOriginMigrationSource(JavaPlugin plugin, ConfigManager configs) {
        this.configs = configs;
        this.placedKey = new NamespacedKey(plugin, "player_placed_blocks_v1");
        this.sessionUnknownKey = new NamespacedKey(plugin, "origin_unknown_after_session_v1");
    }

    Snapshot snapshot(Chunk chunk) {
        BlockOriginMode mode = configs.snapshot().settings().tracking().originMode();
        if (mode == BlockOriginMode.OFF) {
            return Snapshot.known(List.of());
        }
        if (mode == BlockOriginMode.SESSION
                && chunk.getPersistentDataContainer().has(sessionUnknownKey, PersistentDataType.BYTE)) {
            return Snapshot.unknown();
        }

        byte[] encoded = chunk.getPersistentDataContainer().get(placedKey, PersistentDataType.BYTE_ARRAY);
        if (encoded == null || encoded.length == 0) {
            return Snapshot.known(List.of());
        }
        try {
            ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
            if (encoded.length < 8 || encoded.length % 4 != 0 || input.getInt() != FORMAT_VERSION) {
                return Snapshot.unknown();
            }
            int count = input.getInt();
            if (count == -1) {
                return Snapshot.unknown();
            }
            if (count < 0 || count != input.remaining() / Integer.BYTES) {
                return Snapshot.unknown();
            }

            List<CoreRuntime.BlockPosition> positions = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                long packed = Integer.toUnsignedLong(input.getInt());
                int x = (chunk.getX() << 4) + (int) (packed & 15L);
                int z = (chunk.getZ() << 4) + (int) ((packed >>> 4) & 15L);
                int y = chunk.getWorld().getMinHeight() + (int) (packed >>> 8);
                positions.add(new CoreRuntime.BlockPosition(x, y, z));
            }
            return Snapshot.known(positions);
        } catch (RuntimeException malformed) {
            return Snapshot.unknown();
        }
    }

    record Snapshot(boolean known, List<CoreRuntime.BlockPosition> positions, Set<CoreRuntime.BlockPosition> lookup) {
        static Snapshot known(List<CoreRuntime.BlockPosition> positions) {
            List<CoreRuntime.BlockPosition> immutable = List.copyOf(positions);
            return new Snapshot(true, immutable, Set.copyOf(new HashSet<>(immutable)));
        }

        static Snapshot unknown() {
            return new Snapshot(false, List.of(), Set.of());
        }

        boolean playerPlaced(Block block) {
            return known && lookup.contains(new CoreRuntime.BlockPosition(block.getX(), block.getY(), block.getZ()));
        }
    }
}

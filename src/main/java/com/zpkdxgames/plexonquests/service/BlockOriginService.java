package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.BlockOriginMode;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BlockOriginService implements Listener {
    private static final int FORMAT_VERSION = 1;

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final NamespacedKey placedKey;
    private final NamespacedKey sessionUnknownKey;
    private final Map<ChunkKey, OriginSet> chunks = new ConcurrentHashMap<>();
    private final List<BlockPosition> pendingRemovals = new ArrayList<>();
    private final AtomicBoolean removalScheduled = new AtomicBoolean();

    public BlockOriginService(JavaPlugin plugin, ConfigManager configs) {
        this.plugin = plugin;
        this.configs = configs;
        this.placedKey = new NamespacedKey(plugin, "player_placed_blocks_v1");
        this.sessionUnknownKey = new NamespacedKey(plugin, "origin_unknown_after_session_v1");
    }

    public void loadExistingChunks() {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                load(chunk);
            }
        }
    }

    public OriginResult origin(Block block) {
        if (mode() == BlockOriginMode.OFF) {
            return new OriginResult(true, true);
        }
        OriginSet set = chunks.get(ChunkKey.of(block.getChunk()));
        if (set == null || !set.known) {
            return new OriginResult(false, false);
        }
        return new OriginResult(true, !set.positions.contains(pack(block)));
    }

    public void markBroken(Block block) {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        pendingRemovals.add(BlockPosition.of(block));
        if (removalScheduled.compareAndSet(false, true)) {
            Bukkit.getScheduler().runTask(plugin, this::flushPendingRemovals);
        }
    }

    public int loadedChunkCount() {
        return chunks.size();
    }

    public long trackedPositionCount() {
        return chunks.values().stream().mapToLong(set -> set.positions.size()).sum();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        OriginSet set = set(event.getBlockPlaced().getChunk());
        if (!set.known) {
            return;
        }
        int maximum = configs.snapshot().settings().tracking().maximumOriginsPerChunk();
        if (set.positions.size() >= maximum) {
            set.known = false;
            set.positions.clear();
            plugin.getLogger().warning("Player-placed origin set exceeded its limit for chunk "
                    + event.getBlockPlaced().getChunk().getX() + "," + event.getBlockPlaced().getChunk().getZ()
                    + "; natural-only objectives now fail closed there");
            return;
        }
        set.positions.add(pack(event.getBlockPlaced()));
        set.dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        markBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        markBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::markBroken);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(block -> move(block, block.getRelative(event.getDirection())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(block -> move(block, block.getRelative(event.getDirection().getOppositeFace())));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        load(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkKey key = ChunkKey.of(event.getChunk());
        if (mode() == BlockOriginMode.SESSION) {
            chunks.remove(key);
            event.getChunk().getPersistentDataContainer().set(
                    sessionUnknownKey, PersistentDataType.BYTE, (byte) 1);
            return;
        }
        OriginSet set = chunks.remove(key);
        if (set != null) {
            save(event.getChunk(), set);
        }
    }

    public void saveAll() {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                if (mode() == BlockOriginMode.SESSION) {
                    chunk.getPersistentDataContainer().set(
                            sessionUnknownKey, PersistentDataType.BYTE, (byte) 1);
                    continue;
                }
                OriginSet set = chunks.get(ChunkKey.of(chunk));
                if (set != null) {
                    save(chunk, set);
                }
            }
        }
    }

    private void load(Chunk chunk) {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        ChunkKey key = ChunkKey.of(chunk);
        if (chunks.containsKey(key)) {
            return;
        }
        OriginSet set = new OriginSet();
        byte[] encoded = chunk.getPersistentDataContainer().get(placedKey, PersistentDataType.BYTE_ARRAY);
        if (encoded != null && encoded.length > 0) {
            try {
                set.known = decode(encoded, set.positions);
            } catch (IllegalArgumentException exception) {
                set.known = false;
                plugin.getLogger().log(
                        Level.WARNING,
                        "Invalid block-origin data in " + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ()
                                + "; natural-only tracking will fail closed",
                        exception);
            }
        }
        if (mode() == BlockOriginMode.SESSION
                && chunk.getPersistentDataContainer().has(sessionUnknownKey, PersistentDataType.BYTE)) {
            set.positions.clear();
            set.known = false;
        } else if (mode() == BlockOriginMode.PERSISTENT_CHUNK) {
            chunk.getPersistentDataContainer().remove(sessionUnknownKey);
        }
        chunks.put(key, set);
    }

    private OriginSet set(Chunk chunk) {
        ChunkKey key = ChunkKey.of(chunk);
        OriginSet existing = chunks.get(key);
        if (existing != null) {
            return existing;
        }
        load(chunk);
        return chunks.get(key);
    }

    private void save(Chunk chunk, OriginSet set) {
        if (mode() != BlockOriginMode.PERSISTENT_CHUNK || !set.dirty) {
            return;
        }
        if (!set.known) {
            chunk.getPersistentDataContainer().set(placedKey, PersistentDataType.BYTE_ARRAY, encodeUnknown());
        } else if (set.positions.isEmpty()) {
            chunk.getPersistentDataContainer().remove(placedKey);
        } else {
            chunk.getPersistentDataContainer().set(
                    placedKey, PersistentDataType.BYTE_ARRAY, encode(set.positions.toLongArray()));
        }
        set.dirty = false;
    }

    private void move(Block source, Block destination) {
        if (mode() == BlockOriginMode.OFF) {
            return;
        }
        OriginSet sourceSet = set(source.getChunk());
        OriginSet destinationSet = set(destination.getChunk());
        if (!sourceSet.known || !destinationSet.known) {
            destinationSet.known = false;
            destinationSet.positions.clear();
            destinationSet.dirty = true;
            return;
        }
        boolean playerPlaced = sourceSet.positions.remove(pack(source));
        if (playerPlaced) {
            destinationSet.positions.add(pack(destination));
        }
        sourceSet.dirty = true;
        destinationSet.dirty = true;
    }

    private void flushPendingRemovals() {
        List<BlockPosition> removals = List.copyOf(pendingRemovals);
        pendingRemovals.clear();
        removalScheduled.set(false);
        for (BlockPosition position : removals) {
            World world = Bukkit.getWorld(position.worldId());
            if (world == null) {
                continue;
            }
            int chunkX = position.x() >> 4;
            int chunkZ = position.z() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }
            Block current = world.getBlockAt(position.x(), position.y(), position.z());
            if (!current.getType().isAir()) {
                continue;
            }
            OriginSet set = chunks.get(new ChunkKey(position.worldId(), chunkX, chunkZ));
            if (set != null && set.known && set.positions.remove(pack(position.x(), position.y(), position.z(), world.getMinHeight()))) {
                set.dirty = true;
            }
        }
    }

    private BlockOriginMode mode() {
        return configs.snapshot().settings().tracking().originMode();
    }

    private static long pack(Block block) {
        return pack(block.getX(), block.getY(), block.getZ(), block.getWorld().getMinHeight());
    }

    private static long pack(int x, int y, int z, int minimumHeight) {
        long normalizedY = (long) y - minimumHeight;
        if (normalizedY < 0L || normalizedY > 0x00ff_ffffL) {
            throw new IllegalArgumentException("Block Y is outside the supported world range");
        }
        return (normalizedY << 8) | ((z & 15L) << 4) | (x & 15L);
    }

    private static byte[] encode(long[] positions) {
        Arrays.sort(positions);
        ByteBuffer output = ByteBuffer.allocate(8 + positions.length * Integer.BYTES).order(ByteOrder.BIG_ENDIAN);
        output.putInt(FORMAT_VERSION);
        output.putInt(positions.length);
        for (long position : positions) {
            if (position > 0xffff_ffffL) {
                throw new IllegalArgumentException("Packed block origin is too large");
            }
            output.putInt((int) position);
        }
        return output.array();
    }

    private static byte[] encodeUnknown() {
        return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(FORMAT_VERSION).putInt(-1).array();
    }

    private static boolean decode(byte[] encoded, LongOpenHashSet output) {
        if (encoded.length < 8 || encoded.length % 4 != 0) {
            throw new IllegalArgumentException("Invalid encoded length");
        }
        ByteBuffer input = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        if (input.getInt() != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported block-origin format");
        }
        int count = input.getInt();
        if (count == -1) {
            return false;
        }
        if (count < -1) {
            throw new IllegalArgumentException("Invalid block-origin count");
        }
        if (count != input.remaining() / Integer.BYTES) {
            throw new IllegalArgumentException("Block-origin count does not match its payload");
        }
        for (int index = 0; index < count; index++) {
            output.add(Integer.toUnsignedLong(input.getInt()));
        }
        return true;
    }

    public record OriginResult(boolean known, boolean natural) {}

    private static final class OriginSet {
        private final LongOpenHashSet positions = new LongOpenHashSet();
        private boolean known = true;
        private boolean dirty;
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        private static ChunkKey of(Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }

    private record BlockPosition(UUID worldId, int x, int y, int z) {
        private static BlockPosition of(Block block) {
            return new BlockPosition(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }
}

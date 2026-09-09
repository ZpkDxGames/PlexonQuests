package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexonquests.objective.block.BlockObjectiveProcessor;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Lazily imports legacy PlexonQuests chunk provenance into Core 2.0.1+.
 * Until Core confirms a persisted marker, origin classification combines the frozen legacy source
 * with the Core event-time fact so migration cannot create a natural-block exploit window.
 */
public final class CoreOriginMigrator implements Listener, AutoCloseable {
    static final String SOURCE = "plexonquests";
    static final int SOURCE_VERSION = 1;
    private static final long RETRY_TICKS = 100L;

    private final JavaPlugin plugin;
    private final CoreRuntime runtime;
    private final LegacyOriginMigrationSource source;
    private final Map<ChunkKey, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final AtomicLong imports = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong unknown = new AtomicLong();

    public CoreOriginMigrator(JavaPlugin plugin, CoreRuntime runtime, LegacyOriginMigrationSource source) {
        this.plugin = plugin;
        this.runtime = runtime;
        this.source = source;
    }

    public BlockObjectiveProcessor.OriginState resolve(Block block, CoreRuntime.BlockFact fact) {
        if (!runtime.originImportAvailable()) {
            return map(fact);
        }
        Chunk chunk = block.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        Entry entry = entries.computeIfAbsent(key, ignored -> begin(chunk, key));
        if (entry.status == Status.FAILED && Bukkit.getCurrentTick() >= entry.retryAtTick) {
            retry(chunk, key, entry);
        }
        return switch (entry.status) {
            case COMPLETE -> map(fact);
            case UNKNOWN -> BlockObjectiveProcessor.OriginState.UNKNOWN;
            case CHECKING, IMPORTING, FAILED -> combined(entry.snapshot, block, fact);
        };
    }

    private Entry begin(Chunk chunk, ChunkKey key) {
        LegacyOriginMigrationSource.Snapshot snapshot = source.snapshot(chunk);
        if (!snapshot.known()) {
            unknown.incrementAndGet();
            return new Entry(snapshot, Status.UNKNOWN, Long.MAX_VALUE);
        }
        Entry entry = new Entry(snapshot, Status.CHECKING, 0L);
        checkMarker(key, entry);
        return entry;
    }

    private void retry(Chunk chunk, ChunkKey key, Entry entry) {
        LegacyOriginMigrationSource.Snapshot refreshed = source.snapshot(chunk);
        entry.snapshot = refreshed;
        if (!refreshed.known()) {
            entry.status = Status.UNKNOWN;
            entry.retryAtTick = Long.MAX_VALUE;
            unknown.incrementAndGet();
            return;
        }
        entry.status = Status.CHECKING;
        entry.retryAtTick = 0L;
        checkMarker(key, entry);
    }

    private void checkMarker(ChunkKey key, Entry entry) {
        checks.incrementAndGet();
        runtime.originImportComplete(key.worldId, key.chunkX, key.chunkZ, SOURCE, SOURCE_VERSION)
                .whenComplete((alreadyComplete, error) -> sync(() -> {
                    if (!isCurrent(key, entry)) return;
                    if (error != null) {
                        fail(entry, "Core origin import marker check failed", error);
                        return;
                    }
                    if (Boolean.TRUE.equals(alreadyComplete)) {
                        entry.status = Status.COMPLETE;
                        completed.incrementAndGet();
                        return;
                    }
                    importChunk(key, entry);
                }));
    }

    private void importChunk(ChunkKey key, Entry entry) {
        entry.status = Status.IMPORTING;
        imports.incrementAndGet();
        runtime.importPlayerPlacedChunk(
                        key.worldId,
                        key.chunkX,
                        key.chunkZ,
                        SOURCE,
                        SOURCE_VERSION,
                        entry.snapshot.positions())
                .whenComplete((count, error) -> sync(() -> {
                    if (!isCurrent(key, entry)) return;
                    if (error != null) {
                        fail(entry, "Core origin import failed", error);
                        return;
                    }
                    entry.status = Status.COMPLETE;
                    completed.incrementAndGet();
                }));
    }

    private void fail(Entry entry, String message, Throwable error) {
        entry.status = Status.FAILED;
        entry.retryAtTick = Bukkit.getCurrentTick() + RETRY_TICKS;
        failed.incrementAndGet();
        plugin.getLogger().warning(message + "; legacy provenance remains authoritative for this chunk until retry: "
                + root(error).getMessage());
    }

    private boolean isCurrent(ChunkKey key, Entry entry) {
        return entries.get(key) == entry;
    }

    private void sync(Runnable action) {
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static BlockObjectiveProcessor.OriginState combined(
            LegacyOriginMigrationSource.Snapshot snapshot,
            Block block,
            CoreRuntime.BlockFact fact) {
        if (!snapshot.known()) return BlockObjectiveProcessor.OriginState.UNKNOWN;
        if (snapshot.playerPlaced(block)) return BlockObjectiveProcessor.OriginState.PLAYER_PLACED;
        BlockObjectiveProcessor.OriginState core = map(fact);
        if (core == BlockObjectiveProcessor.OriginState.PLAYER_PLACED) {
            return core;
        }
        if (core == BlockObjectiveProcessor.OriginState.UNKNOWN) {
            return core;
        }
        return BlockObjectiveProcessor.OriginState.NATURAL;
    }

    private static BlockObjectiveProcessor.OriginState map(CoreRuntime.BlockFact fact) {
        if (fact == null) return BlockObjectiveProcessor.OriginState.UNKNOWN;
        return switch (fact.origin()) {
            case NATURAL -> BlockObjectiveProcessor.OriginState.NATURAL;
            case PLAYER_PLACED -> BlockObjectiveProcessor.OriginState.PLAYER_PLACED;
            case UNKNOWN -> BlockObjectiveProcessor.OriginState.UNKNOWN;
        };
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        entries.remove(new ChunkKey(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ()));
    }

    public Diagnostics diagnostics() {
        long pending = entries.values().stream()
                .filter(entry -> entry.status == Status.CHECKING || entry.status == Status.IMPORTING)
                .count();
        long unknownChunks = entries.values().stream().filter(entry -> entry.status == Status.UNKNOWN).count();
        return new Diagnostics(entries.size(), pending, checks.get(), imports.get(), completed.get(), failed.get(),
                Math.max(unknown.get(), unknownChunks));
    }

    @Override
    public void close() {
        entries.clear();
    }

    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private enum Status {
        CHECKING,
        IMPORTING,
        COMPLETE,
        UNKNOWN,
        FAILED
    }

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}

    private static final class Entry {
        private volatile LegacyOriginMigrationSource.Snapshot snapshot;
        private volatile Status status;
        private volatile long retryAtTick;

        private Entry(LegacyOriginMigrationSource.Snapshot snapshot, Status status, long retryAtTick) {
            this.snapshot = snapshot;
            this.status = status;
            this.retryAtTick = retryAtTick;
        }
    }

    public record Diagnostics(
            long trackedChunks,
            long pending,
            long markerChecks,
            long importsStarted,
            long completed,
            long failures,
            long unknownChunks) {}
}

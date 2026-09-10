package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.event.QuestClaimedEvent;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.persistence.HistoryEntry;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Bounded asynchronous completion cache. GUI/PAPI/objective paths never synchronously query SQLite history. */
public final class CompletionHistoryCache implements Listener, AutoCloseable {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_HISTORY_ROWS = 4_000;

    private final JavaPlugin plugin;
    private final StorageService storage;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong failedLoads = new AtomicLong();
    private final AtomicLong staleResults = new AtomicLong();

    public CompletionHistoryCache(JavaPlugin plugin, StorageService storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> load(Player player, PlayerProfile profile) {
        Set<String> seed = profile.assignments().stream()
                .filter(assignment -> assignment.state() == AssignmentState.COMPLETED
                        || assignment.state() == AssignmentState.CLAIMING
                        || assignment.state() == AssignmentState.CLAIMED)
                .map(assignment -> assignment.definition().id())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return load(player.getUniqueId(), seed);
    }

    public CompletableFuture<Void> load(Player player) {
        return load(player.getUniqueId(), Set.of());
    }

    CompletableFuture<Void> load(UUID playerId, Set<String> seed) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("completion history cache is closed"));
        }
        Entry existing = entries.get(playerId);
        if (existing != null) {
            existing.seed(seed);
            return existing.future();
        }
        Entry created = new Entry(seed);
        existing = entries.putIfAbsent(playerId, created);
        if (existing != null) {
            existing.seed(seed);
            return existing.future();
        }
        loadPage(playerId, 0, created);
        return created.future();
    }

    public CompletableFuture<Void> reload(Player player, PlayerProfile profile) {
        invalidate(player.getUniqueId());
        return load(player, profile);
    }

    private void loadPage(UUID playerId, int offset, Entry entry) {
        int request = Math.min(PAGE_SIZE, MAX_HISTORY_ROWS - offset);
        if (request <= 0) {
            finish(playerId, entry, true);
            return;
        }
        storage.history(playerId, request, offset).whenComplete((historyRows, failure) -> {
            if (entries.get(playerId) != entry || closed.get()) {
                staleResults.incrementAndGet();
                entry.completeStale();
                return;
            }
            if (failure != null) {
                failedLoads.incrementAndGet();
                entries.remove(playerId, entry);
                entry.fail(failure);
                var logger = plugin.getLogger();
                if (logger != null) {
                    logger.log(Level.WARNING, "Could not warm quest completion history for " + playerId, failure);
                }
                return;
            }
            List<HistoryEntry> rows = historyRows == null ? List.of() : historyRows;
            entry.add(rows);
            if (rows.size() < request) {
                finish(playerId, entry, false);
                return;
            }
            int nextOffset = offset + rows.size();
            if (nextOffset >= MAX_HISTORY_ROWS) {
                finish(playerId, entry, true);
                plugin.getLogger().warning("Quest completion history cache reached the bounded "
                        + MAX_HISTORY_ROWS + "-row limit for " + playerId + "; older prerequisite history is conservative.");
                return;
            }
            loadPage(playerId, nextOffset, entry);
        });
    }

    private void finish(UUID playerId, Entry entry, boolean truncated) {
        if (entries.get(playerId) != entry || closed.get()) {
            staleResults.incrementAndGet();
            entry.completeStale();
            return;
        }
        entry.finish(truncated);
    }

    public boolean loaded(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.loaded();
    }

    public boolean truncated(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.truncated();
    }

    public boolean completed(UUID playerId, String questId) {
        Entry entry = entries.get(playerId);
        return entry != null && entry.loaded() && entry.contains(questId);
    }

    public Set<String> completedQuestIds(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry == null || !entry.loaded() ? Set.of() : entry.snapshot();
    }

    public void invalidate(UUID playerId) {
        Entry removed = entries.remove(playerId);
        if (removed != null) {
            removed.completeStale();
        }
    }

    public void invalidateAll() {
        entries.keySet().forEach(this::invalidate);
    }

    public Diagnostics diagnostics() {
        int loaded = 0;
        int loading = 0;
        int truncated = 0;
        for (Entry entry : entries.values()) {
            if (entry.loaded()) loaded++; else loading++;
            if (entry.truncated()) truncated++;
        }
        return new Diagnostics(entries.size(), loaded, loading, truncated, failedLoads.get(), staleResults.get());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onComplete(QuestCompleteEvent event) {
        markCompleted(event.getPlayer().getUniqueId(), event.questId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimed(QuestClaimedEvent event) {
        markCompleted(event.getPlayer().getUniqueId(), event.questId());
    }

    void markCompleted(UUID playerId, String questId) {
        Entry entry = entries.get(playerId);
        if (entry != null) {
            entry.completed(questId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        closed.set(true);
        invalidateAll();
    }

    public record Diagnostics(int entries, int loaded, int loading, int truncated, long failedLoads, long staleResults) {}

    private static final class Entry {
        private final Set<String> completed = new LinkedHashSet<>();
        private final CompletableFuture<Void> future = new CompletableFuture<>();
        private boolean loaded;
        private boolean truncated;

        private Entry(Set<String> seed) {
            seed(seed);
        }

        private synchronized void seed(Set<String> seed) {
            completed.addAll(seed);
        }

        private synchronized void add(List<HistoryEntry> history) {
            for (HistoryEntry row : history) {
                if (row.state() == AssignmentState.CLAIMED) {
                    completed.add(row.questId());
                }
            }
        }

        private synchronized void completed(String questId) {
            completed.add(questId);
        }

        private synchronized void finish(boolean truncated) {
            this.truncated = truncated;
            this.loaded = true;
            future.complete(null);
        }

        private synchronized void fail(Throwable failure) {
            future.completeExceptionally(failure);
        }

        private synchronized void completeStale() {
            future.complete(null);
        }

        private CompletableFuture<Void> future() { return future; }
        private synchronized boolean loaded() { return loaded; }
        private synchronized boolean truncated() { return truncated; }
        private synchronized boolean contains(String questId) { return completed.contains(questId); }
        private synchronized Set<String> snapshot() { return Set.copyOf(completed); }
    }
}

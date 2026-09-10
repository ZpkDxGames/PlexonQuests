package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.event.QuestClaimedEvent;
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
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bounded asynchronous history cache used by prerequisite and journal state resolution.
 * No GUI, placeholder or objective hot path performs a synchronous SQLite lookup.
 */
public final class CompletionHistoryCache implements Listener {
    private static final int PAGE_SIZE = 256;
    private static final int MAX_HISTORY_ROWS = 4096;

    private final JavaPlugin plugin;
    private final StorageService storage;
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public CompletionHistoryCache(JavaPlugin plugin, StorageService storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public CompletableFuture<Void> load(Player player) {
        UUID playerId = player.getUniqueId();
        Entry entry = new Entry();
        entries.put(playerId, entry);
        CompletableFuture<Void> result = new CompletableFuture<>();
        loadPage(playerId, 0, entry, result);
        return result;
    }

    private void loadPage(UUID playerId, int offset, Entry entry, CompletableFuture<Void> result) {
        int request = Math.min(PAGE_SIZE, MAX_HISTORY_ROWS - offset);
        if (request <= 0) {
            entry.finish(true);
            result.complete(null);
            return;
        }
        storage.history(playerId, request, offset).whenComplete((history, failure) -> {
            if (failure != null) {
                entries.remove(playerId, entry);
                plugin.getLogger().log(Level.WARNING, "Could not warm quest completion history for " + playerId, failure);
                result.completeExceptionally(failure);
                return;
            }
            entry.add(history);
            if (history.size() < request) {
                entry.finish(false);
                result.complete(null);
                return;
            }
            int nextOffset = offset + history.size();
            if (nextOffset >= MAX_HISTORY_ROWS) {
                entry.finish(true);
                plugin.getLogger().warning("Quest completion history cache reached the bounded "
                        + MAX_HISTORY_ROWS + "-row limit for " + playerId + "; older prerequisite history is conservative.");
                result.complete(null);
                return;
            }
            loadPage(playerId, nextOffset, entry, result);
        });
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClaimed(QuestClaimedEvent event) {
        Entry entry = entries.get(event.getPlayer().getUniqueId());
        if (entry != null) {
            entry.claimed(event.questId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        entries.remove(event.getPlayer().getUniqueId());
    }

    private static final class Entry {
        private final Set<String> completed = new LinkedHashSet<>();
        private boolean loaded;
        private boolean truncated;

        private synchronized void add(List<HistoryEntry> history) {
            for (HistoryEntry entry : history) {
                if (entry.state() == AssignmentState.CLAIMED) {
                    completed.add(entry.questId());
                }
            }
        }

        private synchronized void claimed(String questId) {
            completed.add(questId);
        }

        private synchronized void finish(boolean truncated) {
            this.truncated = truncated;
            this.loaded = true;
        }

        private synchronized boolean loaded() {
            return loaded;
        }

        private synchronized boolean truncated() {
            return truncated;
        }

        private synchronized boolean contains(String questId) {
            return completed.contains(questId);
        }

        private synchronized Set<String> snapshot() {
            return Set.copyOf(completed);
        }
    }
}

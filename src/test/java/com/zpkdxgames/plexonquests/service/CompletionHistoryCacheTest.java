package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.persistence.HistoryEntry;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class CompletionHistoryCacheTest {
    @Test
    void coldLoadUsesStorageCapAndRepeatedRequestSharesInFlightFuture() {
        StorageService storage = mock(StorageService.class);
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(storage.history(org.mockito.ArgumentMatchers.any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        CompletionHistoryCache cache = new CompletionHistoryCache(plugin, storage);
        UUID id = UUID.randomUUID();

        CompletableFuture<Void> first = cache.load(id, Set.of("seed"));
        CompletableFuture<Void> second = cache.load(id, Set.of());

        assertSame(first, second);
        assertTrue(first.isDone());
        assertTrue(cache.loaded(id));
        assertTrue(cache.completed(id, "seed"));
        verify(storage, times(1)).history(eq(id), eq(100), eq(0));
    }

    @Test
    void completionUpdatesLoadedCacheWithoutAnotherDatabaseRead() {
        StorageService storage = mock(StorageService.class);
        when(storage.history(org.mockito.ArgumentMatchers.any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        CompletionHistoryCache cache = new CompletionHistoryCache(mock(JavaPlugin.class), storage);
        UUID id = UUID.randomUUID();
        cache.load(id, Set.of()).join();

        cache.markCompleted(id, "prerequisite");

        assertTrue(cache.completed(id, "prerequisite"));
        verify(storage, times(1)).history(eq(id), eq(100), eq(0));
    }

    @Test
    void invalidationRejectsLateAsyncResultAndDoesNotResurrectEntry() {
        StorageService storage = mock(StorageService.class);
        CompletableFuture<List<HistoryEntry>> pending = new CompletableFuture<>();
        when(storage.history(org.mockito.ArgumentMatchers.any(), anyInt(), anyInt())).thenReturn(pending);
        CompletionHistoryCache cache = new CompletionHistoryCache(mock(JavaPlugin.class), storage);
        UUID id = UUID.randomUUID();
        CompletableFuture<Void> load = cache.load(id, Set.of());

        cache.invalidate(id);
        pending.complete(List.of());

        assertTrue(load.isDone());
        assertFalse(cache.loaded(id));
        assertTrue(cache.diagnostics().staleResults() >= 1);
    }

    @Test
    void databaseFailureFailsClosedAndIsObservable() {
        StorageService storage = mock(StorageService.class);
        when(storage.history(org.mockito.ArgumentMatchers.any(), anyInt(), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db")));
        CompletionHistoryCache cache = new CompletionHistoryCache(mock(JavaPlugin.class), storage);
        UUID id = UUID.randomUUID();

        assertTrue(cache.load(id, Set.of()).isCompletedExceptionally());
        assertFalse(cache.loaded(id));
        assertTrue(cache.diagnostics().failedLoads() >= 1);
    }
}

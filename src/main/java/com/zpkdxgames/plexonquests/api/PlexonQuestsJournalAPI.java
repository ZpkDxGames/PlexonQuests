package com.zpkdxgames.plexonquests.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Additive Phase 2 journal API. Obtain from Bukkit ServicesManager. */
public interface PlexonQuestsJournalAPI {
    CompletableFuture<List<JournalQuestView>> journal(UUID playerId);

    CompletableFuture<Optional<JournalQuestView>> quest(UUID playerId, String questId);

    CompletableFuture<Optional<JournalQuestView>> tracked(UUID playerId);

    Map<String, Set<String>> prerequisiteGraph();
}

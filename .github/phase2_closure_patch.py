from pathlib import Path


def replace(path, old, new, count=1):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"anchor missing in {path}: {old[:100]!r}")
    text = text.replace(old, new, count)
    p.write_text(text)


def write(path, content):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)


# Make the prerequisite graph part of the same immutable candidate snapshot.
write("src/main/java/com/zpkdxgames/plexonquests/config/ConfigSnapshot.java", r'''package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import java.time.Instant;

public record ConfigSnapshot(
        PluginSettings settings,
        QuestRegistrySnapshot registry,
        FlatConfiguration messages,
        FlatConfiguration menus,
        FlatConfiguration effects,
        QuestPrerequisiteService.Snapshot prerequisiteGraph,
        Instant loadedAt) {}
''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    "    private final AtomicReference<ConfigSnapshot> active = new AtomicReference<>();\n",
    "    private final AtomicReference<ConfigSnapshot> active = new AtomicReference<>();\n"
    "    private final AtomicReference<List<String>> lastActivationErrors = new AtomicReference<>(List.of());\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''        if (!candidate.activationErrors().isEmpty()) {\n            throw new InvalidConfigurationException(String.join("; ", candidate.activationErrors()));\n        }\n        active.set(candidate.snapshot());\n''',
    '''        if (!candidate.activationErrors().isEmpty()) {\n            lastActivationErrors.set(candidate.activationErrors());\n            throw new InvalidConfigurationException(String.join("; ", candidate.activationErrors()));\n        }\n        active.set(candidate.snapshot());\n        lastActivationErrors.set(List.of());\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''                if (!candidate.activationErrors().isEmpty()) {\n                    return new ReloadResult(false, active.get(), candidate.snapshot().registry().issues(), candidate.activationErrors());\n                }\n                active.set(candidate.snapshot());\n                return new ReloadResult(true, candidate.snapshot(), candidate.snapshot().registry().issues(), List.of());\n            } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {\n                return new ReloadResult(\n                        false,\n                        active.get(),\n                        active.get() == null ? List.of() : active.get().registry().issues(),\n                        List.of(Objects.requireNonNullElse(exception.getMessage(), exception.getClass().getSimpleName())));\n            }\n''',
    '''                if (!candidate.activationErrors().isEmpty()) {\n                    lastActivationErrors.set(candidate.activationErrors());\n                    return new ReloadResult(false, active.get(), candidate.snapshot().registry().issues(), candidate.activationErrors());\n                }\n                active.set(candidate.snapshot());\n                lastActivationErrors.set(List.of());\n                return new ReloadResult(true, candidate.snapshot(), candidate.snapshot().registry().issues(), List.of());\n            } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {\n                List<String> errors = List.of(Objects.requireNonNullElse(\n                        exception.getMessage(), exception.getClass().getSimpleName()));\n                lastActivationErrors.set(errors);\n                return new ReloadResult(\n                        false,\n                        active.get(),\n                        active.get() == null ? List.of() : active.get().registry().issues(),\n                        errors);\n            }\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''    public Path dataDirectory() {\n        return dataDirectory;\n    }\n''',
    '''    public Path dataDirectory() {\n        return dataDirectory;\n    }\n\n    public List<String> lastActivationErrors() {\n        return lastActivationErrors.get();\n    }\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/config/ConfigManager.java",
    '''                FlatConfiguration.from(messages),\n                FlatConfiguration.from(menus),\n                FlatConfiguration.from(effects),\n                Instant.now());\n''',
    '''                FlatConfiguration.from(messages),\n                FlatConfiguration.from(menus),\n                FlatConfiguration.from(effects),\n                prerequisiteGraph.snapshot(),\n                Instant.now());\n''')

replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteService.java",
    "import java.util.concurrent.atomic.AtomicReference;\n",
    "")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteService.java",
    '''    private final ConfigManager configs;\n    private final AtomicReference<Snapshot> active = new AtomicReference<>(Snapshot.empty());\n''',
    '''    private final ConfigManager configs;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteService.java",
    '''    public ValidationResult reloadValidated() {\n        ValidationResult result = validate(configs.dataDirectory());\n        if (result.valid()) {\n            active.set(result.snapshot());\n        }\n        return result;\n    }\n''',
    '''    public ValidationResult reloadValidated() {\n        return new ValidationResult(true, snapshot(), List.of());\n    }\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteService.java",
    '''    public Snapshot snapshot() {\n        return active.get();\n    }\n''',
    '''    public Snapshot snapshot() {\n        return configs.snapshot().prerequisiteGraph();\n    }\n''')

# Diagnostics can report schema version without a synchronous database read.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/persistence/StorageService.java",
    "public final class StorageService implements AutoCloseable {\n",
    "public final class StorageService implements AutoCloseable {\n    public static final int CURRENT_SCHEMA_VERSION = 2;\n")

# Central tracking authority for all player-initiated GUI/command changes. Internal cleanup bypasses player permissions.
write("src/main/java/com/zpkdxgames/plexonquests/service/QuestTrackingService.java", r'''package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.event.QuestCompleteEvent;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.event.QuestTrackEvent;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Single authority for player-facing quest tracking and permission-independent lifecycle cleanup. */
public final class QuestTrackingService implements Listener {
    private final ConfigManager configs;
    private final ProfileService profiles;

    public QuestTrackingService(ConfigManager configs, ProfileService profiles) {
        this.configs = Objects.requireNonNull(configs, "configs");
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    public Result toggle(Player player, UUID assignmentId) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);
        if (assignment == null) {
            return Result.NOT_FOUND;
        }
        if (profile.pinnedAssignment().filter(assignmentId::equals).isPresent()) {
            return clear(player, profile, assignment) ? Result.UNTRACKED : Result.UNCHANGED;
        }
        if (assignment.state() != AssignmentState.ACTIVE) {
            return Result.NOT_ACTIVE;
        }
        set(player, profile, assignment);
        return Result.TRACKED;
    }

    public Result track(Player player, String token) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        QuestAssignment assignment = resolveActive(profile, token);
        if (assignment == null) {
            return Result.NOT_FOUND;
        }
        if (profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()) {
            return Result.UNCHANGED;
        }
        set(player, profile, assignment);
        return Result.TRACKED;
    }

    public Result untrack(Player player) {
        requirePrimary();
        if (!player.hasPermission("plexonquests.pin")) {
            return Result.NO_PERMISSION;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return Result.NOT_FOUND;
        }
        UUID trackedId = profile.pinnedAssignment().orElse(null);
        if (trackedId == null) {
            return Result.UNCHANGED;
        }
        QuestAssignment assignment = profile.assignment(trackedId).orElse(null);
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return Result.UNTRACKED;
    }

    public boolean reconcile(Player player, PlayerProfile profile) {
        requirePrimary();
        UUID trackedId = profile.pinnedAssignment().orElse(null);
        if (trackedId == null) {
            return false;
        }
        QuestAssignment assignment = profile.assignment(trackedId).orElse(null);
        boolean valid = assignment != null && assignment.state() == AssignmentState.ACTIVE;
        if (valid) {
            var currentDefinition = configs.snapshot().registry().quests().get(assignment.definition().id());
            valid = currentDefinition != null && currentDefinition.enabled();
        }
        if (valid) {
            return false;
        }
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return true;
    }

    public int trackedOnlineCount() {
        int count = 0;
        for (PlayerProfile profile : profiles.onlineProfiles()) {
            UUID trackedId = profile.pinnedAssignment().orElse(null);
            QuestAssignment assignment = trackedId == null ? null : profile.assignment(trackedId).orElse(null);
            if (assignment != null && assignment.state() == AssignmentState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onComplete(QuestCompleteEvent event) {
        profiles.profile(event.getPlayer()).ifPresent(profile -> clearIfMatches(event.getPlayer(), profile, event.assignmentId()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onExpire(QuestExpireEvent event) {
        profiles.profile(event.getPlayer()).ifPresent(profile -> clearIfMatches(event.getPlayer(), profile, event.assignmentId()));
    }

    private boolean clearIfMatches(Player player, PlayerProfile profile, UUID assignmentId) {
        if (profile.pinnedAssignment().filter(assignmentId::equals).isEmpty()) {
            return false;
        }
        QuestAssignment assignment = profile.assignment(assignmentId).orElse(null);
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        if (assignment != null) {
            Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                    player, assignment.id(), assignment.definition().id(), false));
        }
        return true;
    }

    private boolean clear(Player player, PlayerProfile profile, QuestAssignment assignment) {
        if (profile.pinnedAssignment().filter(assignment.id()::equals).isEmpty()) {
            return false;
        }
        profile.pinnedAssignment(null);
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), false));
        return true;
    }

    private void set(Player player, PlayerProfile profile, QuestAssignment assignment) {
        UUID previousId = profile.pinnedAssignment().orElse(null);
        if (previousId != null && !previousId.equals(assignment.id())) {
            profile.assignment(previousId).ifPresent(previous -> Bukkit.getPluginManager().callEvent(
                    new QuestTrackEvent(player, previous.id(), previous.definition().id(), false)));
        }
        profile.pinnedAssignment(assignment.id());
        profiles.persistPreferences(profile);
        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(
                player, assignment.id(), assignment.definition().id(), true));
    }

    private static QuestAssignment resolveActive(PlayerProfile profile, String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String lowered = token.trim().toLowerCase(Locale.ROOT);
        List<QuestAssignment> matches = profile.visibleAssignments().stream()
                .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)
                .filter(assignment -> assignment.id().toString().startsWith(lowered)
                        || assignment.definition().id().equals(lowered))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static void requirePrimary() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Quest tracking mutations must run on the primary thread");
        }
    }

    public enum Result { TRACKED, UNTRACKED, UNCHANGED, NO_PERMISSION, NOT_FOUND, NOT_ACTIVE }
}
''')

# Completion history: match StorageService's 100-row query cap, dedupe loads, reject stale callbacks, seed current completions.
write("src/main/java/com/zpkdxgames/plexonquests/service/CompletionHistoryCache.java", r'''package com.zpkdxgames.plexonquests.service;

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
                plugin.getLogger().log(Level.WARNING, "Could not warm quest completion history for " + playerId, failure);
                entry.fail(failure);
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
''')

# State precedence: disabled and authoritative assignment state win; historical terminal completion wins before new eligibility gates.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/service/JournalStateResolver.java",
    '''        Set<String> required = prerequisites.prerequisites(definition.id());\n        if (!required.isEmpty()) {\n            UUID playerId = player.getUniqueId();\n            if (!completionHistory.loaded(playerId)) {\n                return JournalState.LOCKED;\n            }\n            for (String requiredQuest : required) {\n                if (!completionHistory.completed(playerId, requiredQuest)) {\n                    return JournalState.LOCKED;\n                }\n            }\n        }\n\n        if (!eligibility.evaluate(player, profile, definition).eligible()) {\n            return JournalState.LOCKED;\n        }\n        if (!definition.scope().rotating() && completionHistory.completed(player.getUniqueId(), definition.id())) {\n            return JournalState.COMPLETED;\n        }\n''',
    '''        UUID playerId = player.getUniqueId();\n        if (!definition.scope().rotating()) {\n            if (!completionHistory.loaded(playerId)) {\n                return JournalState.LOCKED;\n            }\n            if (completionHistory.completed(playerId, definition.id())) {\n                return JournalState.COMPLETED;\n            }\n        }\n\n        Set<String> required = prerequisites.prerequisites(definition.id());\n        if (!required.isEmpty()) {\n            if (!completionHistory.loaded(playerId)) {\n                return JournalState.LOCKED;\n            }\n            for (String requiredQuest : required) {\n                if (!completionHistory.completed(playerId, requiredQuest)) {\n                    return JournalState.LOCKED;\n                }\n            }\n        }\n\n        if (!eligibility.evaluate(player, profile, definition).eligible()) {\n            return JournalState.LOCKED;\n        }\n''')

# Diagnostics layer adds Phase 2 product state while the existing command/runtime layers retain persistence/Core details.
write("src/main/java/com/zpkdxgames/plexonquests/command/Phase2Diagnostics.java", r'''package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class Phase2Diagnostics {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final StorageService storage;
    private final QuestPrerequisiteService prerequisites;
    private final CompletionHistoryCache history;
    private final QuestTrackingService tracking;
    private final TextService text;

    public Phase2Diagnostics(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            StorageService storage,
            QuestPrerequisiteService prerequisites,
            CompletionHistoryCache history,
            QuestTrackingService tracking,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.storage = storage;
        this.prerequisites = prerequisites;
        this.history = history;
        this.tracking = tracking;
        this.text = text;
    }

    public void append(CommandSender sender) {
        var snapshot = configs.snapshot();
        long enabled = snapshot.registry().quests().values().stream().filter(q -> q.enabled()).count();
        long disabled = snapshot.registry().quests().size() - enabled;
        Set<String> objectiveTypes = snapshot.registry().quests().values().stream()
                .flatMap(q -> q.objectives().values().stream())
                .map(o -> o.type().name())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        Set<String> rewardTypes = snapshot.registry().quests().values().stream()
                .flatMap(q -> q.rewards().entries().stream())
                .map(r -> r.type().name())
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        CompletionHistoryCache.Diagnostics cache = history.diagnostics();
        var persistence = storage.diagnostics();
        List<String> lines = List.of(
                "<gray>Candidate version <white>" + plugin.getPluginMeta().getVersion(),
                "<gray>Loaded quests enabled/disabled <white>" + snapshot.registry().quests().size()
                        + " <dark_gray>(</dark_gray><green>" + enabled + "</green><dark_gray>/</dark_gray><red>" + disabled + "</red><dark_gray>)",
                "<gray>Prerequisite graph <green>VALID <dark_gray>• <white>" + prerequisites.snapshot().questCount() + " quest(s)",
                "<gray>Completion cache entries loaded/loading/truncated <white>" + cache.entries()
                        + "<dark_gray>/</dark_gray><white>" + cache.loaded() + "<dark_gray>/</dark_gray><white>" + cache.loading()
                        + "<dark_gray>/</dark_gray><white>" + cache.truncated(),
                "<gray>Completion cache failed/stale results <white>" + cache.failedLoads()
                        + "<dark_gray>/</dark_gray><white>" + cache.staleResults(),
                "<gray>Tracked online quests <white>" + tracking.trackedOnlineCount()
                        + " <dark_gray>across " + profiles.onlineCount() + " loaded profile(s)",
                "<gray>Persistence <white>" + (persistence.open() ? "OPEN" : "CLOSED")
                        + " <dark_gray>schema v" + StorageService.CURRENT_SCHEMA_VERSION,
                "<gray>Pending persistence queue/dirty <white>" + persistence.queueDepth()
                        + "<dark_gray>/</dark_gray><white>" + persistence.dirtyAssignments(),
                "<gray>Configured objective types <white>" + compact(objectiveTypes),
                "<gray>Configured reward types <white>" + compact(rewardTypes),
                "<gray>PlaceholderAPI <white>" + (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI") ? "ENABLED" : "ABSENT"),
                "<gray>Registry validation errors <white>" + snapshot.registry().errorCount(),
                "<gray>Last rejected configuration errors <white>" + configs.lastActivationErrors().size());
        lines.forEach(line -> sender.sendMessage(text.parse(line)));
        configs.lastActivationErrors().stream().limit(5)
                .forEach(error -> sender.sendMessage(text.parse("<red>Rejected config: <white>" + safe(error))));
    }

    private static String compact(Set<String> values) {
        if (values.isEmpty()) return "none";
        return values.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(", "));
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace("<", "\\<").replace(">", "\\>");
    }
}
''')

# Phase 2 command: add explicit track/untrack commands, use central authority, append Phase 2 diagnostics.
write("src/main/java/com/zpkdxgames/plexonquests/command/Phase2QuestCommand.java", r'''package com.zpkdxgames.plexonquests.command;

import com.zpkdxgames.plexonquests.gui.Phase2JournalService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 4.x command compatibility layer around the mature 3.x QuestCommand. */
public final class Phase2QuestCommand implements CommandExecutor, TabCompleter {
    private static final List<String> JOURNAL_ROOTS = List.of(
            "overview", "active", "available", "categories", "tracked", "completed", "statistics", "help",
            "track", "untrack");

    private final QuestCommand delegate;
    private final Phase2JournalService journal;
    private final QuestTrackingService tracking;
    private final Phase2Diagnostics diagnostics;
    private final TextService text;
    private final AdminConfirmationGate confirmations = new AdminConfirmationGate(Duration.ofSeconds(30));

    public Phase2QuestCommand(
            QuestCommand delegate,
            Phase2JournalService journal,
            QuestTrackingService tracking,
            Phase2Diagnostics diagnostics,
            TextService text) {
        this.delegate = delegate;
        this.journal = journal;
        this.tracking = tracking;
        this.diagnostics = diagnostics;
        this.text = text;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("journal") || args[0].equalsIgnoreCase("overview")) {
            if (sender instanceof Player player && sender.hasPermission("plexonquests.use")) {
                journal.openOverview(player);
                return true;
            }
            return delegate.onCommand(sender, command, label, args);
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if (sender instanceof Player player && sender.hasPermission("plexonquests.use")) {
            switch (root) {
                case "active" -> { journal.openActive(player); return true; }
                case "available" -> { journal.openAvailable(player, 0, null); return true; }
                case "categories" -> { journal.openCategories(player); return true; }
                case "tracked" -> { journal.openTracked(player); return true; }
                case "completed" -> { journal.openCompleted(player); return true; }
                case "statistics", "stats" -> { journal.openStatistics(player); return true; }
                case "help" -> { journal.openHelp(player); return true; }
                case "track" -> { return track(player, args); }
                case "untrack" -> { return untrack(player); }
                default -> { }
            }
        } else if (root.equals("track") || root.equals("untrack")) {
            sender.sendMessage(text.parse("<red>You do not have permission to change quest tracking."));
            return true;
        }

        String destructivePermission = switch (root) {
            case "complete" -> "plexonquests.admin.complete";
            case "reset" -> "plexonquests.admin.reset";
            case "cancel" -> "plexonquests.admin.cancel";
            default -> null;
        };
        if (destructivePermission != null && args.length >= 3 && sender.hasPermission(destructivePermission)) {
            if (confirmations.check(sender, args) == AdminConfirmationGate.Decision.STAGED) {
                sender.sendMessage(text.parse(
                        "<gold><bold>Confirmation required.</bold> <gray>Repeat the exact same command within <white>30 seconds</white> to confirm."
                                + " <dark_gray>The authoritative handler will re-check target state before mutation."));
                return true;
            }
        }
        boolean handled = delegate.onCommand(sender, command, label, args);
        if (root.equals("diagnostics") && sender.hasPermission("plexonquests.admin.diagnostics")) {
            diagnostics.append(sender);
        }
        return handled;
    }

    private boolean track(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(text.parse("<yellow>Usage: <white>/quests track <assignment|quest>"));
            return true;
        }
        report(player, tracking.track(player, args[1]));
        return true;
    }

    private boolean untrack(Player player) {
        report(player, tracking.untrack(player));
        return true;
    }

    private void report(Player player, QuestTrackingService.Result result) {
        switch (result) {
            case TRACKED -> player.sendMessage(text.parse("<yellow>Quest is now tracked."));
            case UNTRACKED -> player.sendMessage(text.parse("<gray>Tracked quest cleared."));
            case UNCHANGED -> player.sendMessage(text.parse("<gray>Quest tracking is already in that state."));
            case NO_PERMISSION -> player.sendMessage(text.parse("<red>You do not have permission to change quest tracking."));
            case NOT_FOUND -> player.sendMessage(text.parse("<red>No matching active quest was found."));
            case NOT_ACTIVE -> player.sendMessage(text.parse("<red>Only active quests can be tracked."));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("track") && sender instanceof Player player) {
            return filter(player, args[1]);
        }
        List<String> delegated = delegate.onTabComplete(sender, command, alias, args);
        if (args.length != 1) {
            return delegated;
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        Set<String> merged = new LinkedHashSet<>();
        if (delegated != null) merged.addAll(delegated);
        JOURNAL_ROOTS.stream().filter(value -> value.startsWith(prefix)).forEach(merged::add);
        return new ArrayList<>(merged);
    }

    private List<String> filter(Player player, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        return player.hasPermission("plexonquests.pin")
                ? journal.activeQuestIds(player).stream().filter(id -> id.startsWith(lowered)).toList()
                : List.of();
    }
}
''')

# Phase 2 journal centralizes mutation through QuestTrackingService and hides unauthorized right-click controls.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    "import com.zpkdxgames.plexonquests.event.QuestTrackEvent;\n",
    "")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    "import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;\n",
    "import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;\nimport com.zpkdxgames.plexonquests.service.QuestTrackingService;\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''    private final CompletionHistoryCache history;\n    private final TextService text;\n''',
    '''    private final CompletionHistoryCache history;\n    private final QuestTrackingService tracking;\n    private final TextService text;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''            QuestPrerequisiteService prerequisites,\n            CompletionHistoryCache history,\n            TextService text) {\n''',
    '''            QuestPrerequisiteService prerequisites,\n            CompletionHistoryCache history,\n            QuestTrackingService tracking,\n            TextService text) {\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''        this.prerequisites = prerequisites;\n        this.history = history;\n        this.text = text;\n''',
    '''        this.prerequisites = prerequisites;\n        this.history = history;\n        this.tracking = tracking;\n        this.text = text;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''            holder.inventory.setItem(slot, assignmentCard(assignment, tracked));\n            holder.actions.put(slot, (p, click) -> {\n                if (click.isRightClick() && assignment.state() == AssignmentState.ACTIVE) {\n                    toggleTracked(p, assignment);\n''',
    '''            holder.inventory.setItem(slot, assignmentCard(player, assignment, tracked));\n            holder.actions.put(slot, (p, click) -> {\n                if (click.isRightClick() && assignment.state() == AssignmentState.ACTIVE\n                        && p.hasPermission("plexonquests.pin")) {\n                    toggleTracked(p, assignment);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''    private void toggleTracked(Player player, QuestAssignment assignment) {\n        PlayerProfile profile = profile(player);\n        if (profile == null || assignment.state() != AssignmentState.ACTIVE) return;\n        boolean old = profile.pinnedAssignment().filter(assignment.id()::equals).isPresent();\n        profile.pinnedAssignment(old ? null : assignment.id());\n        profiles.persistPreferences(profile);\n        Bukkit.getPluginManager().callEvent(new QuestTrackEvent(\n                player, assignment.id(), assignment.definition().id(), !old));\n        player.sendMessage(text.parse(old ? "<gray>Tracked quest cleared." : "<yellow>Quest is now tracked."));\n        openActive(player);\n    }\n''',
    '''    private void toggleTracked(Player player, QuestAssignment assignment) {\n        QuestTrackingService.Result result = tracking.toggle(player, assignment.id());\n        switch (result) {\n            case TRACKED -> player.sendMessage(text.parse("<yellow>Quest is now tracked."));\n            case UNTRACKED -> player.sendMessage(text.parse("<gray>Tracked quest cleared."));\n            case NO_PERMISSION -> player.sendMessage(text.parse("<red>You do not have permission to change quest tracking."));\n            case NOT_ACTIVE -> player.sendMessage(text.parse("<red>Only active quests can be tracked."));\n            case NOT_FOUND -> player.sendMessage(text.parse("<red>That quest is no longer available."));\n            case UNCHANGED -> { }\n        }\n        openActive(player);\n    }\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''    private ItemStack assignmentCard(QuestAssignment assignment, boolean tracked) {\n''',
    '''    private ItemStack assignmentCard(Player player, QuestAssignment assignment, boolean tracked) {\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''                Component.empty(),\n                text.parse("<dark_gray>Left-click details • Right-click track"));\n''',
    '''                Component.empty(),\n                text.parse(player.hasPermission("plexonquests.pin") && assignment.state() == AssignmentState.ACTIVE\n                        ? "<dark_gray>Left-click details • Right-click track"\n                        : "<dark_gray>Left-click details"));\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''    public void openTracked(Player player) {\n        PlayerProfile profile = profile(player);\n        if (profile == null) return;\n        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);\n''',
    '''    public void openTracked(Player player) {\n        PlayerProfile profile = profile(player);\n        if (profile == null) return;\n        tracking.reconcile(player, profile);\n        QuestAssignment tracked = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/Phase2JournalService.java",
    '''    public JournalStateResolver stateResolver() {\n        return states;\n    }\n''',
    '''    public JournalStateResolver stateResolver() {\n        return states;\n    }\n\n    public List<String> activeQuestIds(Player player) {\n        PlayerProfile profile = profiles.profile(player).orElse(null);\n        if (profile == null) return List.of();\n        return profile.visibleAssignments().stream()\n                .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)\n                .map(assignment -> assignment.definition().id())\n                .distinct()\n                .sorted()\n                .toList();\n    }\n''')

# Legacy GUI keeps the same permission but delegates mutation to the same tracking authority.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    "import com.zpkdxgames.plexonquests.service.ProfileService;\n",
    "import com.zpkdxgames.plexonquests.service.ProfileService;\nimport com.zpkdxgames.plexonquests.service.QuestTrackingService;\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    '''    private final BlockOriginService origins;\n    private final TextService text;\n''',
    '''    private final BlockOriginService origins;\n    private final QuestTrackingService tracking;\n    private final TextService text;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    '''            IntegrationManager integrations,\n            BlockOriginService origins,\n            TextService text,\n''',
    '''            IntegrationManager integrations,\n            BlockOriginService origins,\n            QuestTrackingService tracking,\n            TextService text,\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    '''        this.integrations = integrations;\n        this.origins = origins;\n        this.text = text;\n''',
    '''        this.integrations = integrations;\n        this.origins = origins;\n        this.tracking = tracking;\n        this.text = text;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    '''        if (assignedToPlayer && !assignment.state().terminal() && player.hasPermission("plexonquests.pin")) {\n''',
    '''        if (assignedToPlayer && assignment.state() == AssignmentState.ACTIVE && player.hasPermission("plexonquests.pin")) {\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/MenuService.java",
    '''    private void togglePin(Player player, UUID assignmentId, MenuContext returnContext) {\n        if (!player.hasPermission("plexonquests.pin")) {\n            return;\n        }\n        PlayerProfile profile = requireProfile(player);\n        if (profile == null || profile.assignment(assignmentId).isEmpty()) {\n            return;\n        }\n        boolean unpin = profile.pinnedAssignment().filter(assignmentId::equals).isPresent();\n        profile.pinnedAssignment(unpin ? null : assignmentId);\n        profiles.persistPreferences(profile);\n        String path = unpin ? "quests.unpinned" : "quests.pinned";\n        QuestAssignment assignment = profile.assignment(assignmentId).orElseThrow();\n        player.sendMessage(text.message(path, Map.of(\n                "quest_name", text.plain(text.parse(assignment.definition().display().name()))));\n        openContext(player, returnContext);\n    }\n''',
    '''    private void togglePin(Player player, UUID assignmentId, MenuContext returnContext) {\n        QuestTrackingService.Result result = tracking.toggle(player, assignmentId);\n        PlayerProfile profile = profiles.profile(player).orElse(null);\n        QuestAssignment assignment = profile == null ? null : profile.assignment(assignmentId).orElse(null);\n        if (assignment != null && (result == QuestTrackingService.Result.TRACKED\n                || result == QuestTrackingService.Result.UNTRACKED)) {\n            String path = result == QuestTrackingService.Result.UNTRACKED ? "quests.unpinned" : "quests.pinned";\n            player.sendMessage(text.message(path, Map.of(\n                    "quest_name", text.plain(text.parse(assignment.definition().display().name()))));\n        }\n        openContext(player, returnContext);\n    }\n''')

# Never advertise a dead pin action for non-active assignments; guard objective percentages against invalid output.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/QuestItemRenderer.java",
    '''        if (player.hasPermission("plexonquests.pin")) {\n''',
    '''        if (assignment.state() == AssignmentState.ACTIVE && player.hasPermission("plexonquests.pin")) {\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/gui/QuestItemRenderer.java",
    '''                            "progress_color", text.progressColor(objective.current() * 100D / objective.required()),\n''',
    '''                            "progress_color", text.progressColor(objective.required() == 0L\n                                    ? 0D : objective.current() * 100D / objective.required()),\n''')

# Irreversible command-style rewards execute after non-command delivery work.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/reward/RewardService.java",
    '''    private record DeliveryPlan(List<RewardDefinition> rewards) {\n        private DeliveryPlan {\n            rewards = List.copyOf(rewards);\n        }\n    }\n''',
    '''    static List<RewardDefinition> orderForDelivery(List<RewardDefinition> rewards) {\n        List<RewardDefinition> ordered = new ArrayList<>(rewards.size());\n        rewards.stream().filter(reward -> !irreversibleCommand(reward)).forEach(ordered::add);\n        rewards.stream().filter(RewardService::irreversibleCommand).forEach(ordered::add);\n        return List.copyOf(ordered);\n    }\n\n    private static boolean irreversibleCommand(RewardDefinition reward) {\n        return reward.type() == RewardType.COMMAND || reward.type() == RewardType.PLEXON_KEY;\n    }\n\n    private record DeliveryPlan(List<RewardDefinition> rewards) {\n        private DeliveryPlan {\n            rewards = orderForDelivery(rewards);\n        }\n    }\n''')

# Stronger actor identity for player confirmations.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/command/AdminConfirmationGate.java",
    "import org.bukkit.command.CommandSender;\n",
    "import org.bukkit.command.CommandSender;\nimport org.bukkit.entity.Player;\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/command/AdminConfirmationGate.java",
    '''    private static String actorKey(CommandSender sender) {\n        return sender.getClass().getName() + ':' + sender.getName().toLowerCase(Locale.ROOT);\n    }\n''',
    '''    private static String actorKey(CommandSender sender) {\n        if (sender instanceof Player player) {\n            return "player:" + player.getUniqueId();\n        }\n        return sender.getClass().getName() + ':' + sender.getName().toLowerCase(Locale.ROOT);\n    }\n''')

# Plugin wiring: atomic graph snapshot, central tracking, cache reload/invalidation, Phase 2 diagnostics.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    "import com.zpkdxgames.plexonquests.command.Phase2QuestCommand;\n",
    "import com.zpkdxgames.plexonquests.command.Phase2Diagnostics;\nimport com.zpkdxgames.plexonquests.command.Phase2QuestCommand;\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    "import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;\n",
    "import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;\nimport com.zpkdxgames.plexonquests.service.QuestTrackingService;\n")
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''    private CompletionHistoryCache completionHistory;\n    private Phase2JournalService phase2Journal;\n''',
    '''    private CompletionHistoryCache completionHistory;\n    private QuestTrackingService tracking;\n    private Phase2JournalService phase2Journal;\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''            prerequisites = new QuestPrerequisiteService(configs);\n            QuestPrerequisiteService.ValidationResult graph = prerequisites.reloadValidated();\n            if (!graph.valid()) {\n                throw new IllegalStateException("Invalid quest prerequisite graph: " + String.join("; ", graph.errors()));\n            }\n            completionHistory = new CompletionHistoryCache(this, storage);\n''',
    '''            prerequisites = new QuestPrerequisiteService(configs);\n            QuestPrerequisiteService.Snapshot graph = prerequisites.snapshot();\n            completionHistory = new CompletionHistoryCache(this, storage);\n            tracking = new QuestTrackingService(configs, profiles);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''            MenuService menus = new MenuService(\n                    this, configs, profiles, storage, rewards, rerolls, integrations, origins, text, configExecutor);\n            phase2Journal = new Phase2JournalService(\n                    configs, profiles, menus, eligibility, prerequisites, completionHistory, text);\n''',
    '''            MenuService menus = new MenuService(\n                    this, configs, profiles, storage, rewards, rerolls, integrations, origins, tracking, text, configExecutor);\n            phase2Journal = new Phase2JournalService(\n                    configs, profiles, menus, eligibility, prerequisites, completionHistory, tracking, text);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''            registerListeners(rewards, text, menus, phase2Journal);\n''',
    '''            registerListeners(rewards, text, menus, phase2Journal);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''        manager.registerEvents(journal, this);\n        manager.registerEvents(completionHistory, this);\n''',
    '''        manager.registerEvents(journal, this);\n        manager.registerEvents(tracking, this);\n        manager.registerEvents(completionHistory, this);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''        Phase2QuestCommand phase2Handler = new Phase2QuestCommand(\n                this, baseHandler, journal, prerequisites, text, configExecutor);\n''',
    '''        Phase2Diagnostics phase2Diagnostics = new Phase2Diagnostics(\n                this, configs, profiles, storage, prerequisites, completionHistory, tracking, text);\n        Phase2QuestCommand phase2Handler = new Phase2QuestCommand(\n                baseHandler, journal, tracking, phase2Diagnostics, text);\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''            core.markReady("Quest engine ready; block acquisition=" + coreRuntime.acquisitionMode()\n                    + "; origin=" + coreRuntime.originProvider() + "; prerequisite-graph=" + graph.snapshot().questCount());\n''',
    '''            core.markReady("Quest engine ready; block acquisition=" + coreRuntime.acquisitionMode()\n                    + "; origin=" + coreRuntime.originProvider() + "; prerequisite-graph=" + graph.questCount());\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''                    + "; prerequisite graph: " + graph.snapshot().questCount() + " quest(s).\");\n''',
    '''                    + "; prerequisite graph: " + graph.questCount() + " quest(s).\");\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''        profiles.readyHandler((player, profile) -> {\n            progress.reindex(profile);\n            rerolls.warm(player);\n            completionHistory.load(player).whenComplete((ignored, failure) ->\n''',
    '''        profiles.readyHandler((player, profile) -> {\n            tracking.reconcile(player, profile);\n            progress.reindex(profile);\n            rerolls.warm(player);\n            completionHistory.load(player, profile).whenComplete((ignored, failure) ->\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''        integrations.detect();\n        profiles.onlineProfiles().forEach(profile -> {\n            profiles.refreshRankCategory(profile);\n            progress.reindex(profile);\n        });\n        if (prerequisites != null && configExecutor != null) {\n            java.util.concurrent.CompletableFuture\n                    .supplyAsync(prerequisites::reloadValidated, configExecutor)\n                    .thenAccept(result -> {\n                        if (!result.valid()) {\n                            getLogger().severe("Reloaded YAML contains an invalid prerequisite graph; previous Phase 2 graph remains active: "\n                                    + String.join("; ", result.errors()));\n                            if (core != null) {\n                                core.markDegraded("Quest prerequisite graph reload rejected; previous graph retained");\n                            }\n                        }\n                    });\n        }\n''',
    '''        integrations.detect();\n        completionHistory.invalidateAll();\n        profiles.onlineProfiles().forEach(profile -> {\n            profiles.refreshRankCategory(profile);\n            Player player = Bukkit.getPlayer(profile.playerId());\n            if (player != null && player.isOnline()) {\n                tracking.reconcile(player, profile);\n                completionHistory.load(player, profile).whenComplete((ignored, failure) -> {\n                    if (failure != null) {\n                        getLogger().log(Level.WARNING, "Could not reload completion history after configuration activation", failure);\n                    }\n                });\n            }\n            progress.reindex(profile);\n        });\n''')
replace(
    "src/main/java/com/zpkdxgames/plexonquests/PlexonQuestsPlugin.java",
    '''        if (activity != null) {\n            activity.close();\n            activity = null;\n        }\n''',
    '''        if (activity != null) {\n            activity.close();\n            activity = null;\n        }\n        if (completionHistory != null) {\n            completionHistory.close();\n            completionHistory = null;\n        }\n''')

# PAPI never presents a stale completed/expired pin as tracked.
replace(
    "src/main/java/com/zpkdxgames/plexonquests/integration/PlexonQuestsExpansion.java",
    '''        QuestAssignment pinned = profile.pinnedAssignment().flatMap(profile::assignment).orElse(null);\n''',
    '''        QuestAssignment pinned = profile.pinnedAssignment().flatMap(profile::assignment)\n                .filter(assignment -> assignment.state() == com.zpkdxgames.plexonquests.quest.AssignmentState.ACTIVE)\n                .orElse(null);\n''')

# Tests: graph chains, state precedence, cache paging/stale callbacks, tracking permission/lifecycle, reward command boundary.
write("src/test/java/com/zpkdxgames/plexonquests/service/JournalStateResolverTest.java", r'''package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.JournalState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JournalStateResolverTest {
    private final UUID playerId = UUID.randomUUID();
    private QuestEligibilityService eligibility;
    private QuestPrerequisiteService prerequisites;
    private CompletionHistoryCache history;
    private Player player;
    private JournalStateResolver resolver;

    @BeforeEach
    void setup() {
        eligibility = mock(QuestEligibilityService.class);
        prerequisites = mock(QuestPrerequisiteService.class);
        history = mock(CompletionHistoryCache.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(eligibility.evaluate(org.mockito.ArgumentMatchers.eq(player), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(QuestDefinition.class)))
                .thenReturn(QuestEligibilityService.EligibilityResult.allowed());
        when(prerequisites.prerequisites(org.mockito.ArgumentMatchers.anyString())).thenReturn(Set.of());
        when(history.loaded(playerId)).thenReturn(true);
        resolver = new JournalStateResolver(eligibility, prerequisites, history);
    }

    @Test
    void resolvesAvailableLockedDisabledAndHistoricalCompletedPrecedence() {
        QuestDefinition available = TestFixtures.quest("available", "general", QuestScope.DAILY, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2);
        PlayerProfile empty = profile(List.of());
        assertEquals(JournalState.AVAILABLE, resolver.resolve(player, empty, available));

        when(prerequisites.prerequisites("available")).thenReturn(Set.of("base"));
        when(history.completed(playerId, "base")).thenReturn(false);
        assertEquals(JournalState.LOCKED, resolver.resolve(player, empty, available));

        QuestDefinition disabled = copyEnabled(available, false);
        assertEquals(JournalState.DISABLED, resolver.resolve(player, empty, disabled));

        QuestDefinition milestone = TestFixtures.quest("milestone", "general", QuestScope.MILESTONE, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2);
        when(history.completed(playerId, "milestone")).thenReturn(true);
        when(eligibility.evaluate(org.mockito.ArgumentMatchers.eq(player), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(milestone)))
                .thenReturn(QuestEligibilityService.EligibilityResult.denied("new gate"));
        assertEquals(JournalState.COMPLETED, resolver.resolve(player, empty, milestone));
    }

    @Test
    void resolvesActiveTrackedCompletableCooldownAndExpiredWithStablePrecedence() {
        QuestDefinition quest = TestFixtures.quest("daily", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 1);
        QuestAssignment active = QuestAssignment.create(playerId, quest, "daily", "p", Instant.now(), Instant.now().plusSeconds(60));
        PlayerProfile profile = profile(List.of(active));
        assertEquals(JournalState.ACTIVE, resolver.resolve(player, profile, quest));

        profile.pinnedAssignment(active.id());
        assertEquals(JournalState.TRACKED, resolver.resolve(player, profile, quest));

        active.addProgress("objective_1", 1, Instant.now());
        assertEquals(AssignmentState.COMPLETED, active.state());
        assertEquals(JournalState.COMPLETABLE, resolver.resolve(player, profile, quest));

        active.markClaiming();
        active.markClaimed(Instant.now());
        assertEquals(JournalState.COOLDOWN, resolver.resolve(player, profile, quest));

        QuestAssignment expired = QuestAssignment.create(playerId, TestFixtures.quest("expired", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), Instant.now());
        expired.expire();
        PlayerProfile expiredProfile = profile(List.of(expired));
        assertEquals(JournalState.EXPIRED, resolver.resolve(player, expiredProfile, expired.definition()));
    }

    @Test
    void coldHistoryFailsClosedForNonRotatingCompletionKnowledge() {
        QuestDefinition milestone = TestFixtures.quest("cold", "general", QuestScope.MILESTONE, com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 1);
        when(history.loaded(playerId)).thenReturn(false);
        assertEquals(JournalState.LOCKED, resolver.resolve(player, profile(List.of()), milestone));
    }

    private PlayerProfile profile(List<QuestAssignment> assignments) {
        return new PlayerProfile(playerId, "Tester", FeedbackPreferences.defaults(), null, 0, assignments);
    }

    private static QuestDefinition copyEnabled(QuestDefinition q, boolean enabled) {
        return new QuestDefinition(q.id(), q.revision(), enabled, q.scope(), q.category(), q.rarity(), q.weight(), q.eligibility(),
                q.display(), q.completionMode(), q.claimMode(), q.objectives(), q.rewards(), q.completeEffect(), q.claimEffect(), q.fingerprint(), q.source());
    }
}
''')

write("src/test/java/com/zpkdxgames/plexonquests/service/CompletionHistoryCacheTest.java", r'''package com.zpkdxgames.plexonquests.service;

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
''')

write("src/test/java/com/zpkdxgames/plexonquests/service/QuestTrackingServiceTest.java", r'''package com.zpkdxgames.plexonquests.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexonquests.TestFixtures;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class QuestTrackingServiceTest {
    private Player player;
    private PlayerProfile profile;
    private QuestTrackingService tracking;
    private QuestAssignment first;
    private QuestAssignment second;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        first = QuestAssignment.create(playerId, TestFixtures.quest("first", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), null);
        second = QuestAssignment.create(playerId, TestFixtures.quest("second", com.zpkdxgames.plexonquests.quest.CompletionMode.ALL, 2), "daily", "p", Instant.now(), null);
        profile = new PlayerProfile(playerId, "Tester", FeedbackPreferences.defaults(), null, 0, List.of(first, second));
        ProfileService profiles = mock(ProfileService.class);
        when(profiles.profile(player)).thenReturn(java.util.Optional.of(profile));
        when(profiles.onlineProfiles()).thenReturn(List.of(profile));
        ConfigManager configs = mock(ConfigManager.class);
        ConfigSnapshot snapshot = mock(ConfigSnapshot.class);
        QuestRegistrySnapshot registry = mock(QuestRegistrySnapshot.class);
        when(configs.snapshot()).thenReturn(snapshot);
        when(snapshot.registry()).thenReturn(registry);
        when(registry.quests()).thenReturn(Map.of("first", first.definition(), "second", second.definition()));
        tracking = new QuestTrackingService(configs, profiles);
    }

    @AfterEach
    void stop() { MockBukkit.unmock(); }

    @Test
    void permissionRejectsBothTrackAndUntrackMutation() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(false);
        assertEquals(QuestTrackingService.Result.NO_PERMISSION, tracking.track(player, "first"));
        profile.pinnedAssignment(first.id());
        assertEquals(QuestTrackingService.Result.NO_PERMISSION, tracking.untrack(player));
        assertTrue(profile.pinnedAssignment().filter(first.id()::equals).isPresent());
    }

    @Test
    void switchingAndCompletionCleanupUseOneAuthority() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(true);
        assertEquals(QuestTrackingService.Result.TRACKED, tracking.track(player, "first"));
        assertEquals(QuestTrackingService.Result.TRACKED, tracking.track(player, "second"));
        assertTrue(profile.pinnedAssignment().filter(second.id()::equals).isPresent());

        second.addProgress("objective_1", 2, Instant.now());
        tracking.onComplete(new com.zpkdxgames.plexonquests.event.QuestCompleteEvent(player, second.id(), "second"));
        assertTrue(profile.pinnedAssignment().isEmpty());
    }

    @Test
    void reconcileClearsPersistedNonActiveTrackingWithoutCheckingPermission() {
        when(player.hasPermission("plexonquests.pin")).thenReturn(false);
        profile.pinnedAssignment(first.id());
        first.forceComplete(Instant.now());
        assertTrue(tracking.reconcile(player, profile));
        assertTrue(profile.pinnedAssignment().isEmpty());
    }
}
''')

write("src/test/java/com/zpkdxgames/plexonquests/reward/RewardDeliveryOrderTest.java", r'''package com.zpkdxgames.plexonquests.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RewardDeliveryOrderTest {
    @Test
    void commandStyleRewardsAreAlwaysAfterNonCommandRewards() {
        RewardDefinition command = reward("command", RewardType.COMMAND);
        RewardDefinition item = reward("item", RewardType.ITEM);
        RewardDefinition money = reward("money", RewardType.MONEY);
        RewardDefinition key = reward("key", RewardType.PLEXON_KEY);

        List<RewardDefinition> ordered = RewardService.orderForDelivery(List.of(command, item, key, money));

        assertEquals(List.of(item, money, command, key), ordered);
    }

    private static RewardDefinition reward(String id, RewardType type) {
        return new RewardDefinition(id, type, 1L, 1D, org.bukkit.Material.STONE,
                type == RewardType.COMMAND ? "say test" : "", "", Duration.ZERO, "", "",
                type == RewardType.PLEXON_KEY ? "say key" : "", id, 1);
    }
}
''')

# Extend graph tests for multi-node and transitive disabled/unreachable chains.
replace(
    "src/test/java/com/zpkdxgames/plexonquests/service/QuestPrerequisiteServiceTest.java",
    '''    @Test\n    void rejectsEnabledQuestDependingOnDisabledQuest() throws Exception {\n''',
    '''    @Test\n    void rejectsThreeNodeCycle() throws Exception {\n        quest("alpha", true, "  completed-quests:\\n    - beta\\n");\n        quest("beta", true, "  completed-quests:\\n    - gamma\\n");\n        quest("gamma", true, "  completed-quests:\\n    - alpha\\n");\n\n        var result = QuestPrerequisiteService.validate(temp);\n\n        assertFalse(result.valid());\n        assertTrue(result.errors().stream().anyMatch(error -> error.contains("prerequisite cycle")));\n    }\n\n    @Test\n    void rejectsTransitiveUnreachableDisabledChain() throws Exception {\n        quest("base", false, "");\n        quest("middle", true, "  completed-quests:\\n    - base\\n");\n        quest("top", true, "  completed-quests:\\n    - middle\\n");\n\n        var result = QuestPrerequisiteService.validate(temp);\n\n        assertFalse(result.valid());\n        assertTrue(result.errors().stream().anyMatch(error -> error.contains("base") && error.contains("unreachable")));\n    }\n\n    @Test\n    void rejectsEnabledQuestDependingOnDisabledQuest() throws Exception {\n''')

# Completion transition tests enforce duplicate final event/idempotent state behavior at the assignment boundary.
write("src/test/java/com/zpkdxgames/plexonquests/quest/CompletionIdempotencyTest.java", r'''package com.zpkdxgames.plexonquests.quest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.TestFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompletionIdempotencyTest {
    @Test
    void duplicateFinalObjectiveCannotCompleteAssignmentTwice() {
        QuestAssignment assignment = QuestAssignment.create(UUID.randomUUID(),
                TestFixtures.quest("duplicate", CompletionMode.ALL, 1), "daily", "period", Instant.now(), null);

        ProgressResult first = assignment.addProgress("objective_1", 1, Instant.now());
        ProgressResult duplicate = assignment.addProgress("objective_1", 1, Instant.now());

        assertTrue(first.questCompleted());
        assertFalse(duplicate.accepted());
        assertFalse(duplicate.questCompleted());
    }

    @Test
    void forceCompleteAndClaimTransitionsAreOneShot() {
        QuestAssignment assignment = QuestAssignment.create(UUID.randomUUID(),
                TestFixtures.quest("force", CompletionMode.ALL, 2), "daily", "period", Instant.now(), null);
        assertTrue(assignment.forceComplete(Instant.now()));
        assertFalse(assignment.forceComplete(Instant.now()));
        assertTrue(assignment.markClaiming());
        assertFalse(assignment.markClaiming());
        assertTrue(assignment.markClaimed(Instant.now()));
        assertFalse(assignment.markClaimed(Instant.now()));
    }
}
''')

print("Phase 2 closure patch applied")

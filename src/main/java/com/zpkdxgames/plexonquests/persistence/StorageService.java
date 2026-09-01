package com.zpkdxgames.plexonquests.persistence;

import com.zpkdxgames.plexonquests.config.PluginSettings;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.service.FeedbackPreferences;
import com.zpkdxgames.plexonquests.util.AtomicFiles;
import com.zpkdxgames.plexonquests.util.LogSanitizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StorageService implements AutoCloseable {
    private static final String[] MIGRATION_V1 = {
        """
        CREATE TABLE IF NOT EXISTS schema_meta (
          key TEXT PRIMARY KEY,
          value TEXT NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS player_profiles (
          player_uuid TEXT PRIMARY KEY,
          latest_name TEXT NOT NULL,
          settings_mask INTEGER NOT NULL,
          pinned_assignment TEXT,
          completed_total INTEGER NOT NULL DEFAULT 0,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS assignments (
          assignment_id TEXT PRIMARY KEY,
          player_uuid TEXT NOT NULL,
          quest_id TEXT NOT NULL,
          revision INTEGER NOT NULL,
          fingerprint TEXT NOT NULL,
          scope TEXT NOT NULL,
          pool_id TEXT NOT NULL,
          period_key TEXT NOT NULL,
          state TEXT NOT NULL,
          assigned_at INTEGER NOT NULL,
          expires_at INTEGER,
          completed_at INTEGER,
          claimed_at INTEGER,
          definition_snapshot TEXT NOT NULL,
          FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE,
          UNIQUE (player_uuid, quest_id, period_key)
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS assignment_objectives (
          assignment_id TEXT NOT NULL,
          objective_id TEXT NOT NULL,
          objective_type TEXT NOT NULL,
          required_amount INTEGER NOT NULL,
          current_amount INTEGER NOT NULL,
          completed INTEGER NOT NULL,
          PRIMARY KEY (assignment_id, objective_id),
          FOREIGN KEY (assignment_id) REFERENCES assignments(assignment_id) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS claim_transactions (
          transaction_id TEXT PRIMARY KEY,
          assignment_id TEXT NOT NULL,
          player_uuid TEXT NOT NULL,
          status TEXT NOT NULL,
          reward_fingerprint TEXT NOT NULL,
          detail TEXT NOT NULL,
          created_at INTEGER NOT NULL,
          updated_at INTEGER NOT NULL,
          FOREIGN KEY (assignment_id) REFERENCES assignments(assignment_id) ON DELETE RESTRICT
        )
        """,
        """
        CREATE UNIQUE INDEX IF NOT EXISTS claim_one_live_transaction
        ON claim_transactions(assignment_id)
        WHERE status IN ('RESERVED', 'SUCCESS', 'UNCERTAIN')
        """,
        """
        CREATE TABLE IF NOT EXISTS reroll_transactions (
          transaction_id TEXT PRIMARY KEY,
          player_uuid TEXT NOT NULL,
          old_assignment_id TEXT NOT NULL,
          new_assignment_id TEXT NOT NULL,
          cost REAL NOT NULL,
          status TEXT NOT NULL,
          detail TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS quest_history (
          history_id INTEGER PRIMARY KEY AUTOINCREMENT,
          player_uuid TEXT NOT NULL,
          assignment_id TEXT NOT NULL UNIQUE,
          quest_id TEXT NOT NULL,
          display_name TEXT NOT NULL,
          rarity TEXT NOT NULL,
          scope TEXT NOT NULL,
          state TEXT NOT NULL,
          assigned_at INTEGER NOT NULL,
          completed_at INTEGER,
          claimed_at INTEGER,
          objective_summary TEXT NOT NULL,
          reward_summary TEXT NOT NULL,
          FOREIGN KEY (player_uuid) REFERENCES player_profiles(player_uuid) ON DELETE CASCADE
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS admin_audit (
          audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
          actor TEXT NOT NULL,
          player_uuid TEXT,
          assignment_id TEXT,
          action TEXT NOT NULL,
          reason TEXT NOT NULL,
          old_value TEXT NOT NULL,
          new_value TEXT NOT NULL,
          created_at INTEGER NOT NULL
        )
        """,
        """
        CREATE TABLE IF NOT EXISTS rotation_state (
          state_key TEXT PRIMARY KEY,
          state_value TEXT NOT NULL,
          updated_at INTEGER NOT NULL
        )
        """,
        "CREATE INDEX IF NOT EXISTS assignments_player_state ON assignments(player_uuid, state)",
        "CREATE INDEX IF NOT EXISTS assignments_period ON assignments(player_uuid, scope, period_key)",
        "CREATE INDEX IF NOT EXISTS history_player_date ON quest_history(player_uuid, history_id DESC)",
        "INSERT OR REPLACE INTO schema_meta(key, value) VALUES ('schema_version', '1')"
    };

    private final Path dataDirectory;
    private final Path databaseFile;
    private final PluginSettings.Storage settings;
    private final Logger logger;
    private final ThreadPoolExecutor writer;
    private final AssignmentSnapshotCodec codec = new AssignmentSnapshotCodec();
    private final ConcurrentHashMap<UUID, DirtyAssignment> dirty = new ConcurrentHashMap<>();
    private final AtomicBoolean flushScheduled = new AtomicBoolean();
    private final AtomicBoolean accepting = new AtomicBoolean();
    private final AtomicLong rejectedOperations = new AtomicLong();
    private volatile Connection connection;
    private volatile Duration lastFlushDuration = Duration.ZERO;
    private volatile String lastFlushResult = "Never flushed";
    private volatile Instant lastFlushAt = Instant.EPOCH;

    public StorageService(Path dataDirectory, PluginSettings.Storage settings, Logger logger) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
        this.settings = settings;
        this.logger = logger;
        this.databaseFile = AtomicFiles.resolveInside(this.dataDirectory, settings.file());
        this.writer = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "PlexonQuests-Storage");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, executor) -> {
                    rejectedOperations.incrementAndGet();
                    throw new RejectedExecutionException("PlexonQuests storage queue is full");
                });
    }

    public void start() throws SQLException, IOException, TimeoutException {
        Files.createDirectories(databaseFile.getParent());
        CompletableFuture<Void> started = submitInternal(() -> {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException exception) {
                throw new SQLException("Bundled SQLite driver is missing", exception);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=" + settings.busyTimeoutMillis());
            }
            migrate();
            accepting.set(true);
            return null;
        });
        try {
            started.get(Math.max(5L, settings.shutdownTimeout().toSeconds()), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while opening quest storage", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof SQLException sql) {
                throw sql;
            }
            if (cause instanceof IOException io) {
                throw io;
            }
            throw new SQLException("Unable to initialize quest storage", cause);
        }
    }

    public CompletableFuture<StoredProfile> loadProfile(UUID playerId, String latestName) {
        return submit(() -> {
            ensureProfile(playerId, latestName);
            FeedbackPreferences preferences = FeedbackPreferences.defaults();
            UUID pinned = null;
            long completedTotal = 0L;
            String persistedName = latestName;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT latest_name, settings_mask, pinned_assignment, completed_total FROM player_profiles WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        persistedName = result.getString(1);
                        preferences = new FeedbackPreferences(result.getInt(2));
                        String pinnedText = result.getString(3);
                        pinned = pinnedText == null ? null : UUID.fromString(pinnedText);
                        completedTotal = result.getLong(4);
                    }
                }
            }

            List<QuestAssignment> assignments = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT assignment_id, pool_id, period_key, state, assigned_at, expires_at,
                           completed_at, claimed_at, definition_snapshot
                    FROM assignments
                    WHERE player_uuid=? AND state IN ('ACTIVE','COMPLETED','CLAIMING')
                    ORDER BY assigned_at, assignment_id
                    """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID assignmentId = UUID.fromString(result.getString("assignment_id"));
                        QuestDefinition definition;
                        try {
                            definition = codec.decode(result.getString("definition_snapshot"));
                        } catch (IOException exception) {
                            logger.log(Level.SEVERE, "Quarantined unreadable assignment snapshot " + assignmentId, exception);
                            continue;
                        }
                        Map<String, Long> progress = loadObjectiveProgress(assignmentId);
                        assignments.add(new QuestAssignment(
                                assignmentId,
                                playerId,
                                definition,
                                result.getString("pool_id"),
                                result.getString("period_key"),
                                instant(result, "assigned_at"),
                                nullableInstant(result, "expires_at"),
                                AssignmentState.valueOf(result.getString("state")),
                                nullableInstant(result, "completed_at"),
                                nullableInstant(result, "claimed_at"),
                                progress));
                    }
                }
            }
            return new StoredProfile(playerId, persistedName, preferences, pinned, completedTotal, assignments);
        });
    }

    public CompletableFuture<Boolean> insertAssignment(QuestAssignment assignment, String latestName) {
        return submit(() -> transaction(() -> {
            ensureProfile(assignment.playerId(), latestName);
            return insertAssignmentRow(assignment);
        }));
    }

    public CompletableFuture<Boolean> persistReroll(
            String transactionId, QuestAssignment previous, QuestAssignment replacement, double cost, String detail) {
        return submit(() -> transaction(() -> {
            try (PreparedStatement cancel = connection.prepareStatement(
                    "UPDATE assignments SET state='CANCELLED' WHERE assignment_id=? AND state IN ('ACTIVE','COMPLETED')")) {
                cancel.setString(1, previous.id().toString());
                if (cancel.executeUpdate() != 1) {
                    return false;
                }
            }
            if (!insertAssignmentRow(replacement)) {
                throw new SQLException("Replacement assignment conflicted with existing data");
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO reroll_transactions(transaction_id, player_uuid, old_assignment_id,
                      new_assignment_id, cost, status, detail, created_at)
                    VALUES(?,?,?,?,?,'RESERVED',?,?)
                    """)) {
                statement.setString(1, transactionId);
                statement.setString(2, previous.playerId().toString());
                statement.setString(3, previous.id().toString());
                statement.setString(4, replacement.id().toString());
                statement.setDouble(5, cost);
                statement.setString(6, LogSanitizer.clean(detail));
                statement.setLong(7, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
            dirty.remove(previous.id());
            return true;
        }));
    }

    public CompletableFuture<Void> finishReroll(String transactionId, String status, String detail) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE reroll_transactions SET status=?, detail=? WHERE transaction_id=?")) {
                statement.setString(1, status);
                statement.setString(2, LogSanitizer.clean(detail));
                statement.setString(3, transactionId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> countRerolls(UUID playerId, String periodKey) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM reroll_transactions r
                    JOIN assignments a ON a.assignment_id=r.new_assignment_id
                    WHERE r.player_uuid=? AND a.period_key=? AND r.status='SUCCESS'
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, periodKey);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<Void> rollbackReroll(
            String transactionId,
            QuestAssignment previous,
            QuestAssignment replacement,
            AssignmentState previousState,
            String detail) {
        return submit(() -> transaction(() -> {
            try (PreparedStatement old = connection.prepareStatement(
                    "UPDATE assignments SET state=? WHERE assignment_id=? AND state='CANCELLED'")) {
                old.setString(1, previousState.name());
                old.setString(2, previous.id().toString());
                old.executeUpdate();
            }
            try (PreparedStatement replacementStatement = connection.prepareStatement(
                    "UPDATE assignments SET state='CANCELLED' WHERE assignment_id=?")) {
                replacementStatement.setString(1, replacement.id().toString());
                replacementStatement.executeUpdate();
            }
            try (PreparedStatement transaction = connection.prepareStatement(
                    "UPDATE reroll_transactions SET status='ROLLED_BACK', detail=? WHERE transaction_id=?")) {
                transaction.setString(1, LogSanitizer.clean(detail));
                transaction.setString(2, transactionId);
                transaction.executeUpdate();
            }
            return null;
        }));
    }

    public void markDirty(QuestAssignment assignment) {
        if (!accepting.get()) {
            return;
        }
        dirty.put(assignment.id(), DirtyAssignment.capture(assignment));
    }

    public CompletableFuture<Integer> flushDirty() {
        if (!accepting.get() || !flushScheduled.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(0);
        }
        Map<UUID, DirtyAssignment> batch = new LinkedHashMap<>(dirty);
        batch.forEach((id, snapshot) -> dirty.remove(id, snapshot));
        if (batch.isEmpty()) {
            flushScheduled.set(false);
            return CompletableFuture.completedFuture(0);
        }
        Instant started = Instant.now();
        return submit(() -> transaction(() -> {
            for (DirtyAssignment assignment : batch.values()) {
                persistDirty(assignment);
            }
            return batch.size();
        })).whenComplete((count, failure) -> {
            lastFlushDuration = Duration.between(started, Instant.now());
            lastFlushAt = Instant.now();
            if (failure == null) {
                lastFlushResult = "OK (" + count + " assignments)";
            } else {
                lastFlushResult = "FAILED: " + LogSanitizer.clean(failure.getMessage());
                batch.forEach(dirty::putIfAbsent);
            }
            flushScheduled.set(false);
        });
    }

    public CompletableFuture<Void> savePreferences(
            UUID playerId, String latestName, FeedbackPreferences preferences, UUID pinnedAssignment) {
        return submit(() -> {
            ensureProfile(playerId, latestName);
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    UPDATE player_profiles
                    SET latest_name=?, settings_mask=?, pinned_assignment=?, updated_at=?
                    WHERE player_uuid=?
                    """)) {
                statement.setString(1, latestName);
                statement.setInt(2, preferences.mask());
                nullableString(statement, 3, pinnedAssignment == null ? null : pinnedAssignment.toString());
                statement.setLong(4, Instant.now().toEpochMilli());
                statement.setString(5, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> reserveClaim(String transactionId, QuestAssignment assignment) {
        return submit(() -> transaction(() -> {
            DirtyAssignment snapshot = DirtyAssignment.capture(assignment);
            persistDirty(snapshot.withState(AssignmentState.COMPLETED));
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE assignments SET state='CLAIMING' WHERE assignment_id=? AND state='COMPLETED'")) {
                update.setString(1, assignment.id().toString());
                if (update.executeUpdate() != 1) {
                    return false;
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    """
                    INSERT INTO claim_transactions(transaction_id, assignment_id, player_uuid, status,
                      reward_fingerprint, detail, created_at, updated_at)
                    VALUES(?,?,?,'RESERVED',?,'',?,?)
                    """)) {
                long now = Instant.now().toEpochMilli();
                insert.setString(1, transactionId);
                insert.setString(2, assignment.id().toString());
                insert.setString(3, assignment.playerId().toString());
                insert.setString(4, assignment.definition().fingerprint());
                insert.setLong(5, now);
                insert.setLong(6, now);
                insert.executeUpdate();
            }
            dirty.remove(assignment.id());
            return true;
        })).exceptionally(failure -> {
            if (rootCause(failure) instanceof SQLException sql && sql.getMessage().contains("UNIQUE")) {
                return false;
            }
            throw new java.util.concurrent.CompletionException(failure);
        });
    }

    public CompletableFuture<Void> completeClaim(String transactionId, QuestAssignment assignment) {
        return submit(() -> transaction(() -> {
            long now = Instant.now().toEpochMilli();
            try (PreparedStatement transaction = connection.prepareStatement(
                    "UPDATE claim_transactions SET status='SUCCESS', detail='delivered', updated_at=? WHERE transaction_id=? AND status='RESERVED'")) {
                transaction.setLong(1, now);
                transaction.setString(2, transactionId);
                if (transaction.executeUpdate() != 1) {
                    throw new SQLException("Claim reservation is no longer active");
                }
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE assignments SET state='CLAIMED', claimed_at=? WHERE assignment_id=? AND state='CLAIMING'")) {
                update.setLong(1, now);
                update.setString(2, assignment.id().toString());
                if (update.executeUpdate() != 1) {
                    throw new SQLException("Assignment was not in CLAIMING state");
                }
            }
            insertHistory(assignment, AssignmentState.CLAIMED);
            try (PreparedStatement profile = connection.prepareStatement(
                    "UPDATE player_profiles SET completed_total=completed_total+1, updated_at=? WHERE player_uuid=?")) {
                profile.setLong(1, now);
                profile.setString(2, assignment.playerId().toString());
                profile.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> rollbackClaim(String transactionId, QuestAssignment assignment, String detail) {
        return submit(() -> transaction(() -> {
            long now = Instant.now().toEpochMilli();
            try (PreparedStatement transaction = connection.prepareStatement(
                    "UPDATE claim_transactions SET status='ROLLED_BACK', detail=?, updated_at=? WHERE transaction_id=? AND status='RESERVED'")) {
                transaction.setString(1, LogSanitizer.clean(detail));
                transaction.setLong(2, now);
                transaction.setString(3, transactionId);
                transaction.executeUpdate();
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE assignments SET state='COMPLETED' WHERE assignment_id=? AND state='CLAIMING'")) {
                update.setString(1, assignment.id().toString());
                update.executeUpdate();
            }
            return null;
        }));
    }

    public CompletableFuture<Void> uncertainClaim(String transactionId, QuestAssignment assignment, String detail) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE claim_transactions SET status='UNCERTAIN', detail=?, updated_at=? WHERE transaction_id=?")) {
                statement.setString(1, LogSanitizer.clean(detail));
                statement.setLong(2, Instant.now().toEpochMilli());
                statement.setString(3, transactionId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<List<HistoryEntry>> history(UUID playerId, int limit, int offset) {
        return submit(() -> {
            List<HistoryEntry> output = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT history_id, assignment_id, quest_id, display_name, rarity, scope, state,
                      assigned_at, completed_at, claimed_at, objective_summary, reward_summary
                    FROM quest_history WHERE player_uuid=? ORDER BY history_id DESC LIMIT ? OFFSET ?
                    """)) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, Math.max(1, Math.min(100, limit)));
                statement.setInt(3, Math.max(0, offset));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        output.add(new HistoryEntry(
                                result.getLong("history_id"),
                                UUID.fromString(result.getString("assignment_id")),
                                result.getString("quest_id"),
                                result.getString("display_name"),
                                result.getString("rarity"),
                                QuestScope.valueOf(result.getString("scope")),
                                AssignmentState.valueOf(result.getString("state")),
                                instant(result, "assigned_at"),
                                nullableInstant(result, "completed_at"),
                                nullableInstant(result, "claimed_at"),
                                result.getString("objective_summary"),
                                result.getString("reward_summary")));
                    }
                }
            }
            return List.copyOf(output);
        });
    }

    public CompletableFuture<Set<String>> recentQuestIds(UUID playerId, Instant since) {
        return submit(() -> {
            Set<String> ids = new java.util.HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT DISTINCT quest_id FROM assignments WHERE player_uuid=? AND assigned_at>=?")) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, since.toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        ids.add(result.getString(1));
                    }
                }
            }
            return Set.copyOf(ids);
        });
    }

    public CompletableFuture<Long> serverSeed() {
        return submit(() -> transaction(() -> {
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT state_value FROM rotation_state WHERE state_key='server_seed'")) {
                try (ResultSet result = select.executeQuery()) {
                    if (result.next()) {
                        return Long.parseLong(result.getString(1));
                    }
                }
            }
            long seed = new java.security.SecureRandom().nextLong();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO rotation_state(state_key, state_value, updated_at) VALUES('server_seed',?,?)")) {
                insert.setString(1, Long.toString(seed));
                insert.setLong(2, Instant.now().toEpochMilli());
                insert.executeUpdate();
            }
            return seed;
        }));
    }

    public CompletableFuture<Integer> cancelAssignments(UUID playerId, QuestScope scope) {
        return submit(() -> {
            String sql = scope == null
                    ? "UPDATE assignments SET state='CANCELLED' WHERE player_uuid=? AND state IN ('ACTIVE','COMPLETED')"
                    : "UPDATE assignments SET state='CANCELLED' WHERE player_uuid=? AND scope=? AND state IN ('ACTIVE','COMPLETED')";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerId.toString());
                if (scope != null) {
                    statement.setString(2, scope.name());
                }
                return statement.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> audit(
            String actor, UUID playerId, UUID assignmentId, String action, String reason, String oldValue, String newValue) {
        return submit(() -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO admin_audit(actor, player_uuid, assignment_id, action, reason,
                      old_value, new_value, created_at) VALUES(?,?,?,?,?,?,?,?)
                    """)) {
                statement.setString(1, LogSanitizer.clean(actor));
                nullableString(statement, 2, playerId == null ? null : playerId.toString());
                nullableString(statement, 3, assignmentId == null ? null : assignmentId.toString());
                statement.setString(4, LogSanitizer.clean(action));
                statement.setString(5, LogSanitizer.clean(reason));
                statement.setString(6, LogSanitizer.clean(oldValue));
                statement.setString(7, LogSanitizer.clean(newValue));
                statement.setLong(8, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> checkpoint() {
        return submit(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
            return null;
        });
    }

    public CompletableFuture<Path> backup(String fileName) {
        return submit(() -> {
            Path backups = dataDirectory.resolve("backups").normalize();
            Files.createDirectories(backups);
            Path target = AtomicFiles.resolveInside(backups, fileName);
            if (Files.exists(target)) {
                throw new IOException("Backup already exists: " + target.getFileName());
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(FULL)");
                String escaped = target.toString().replace("'", "''");
                statement.execute("VACUUM INTO '" + escaped + "'");
            }
            return target;
        });
    }

    public StorageDiagnostics diagnostics() {
        long uncertain = -1L;
        return new StorageDiagnostics(
                accepting.get() && connection != null,
                writer.getQueue().size(),
                settings.queueCapacity(),
                dirty.size(),
                lastFlushDuration,
                lastFlushResult,
                lastFlushAt,
                rejectedOperations.get(),
                uncertain);
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version > 1) {
            throw new SQLException("Database schema " + version + " is newer than this plugin supports");
        }
        if (version == 0) {
            transaction(() -> {
                try (Statement statement = connection.createStatement()) {
                    for (String sql : MIGRATION_V1) {
                        statement.execute(sql);
                    }
                    statement.execute("PRAGMA user_version=1");
                }
                return null;
            });
        }
    }

    private void ensureProfile(UUID playerId, String latestName) throws SQLException {
        long now = Instant.now().toEpochMilli();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO player_profiles(player_uuid, latest_name, settings_mask, created_at, updated_at)
                VALUES(?,?,?,?,?)
                ON CONFLICT(player_uuid) DO UPDATE SET latest_name=excluded.latest_name, updated_at=excluded.updated_at
                """)) {
            statement.setString(1, playerId.toString());
            statement.setString(2, LogSanitizer.clean(latestName));
            statement.setInt(3, FeedbackPreferences.defaults().mask());
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
        }
    }

    private boolean insertAssignmentRow(QuestAssignment assignment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT OR IGNORE INTO assignments(assignment_id, player_uuid, quest_id, revision,
                  fingerprint, scope, pool_id, period_key, state, assigned_at, expires_at,
                  completed_at, claimed_at, definition_snapshot)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, assignment.id().toString());
            statement.setString(2, assignment.playerId().toString());
            statement.setString(3, assignment.definition().id());
            statement.setInt(4, assignment.definition().revision());
            statement.setString(5, assignment.definition().fingerprint());
            statement.setString(6, assignment.definition().scope().name());
            statement.setString(7, assignment.poolId());
            statement.setString(8, assignment.periodKey());
            statement.setString(9, assignment.state().name());
            statement.setLong(10, assignment.assignedAt().toEpochMilli());
            nullableLong(statement, 11, assignment.expiresAt().map(Instant::toEpochMilli).orElse(null));
            nullableLong(statement, 12, assignment.completedAt().map(Instant::toEpochMilli).orElse(null));
            nullableLong(statement, 13, assignment.claimedAt().map(Instant::toEpochMilli).orElse(null));
            statement.setString(14, codec.encode(assignment.definition()));
            if (statement.executeUpdate() != 1) {
                return false;
            }
        }
        for (ObjectiveProgress objective : assignment.objectives()) {
            upsertObjective(assignment.id(), objective);
        }
        return true;
    }

    private Map<String, Long> loadObjectiveProgress(UUID assignmentId) throws SQLException {
        Map<String, Long> progress = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT objective_id, current_amount FROM assignment_objectives WHERE assignment_id=?")) {
            statement.setString(1, assignmentId.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    progress.put(result.getString(1), result.getLong(2));
                }
            }
        }
        return progress;
    }

    private void persistDirty(DirtyAssignment assignment) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE assignments SET state=?, completed_at=?, claimed_at=? WHERE assignment_id=?
                """)) {
            statement.setString(1, assignment.state().name());
            nullableLong(statement, 2, assignment.completedAt());
            nullableLong(statement, 3, assignment.claimedAt());
            statement.setString(4, assignment.assignmentId().toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Dirty assignment no longer exists: " + assignment.assignmentId());
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE assignment_objectives SET current_amount=?, completed=?
                WHERE assignment_id=? AND objective_id=?
                """)) {
            for (DirtyObjective objective : assignment.objectives()) {
                statement.setLong(1, objective.current());
                statement.setInt(2, objective.current() >= objective.required() ? 1 : 0);
                statement.setString(3, assignment.assignmentId().toString());
                statement.setString(4, objective.id());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void upsertObjective(UUID assignmentId, ObjectiveProgress objective) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO assignment_objectives(assignment_id, objective_id, objective_type,
                  required_amount, current_amount, completed) VALUES(?,?,?,?,?,?)
                ON CONFLICT(assignment_id, objective_id) DO UPDATE SET
                  current_amount=excluded.current_amount, completed=excluded.completed
                """)) {
            statement.setString(1, assignmentId.toString());
            statement.setString(2, objective.definition().id());
            statement.setString(3, objective.definition().type().name());
            statement.setLong(4, objective.required());
            statement.setLong(5, objective.current());
            statement.setInt(6, objective.complete() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    private void insertHistory(QuestAssignment assignment, AssignmentState state) throws SQLException {
        String objectives = assignment.objectives().stream()
                .map(objective -> objective.definition().id() + "=" + objective.current() + "/" + objective.required())
                .collect(java.util.stream.Collectors.joining(","));
        String rewards = assignment.definition().rewards().entries().stream()
                .map(reward -> reward.id() + "=" + reward.display())
                .collect(java.util.stream.Collectors.joining(","));
        try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT OR IGNORE INTO quest_history(player_uuid, assignment_id, quest_id, display_name,
                  rarity, scope, state, assigned_at, completed_at, claimed_at, objective_summary, reward_summary)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            statement.setString(1, assignment.playerId().toString());
            statement.setString(2, assignment.id().toString());
            statement.setString(3, assignment.definition().id());
            statement.setString(4, assignment.definition().display().name());
            statement.setString(5, assignment.definition().rarity());
            statement.setString(6, assignment.definition().scope().name());
            statement.setString(7, state.name());
            statement.setLong(8, assignment.assignedAt().toEpochMilli());
            nullableLong(statement, 9, assignment.completedAt().map(Instant::toEpochMilli).orElse(null));
            Long claimedAt = assignment.claimedAt().map(Instant::toEpochMilli).orElse(null);
            if (claimedAt == null && state == AssignmentState.CLAIMED) {
                claimedAt = Instant.now().toEpochMilli();
            }
            nullableLong(statement, 10, claimedAt);
            statement.setString(11, objectives);
            statement.setString(12, rewards);
            statement.executeUpdate();
        }
    }

    private <T> T transaction(SqlCallable<T> operation) throws SQLException, IOException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = operation.call();
            connection.commit();
            return result;
        } catch (SQLException | IOException | RuntimeException exception) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private <T> CompletableFuture<T> submit(SqlCallable<T> operation) {
        if (!accepting.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Quest storage is not accepting operations"));
        }
        return submitInternal(operation);
    }

    private <T> CompletableFuture<T> submitInternal(SqlCallable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            writer.execute(() -> {
                try {
                    future.complete(operation.call());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    @Override
    public void close() {
        if (!accepting.getAndSet(false)) {
            writer.shutdownNow();
            return;
        }
        Map<UUID, DirtyAssignment> finalBatch = new LinkedHashMap<>(dirty);
        dirty.clear();
        CompletableFuture<Void> closing = submitInternal(() -> {
            if (!finalBatch.isEmpty()) {
                transaction(() -> {
                    for (DirtyAssignment assignment : finalBatch.values()) {
                        persistDirty(assignment);
                    }
                    return null;
                });
            }
            if (connection != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                connection.close();
            }
            return null;
        });
        try {
            closing.get(settings.shutdownTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Interrupted while saving final PlexonQuests data", exception);
        } catch (ExecutionException | TimeoutException exception) {
            logger.log(Level.SEVERE, "PlexonQuests final persistence did not complete safely", exception);
        } finally {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(Math.max(1L, settings.shutdownTimeout().toSeconds()), TimeUnit.SECONDS)) {
                    logger.severe("PlexonQuests storage writer did not terminate within the configured timeout");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        return Instant.ofEpochMilli(result.getLong(column));
    }

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static void nullableLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static void nullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    private interface SqlCallable<T> {
        T call() throws SQLException, IOException;
    }

    private record DirtyObjective(String id, long required, long current) {}

    private record DirtyAssignment(
            UUID assignmentId,
            AssignmentState state,
            Long completedAt,
            Long claimedAt,
            List<DirtyObjective> objectives) {

        private static DirtyAssignment capture(QuestAssignment assignment) {
            List<DirtyObjective> objectives = assignment.objectives().stream()
                    .map(objective -> new DirtyObjective(
                            objective.definition().id(), objective.required(), objective.current()))
                    .toList();
            return new DirtyAssignment(
                    assignment.id(),
                    assignment.state(),
                    assignment.completedAt().map(Instant::toEpochMilli).orElse(null),
                    assignment.claimedAt().map(Instant::toEpochMilli).orElse(null),
                    objectives);
        }

        private DirtyAssignment withState(AssignmentState replacement) {
            return new DirtyAssignment(assignmentId, replacement, completedAt, claimedAt, objectives);
        }
    }
}

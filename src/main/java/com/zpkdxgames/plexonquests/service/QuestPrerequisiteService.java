package com.zpkdxgames.plexonquests.service;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Additive 4.x prerequisite graph. Candidate graphs are fully validated before activation.
 * Existing QuestDefinition snapshots remain unchanged for 3.3.1 persistence compatibility.
 */
public final class QuestPrerequisiteService {
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]+");
    private final ConfigManager configs;
    private final AtomicReference<Snapshot> active = new AtomicReference<>(Snapshot.empty());

    public QuestPrerequisiteService(ConfigManager configs) {
        this.configs = Objects.requireNonNull(configs, "configs");
    }

    public ValidationResult validateCandidate() {
        return validate(configs.dataDirectory());
    }

    public ValidationResult reloadValidated() {
        ValidationResult result = validate(configs.dataDirectory());
        if (result.valid()) {
            active.set(result.snapshot());
        }
        return result;
    }

    /** Pure filesystem preflight used by ConfigManager before it atomically swaps a candidate registry. */
    public static ValidationResult validate(Path dataDirectory) {
        Path base = Objects.requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().normalize();
        Path root = base.resolve("quests").normalize();
        List<String> errors = new ArrayList<>();
        Map<String, Node> nodes = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return new ValidationResult(false, Snapshot.empty(), List.of("quests directory is missing"));
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(value -> value.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .sorted().toList()) {
                parse(path, base, nodes, errors);
            }
        } catch (IOException exception) {
            errors.add("Could not scan quest graph: " + safe(exception.getMessage()));
        }

        for (Node node : nodes.values()) {
            for (String dependency : node.dependencies()) {
                if (dependency.equals(node.id())) {
                    errors.add(node.source() + ": quest " + node.id() + " cannot depend on itself");
                    continue;
                }
                Node target = nodes.get(dependency);
                if (target == null) {
                    errors.add(node.source() + ": missing prerequisite quest " + dependency);
                } else if (!target.enabled()) {
                    errors.add(node.source() + ": prerequisite quest " + dependency + " is disabled and makes "
                            + node.id() + " unreachable");
                }
            }
        }
        detectCycles(nodes, errors);

        if (!errors.isEmpty()) {
            return new ValidationResult(false, Snapshot.empty(), List.copyOf(errors));
        }
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        nodes.forEach((id, node) -> graph.put(id, Set.copyOf(node.dependencies())));
        Snapshot snapshot = new Snapshot(Map.copyOf(graph), Instant.now(), nodes.size());
        return new ValidationResult(true, snapshot, List.of());
    }

    public Snapshot snapshot() {
        return active.get();
    }

    public Set<String> prerequisites(String questId) {
        return active.get().prerequisites().getOrDefault(normalize(questId), Set.of());
    }

    public Set<String> missing(UUIDView completed, String questId) {
        Set<String> required = prerequisites(questId);
        if (required.isEmpty()) {
            return Set.of();
        }
        Set<String> missing = new LinkedHashSet<>();
        for (String id : required) {
            if (!completed.contains(id)) {
                missing.add(id);
            }
        }
        return Set.copyOf(missing);
    }

    private static void parse(
            Path path, Path dataDirectory, Map<String, Node> nodes, List<String> errors) {
        String source;
        try {
            source = dataDirectory.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException ignored) {
            source = path.getFileName().toString();
        }
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(Files.readString(path));
            String id = normalize(yaml.getString("id", ""));
            if (!ID.matcher(id).matches()) {
                errors.add(source + ": invalid quest id for prerequisite graph");
                return;
            }
            Set<String> dependencies = new LinkedHashSet<>();
            for (String raw : yaml.getStringList("prerequisites.completed-quests")) {
                String dependency = normalize(raw);
                if (!ID.matcher(dependency).matches()) {
                    errors.add(source + ": invalid prerequisite id " + raw);
                } else {
                    dependencies.add(dependency);
                }
            }
            Node previous = nodes.putIfAbsent(id,
                    new Node(id, yaml.getBoolean("enabled", true), Set.copyOf(dependencies), source));
            if (previous != null) {
                errors.add(source + ": duplicate quest id " + id + " also defined by " + previous.source());
            }
        } catch (IOException | InvalidConfigurationException exception) {
            errors.add(source + ": " + safe(exception.getMessage()));
        }
    }

    private static void detectCycles(Map<String, Node> nodes, List<String> errors) {
        Map<String, Visit> visits = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> reportedCycles = new LinkedHashSet<>();
        for (String id : nodes.keySet()) {
            visit(id, nodes, visits, stack, reportedCycles, errors);
        }
    }

    private static void visit(
            String id,
            Map<String, Node> nodes,
            Map<String, Visit> visits,
            Deque<String> stack,
            Set<String> reportedCycles,
            List<String> errors) {
        Visit state = visits.get(id);
        if (state == Visit.DONE) {
            return;
        }
        if (state == Visit.ACTIVE) {
            List<String> cycle = new ArrayList<>();
            boolean collect = false;
            for (String value : stack) {
                if (value.equals(id)) {
                    collect = true;
                }
                if (collect) {
                    cycle.add(value);
                }
            }
            cycle.add(id);
            String signature = String.join(" -> ", cycle);
            if (reportedCycles.add(signature)) {
                errors.add("quest-graph: prerequisite cycle " + signature);
            }
            return;
        }
        Node node = nodes.get(id);
        if (node == null) {
            return;
        }
        visits.put(id, Visit.ACTIVE);
        stack.addLast(id);
        for (String dependency : node.dependencies()) {
            if (nodes.containsKey(dependency)) {
                visit(dependency, nodes, visits, stack, reportedCycles, errors);
            }
        }
        stack.removeLast();
        visits.put(id, Visit.DONE);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown error" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private enum Visit { ACTIVE, DONE }

    private record Node(String id, boolean enabled, Set<String> dependencies, String source) {}

    @FunctionalInterface
    public interface UUIDView {
        boolean contains(String questId);
    }

    public record Snapshot(Map<String, Set<String>> prerequisites, Instant loadedAt, int questCount) {
        public Snapshot {
            Map<String, Set<String>> copy = new LinkedHashMap<>();
            prerequisites.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            prerequisites = Collections.unmodifiableMap(copy);
            loadedAt = Objects.requireNonNull(loadedAt, "loadedAt");
        }

        private static Snapshot empty() {
            return new Snapshot(Map.of(), Instant.EPOCH, 0);
        }
    }

    public record ValidationResult(boolean valid, Snapshot snapshot, List<String> errors) {
        public ValidationResult {
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            errors = List.copyOf(errors);
        }
    }
}

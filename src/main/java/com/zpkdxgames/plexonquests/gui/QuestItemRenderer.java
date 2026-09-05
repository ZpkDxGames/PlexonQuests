package com.zpkdxgames.plexonquests.gui;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.presentation.ItemFactory;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.ObjectiveProgress;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.reward.RewardDefinition;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class QuestItemRenderer {
    private final ConfigManager configs;
    private final TextService text;
    private final ItemFactory items;

    public QuestItemRenderer(ConfigManager configs, TextService text, ItemFactory items) {
        this.configs = configs;
        this.text = text;
        this.items = items;
    }

    public ItemStack questCard(Player player, QuestAssignment assignment, boolean pinned) {
        QuestAssignment.ProgressSummary progress = assignment.displayProgress();
        double percentage = progress.percentage();
        Map<String, String> values = text.placeholders(
                "scope", assignment.definition().scope().name(),
                "category", humanize(assignment.definition().category()),
                "completion_rule", completionRule(assignment),
                "status", statusLabel(assignment.state()),
                "status_color", statusColor(assignment.state()),
                "current", formatAssignmentValue(assignment, progress.current()),
                "required", formatAssignmentValue(assignment, progress.required()),
                "percentage", Integer.toString((int) Math.floor(percentage)),
                "progress_color", text.progressColor(percentage),
                "time_left", timeLeft(assignment),
                "pin_action", pinned ? "unpin" : "pin");
        Component rarity = configs.snapshot().registry().rarities().containsKey(assignment.definition().rarity())
                ? text.parse(configs.snapshot().registry().rarities().get(assignment.definition().rarity()).display())
                : Component.text(assignment.definition().rarity());
        Map<String, Component> components = Map.of(
                "rarity", rarity,
                "description", text.parse(assignment.definition().display().shortDescription()),
                "progress_bar", text.progressBar(percentage));
        Map<String, List<Component>> expansions = Map.of(
                "objectives", objectiveSummary(assignment),
                "actions", actionSummary(player, assignment, pinned));
        List<Component> lore = text.expandLines(
                configs.snapshot().menus().strings("journal.quest-card.lore"), values, components, expansions);
        boolean glow = assignment.state() == AssignmentState.COMPLETED
                && assignment.definition().display().icon().glowWhenComplete();
        return items.create(
                assignment.definition().display().icon().material(),
                text.parse(assignment.definition().display().name()),
                lore,
                glow);
    }

    public ItemStack detailHeader(QuestAssignment assignment) {
        QuestAssignment.ProgressSummary progress = assignment.displayProgress();
        double percentage = progress.percentage();
        Map<String, String> values = text.placeholders(
                "scope", assignment.definition().scope().name(),
                "category", humanize(assignment.definition().category()),
                "completion_rule", completionRule(assignment),
                "status", statusLabel(assignment.state()),
                "status_color", statusColor(assignment.state()),
                "current", formatAssignmentValue(assignment, progress.current()),
                "required", formatAssignmentValue(assignment, progress.required()),
                "percentage", Integer.toString((int) Math.floor(percentage)),
                "progress_color", text.progressColor(percentage),
                "time_left", timeLeft(assignment));
        Component rarity = configs.snapshot().registry().rarities().containsKey(assignment.definition().rarity())
                ? text.parse(configs.snapshot().registry().rarities().get(assignment.definition().rarity()).display())
                : Component.text(assignment.definition().rarity());
        Map<String, Component> components = Map.of(
                "rarity", rarity,
                "description", text.parse(assignment.definition().display().shortDescription()),
                "progress_bar", text.progressBar(percentage));
        List<Component> lore = text.expandLines(
                configs.snapshot().menus().strings("details.header.lore"),
                values,
                components,
                Map.of("requirements", requirementSummary(assignment)));
        boolean glow = assignment.state() == AssignmentState.COMPLETED
                && assignment.definition().display().icon().glowWhenComplete();
        return items.create(
                assignment.definition().display().icon().material(),
                text.parse(assignment.definition().display().name()),
                lore,
                glow);
    }

    public ItemStack objectiveCard(ObjectiveProgress objective, boolean locked) {
        double percentage = objective.required() == 0L
                ? 0D
                : Math.min(100D, objective.current() * 100D / objective.required());
        String stateColor = locked ? "#8B95A7" : objective.complete() ? "#72F1B8" : "#56B9F2";
        Material material = locked
                ? material("details.objective.locked-material", Material.GRAY_DYE)
                : objective.complete()
                        ? material("details.objective.complete-material", Material.EMERALD)
                        : material("details.objective.active-material", Material.LIME_DYE);
        Map<String, String> values = text.placeholders(
                "objective_state_color", stateColor,
                "objective_name", objective.definition().display(),
                "objective_state", locked ? "Locked" : objective.complete() ? "Complete" : "In progress",
                "objective_type", humanize(objective.definition().type().name()),
                "current", formatObjectiveValue(objective, objective.current()),
                "required", formatObjectiveValue(objective, objective.required()),
                "remaining", formatObjectiveValue(objective, Math.max(0L, objective.required() - objective.current())),
                "progress_color", text.progressColor(percentage));
        Map<String, Component> components = Map.of("progress_bar", text.progressBar(percentage));
        List<Component> filters = filterSummary(objective);
        List<Component> lore = text.expandLines(
                configs.snapshot().menus().strings("details.objective.lore"),
                values,
                components,
                Map.of("filters", filters));
        return items.create(
                material,
                text.parse(configs.snapshot().menus().string("details.objective.name", "<white><objective_name>"), values),
                lore,
                objective.complete());
    }

    public ItemStack rewardCard(RewardDefinition reward) {
        Material material = reward.material() == null ? Material.CHEST : reward.material();
        List<Component> lore = List.of(
                text.parse("<dark_gray>" + humanize(reward.type().name())),
                Component.empty(),
                text.parse("<gray>Delivered when you claim this quest."));
        return items.create(material, text.parse(reward.display()), lore, false);
    }

    public ItemStack configured(String path, Map<String, String> values) {
        return configured(path, values, Map.of(), false);
    }

    public ItemStack configured(
            String path, Map<String, String> values, Map<String, Component> components) {
        return configured(path, values, components, false);
    }

    public ItemStack configured(
            String path,
            Map<String, String> values,
            Map<String, Component> components,
            boolean glow) {
        Material material = material(path + ".material", Material.PAPER);
        Component name = text.parse(configs.snapshot().menus().string(path + ".name", " "), values);
        List<Component> lore = configs.snapshot().menus().strings(path + ".lore").stream()
                .map(line -> text.parse(null, line, values, components))
                .toList();
        return items.create(material, name, lore, glow);
    }

    public ItemStack filler() {
        return configured("common.filler", Map.of());
    }

    private List<Component> objectiveSummary(QuestAssignment assignment) {
        List<Component> output = new ArrayList<>();
        int maximum = Math.max(1, Math.min(4,
                configs.snapshot().menus().integer("journal.quest-card.objectives-shown", 2)));
        int shown = 0;
        for (ObjectiveProgress objective : assignment.objectives()) {
            if (shown++ >= maximum) {
                output.add(text.parse("<dark_gray>+" + (assignment.objectives().size() - maximum) + " more objective(s)"));
                break;
            }
            String icon = objective.complete() ? "<green>✔" : "<dark_gray>•";
            output.add(text.parse(
                    icon + " <gray><name> <progress_color><current></progress_color><dark_gray>/</dark_gray><white><required>",
                    text.placeholders(
                            "name", objective.definition().display(),
                            "progress_color", text.progressColor(objective.current() * 100D / objective.required()),
                            "current", formatObjectiveValue(objective, objective.current()),
                            "required", formatObjectiveValue(objective, objective.required()))));
        }
        return List.copyOf(output);
    }

    private List<Component> actionSummary(Player player, QuestAssignment assignment, boolean pinned) {
        List<Component> output = new ArrayList<>();
        String firstLine = "<yellow>Left-click <gray>details";
        if (player.hasPermission("plexonquests.pin")) {
            firstLine += " <dark_gray>• <aqua>Right-click <gray>" + (pinned ? "unpin" : "pin");
        }
        output.add(text.parse(firstLine));
        if (assignment.state() == AssignmentState.ACTIVE
                && assignment.definition().scope().rotating()
                && configs.snapshot().settings().rerolls().enabled()
                && player.hasPermission("plexonquests.reroll")) {
            output.add(text.parse("<light_purple>Shift-left-click <gray>reroll"));
        }
        return List.copyOf(output);
    }

    private static String statusLabel(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "In progress";
            case COMPLETED -> "Ready to claim";
            case CLAIMING -> "Claiming";
            case CLAIMED -> "Claimed";
            case EXPIRED -> "Expired";
            case CANCELLED -> "Cancelled";
        };
    }

    private static String statusColor(AssignmentState state) {
        return switch (state) {
            case ACTIVE -> "#56B9F2";
            case COMPLETED, CLAIMED -> "#72F1B8";
            case CLAIMING -> "#FFD166";
            case EXPIRED, CANCELLED -> "#8B95A7";
        };
    }

    private List<Component> filterSummary(ObjectiveProgress objective) {
        List<Component> output = new ArrayList<>();
        var filters = objective.definition().filters();
        if (!filters.materials().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Counts <white>" + summarize(filters.materials(), 4)));
        }
        if (!filters.caughtMaterials().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Catches <white>" + summarize(filters.caughtMaterials(), 4)));
        }
        if (!filters.entityTypes().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Targets <white>" + summarize(filters.entityTypes(), 4)));
        }
        if (!filters.damageCauses().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Damage <white>" + summarize(filters.damageCauses(), 3)));
        }
        if (!filters.spawnReasons().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Spawns <white>" + summarize(filters.spawnReasons(), 3)));
        }
        if (filters.origin() == OriginPolicy.NATURAL_ONLY) {
            output.add(text.parse("<dark_gray>• <gray>Natural blocks only"));
        } else if (filters.origin() == OriginPolicy.PLAYER_PLACED_ONLY) {
            output.add(text.parse("<dark_gray>• <gray>Player-placed blocks only"));
        }
        if (filters.matureOnly()) {
            output.add(text.parse("<dark_gray>• <gray>Fully grown crops only"));
        }
        if (filters.hostileOnly()) {
            output.add(text.parse("<dark_gray>• <gray>Hostile creatures only"));
        }
        if (filters.uniqueOnly()) {
            output.add(text.parse("<dark_gray>• <gray>Each unique target counts once"));
        }
        if (!filters.worlds().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Worlds: <white><worlds>",
                    Map.of("worlds", String.join(", ", filters.worlds()))));
        }
        if (!filters.worldEnvironments().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Dimension <white>" + summarize(filters.worldEnvironments(), 3)));
        }
        if (!filters.movementTypes().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Movement <white>" + summarize(filters.movementTypes(), 4)));
        }
        if (!filters.gameModes().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Mode <white>" + summarize(filters.gameModes(), 3)));
        }
        if (!filters.advancementKeys().isEmpty()) {
            output.add(text.parse("<dark_gray>• <gray>Advancements <white>" + summarize(filters.advancementKeys(), 3)));
        }
        return List.copyOf(output);
    }

    private List<Component> requirementSummary(QuestAssignment assignment) {
        List<Component> output = new ArrayList<>();
        output.add(text.parse("<gray>Completion <white>" + completionRule(assignment)));
        var eligibility = assignment.definition().eligibility();
        if (!eligibility.requiredIntegrations().isEmpty()) {
            output.add(text.parse("<gray>Requires <white>" + summarize(eligibility.requiredIntegrations(), 3)));
        }
        if (!eligibility.rankCategories().isEmpty()) {
            output.add(text.parse("<gray>Ranks <white>" + summarize(eligibility.rankCategories(), 4)));
        }
        if (!eligibility.worlds().isEmpty()) {
            output.add(text.parse("<gray>Available in <white>" + summarize(eligibility.worlds(), 4)));
        }
        if (!eligibility.requiredPermission().isBlank()) {
            output.add(text.parse("<gray>Requires a server permission"));
        }
        return List.copyOf(output);
    }

    private String formatAssignmentValue(QuestAssignment assignment, long value) {
        boolean playTimeOnly = !assignment.objectives().isEmpty()
                && assignment.objectives().stream()
                        .allMatch(objective -> objective.definition().type() == ObjectiveType.PLAY_TIME);
        return playTimeOnly ? text.formatDuration(Duration.ofSeconds(value)) : text.formatNumber(value);
    }

    private String formatObjectiveValue(ObjectiveProgress objective, long value) {
        return objective.definition().type() == ObjectiveType.PLAY_TIME
                ? text.formatDuration(Duration.ofSeconds(value))
                : text.formatNumber(value);
    }

    private static String completionRule(QuestAssignment assignment) {
        return switch (assignment.definition().completionMode()) {
            case ALL -> "Complete every objective";
            case ANY -> "Complete any one objective";
            case SEQUENCE -> "Complete objectives in order";
        };
    }

    private static String summarize(Collection<?> values, int maximum) {
        List<String> labels = values.stream()
                .map(value -> String.valueOf(value))
                .map(QuestItemRenderer::humanize)
                .sorted()
                .toList();
        String visible = String.join(", ", labels.stream().limit(maximum).toList());
        int remaining = labels.size() - maximum;
        return remaining > 0 ? visible + " +" + remaining : visible;
    }

    private static String humanize(String value) {
        String normalized = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder output = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (int index = 0; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            output.append(capitalize ? Character.toUpperCase(character) : character);
            capitalize = character == ' ';
        }
        return output.toString();
    }

    private String timeLeft(QuestAssignment assignment) {
        return assignment.expiresAt()
                .map(expiry -> text.formatDuration(Duration.between(Instant.now(), expiry)))
                .orElse("No expiry");
    }

    private Material material(String path, Material fallback) {
        Material material = Material.matchMaterial(configs.snapshot().menus().string(path, fallback.name()));
        return material == null || material.isAir() ? fallback : material;
    }
}

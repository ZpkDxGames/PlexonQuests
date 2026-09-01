package com.zpkdxgames.plexonquests.persistence;

import com.zpkdxgames.plexonquests.objective.ObjectiveDefinition;
import com.zpkdxgames.plexonquests.objective.ObjectiveFilters;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.quest.ClaimMode;
import com.zpkdxgames.plexonquests.quest.CompletionMode;
import com.zpkdxgames.plexonquests.quest.IconDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestDisplay;
import com.zpkdxgames.plexonquests.quest.QuestEligibility;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.reward.RewardBundle;
import com.zpkdxgames.plexonquests.reward.RewardDefinition;
import com.zpkdxgames.plexonquests.reward.RewardMode;
import com.zpkdxgames.plexonquests.reward.RewardType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class AssignmentSnapshotCodec {
    private static final int MAGIC = 0x5051534E;
    private static final int VERSION = 1;
    private static final int MAX_COLLECTION = 2048;
    private static final int MAX_STRING_BYTES = 1_048_576;

    public String encode(QuestDefinition quest) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                string(output, quest.id());
                output.writeInt(quest.revision());
                output.writeBoolean(quest.enabled());
                string(output, quest.scope().name());
                string(output, quest.category());
                string(output, quest.rarity());
                output.writeInt(quest.weight());
                eligibility(output, quest.eligibility());
                display(output, quest.display());
                string(output, quest.completionMode().name());
                string(output, quest.claimMode().name());
                output.writeInt(quest.objectives().size());
                for (ObjectiveDefinition objective : quest.objectives().values()) {
                    objective(output, objective);
                }
                rewards(output, quest.rewards());
                string(output, quest.completeEffect());
                string(output, quest.claimEffect());
                string(output, quest.fingerprint());
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode quest snapshot", exception);
        }
    }

    public QuestDefinition decode(String encoded) throws IOException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Quest snapshot is not valid Base64", exception);
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IOException("Unsupported quest snapshot format");
            }
            String id = string(input);
            int revision = input.readInt();
            boolean enabled = input.readBoolean();
            QuestScope scope = enumValue(QuestScope.class, string(input));
            String category = string(input);
            String rarity = string(input);
            int weight = input.readInt();
            QuestEligibility eligibility = eligibility(input);
            QuestDisplay display = display(input);
            CompletionMode completionMode = enumValue(CompletionMode.class, string(input));
            ClaimMode claimMode = enumValue(ClaimMode.class, string(input));
            int objectiveCount = count(input);
            Map<String, ObjectiveDefinition> objectives = new LinkedHashMap<>();
            for (int index = 0; index < objectiveCount; index++) {
                ObjectiveDefinition objective = objective(input);
                objectives.put(objective.id(), objective);
            }
            RewardBundle rewards = rewards(input);
            String completeEffect = string(input);
            String claimEffect = string(input);
            String fingerprint = string(input);
            if (input.available() != 0) {
                throw new IOException("Quest snapshot contains trailing data");
            }
            return new QuestDefinition(
                    id, revision, enabled, scope, category, rarity, weight, eligibility, display,
                    completionMode, claimMode, objectives, rewards, completeEffect, claimEffect,
                    fingerprint, "database-snapshot");
        } catch (EOFException exception) {
            throw new IOException("Quest snapshot is truncated", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Quest snapshot contains an invalid value: " + exception.getMessage(), exception);
        }
    }

    private static void eligibility(DataOutputStream output, QuestEligibility eligibility) throws IOException {
        string(output, eligibility.requiredPermission());
        strings(output, eligibility.blockedPermissions());
        strings(output, eligibility.rankCategories());
        strings(output, eligibility.worlds());
        strings(output, eligibility.requiredIntegrations());
    }

    private static QuestEligibility eligibility(DataInputStream input) throws IOException {
        return new QuestEligibility(string(input), strings(input), strings(input), strings(input), strings(input));
    }

    private static void display(DataOutputStream output, QuestDisplay display) throws IOException {
        IconDefinition icon = display.icon();
        string(output, icon.material().name());
        output.writeInt(icon.amount());
        output.writeBoolean(icon.glowWhenComplete());
        output.writeBoolean(icon.customModelData() != null);
        if (icon.customModelData() != null) {
            output.writeInt(icon.customModelData());
        }
        string(output, icon.texture());
        string(output, icon.serializedItem());
        string(output, display.name());
        string(output, display.shortDescription());
        string(output, display.loreTemplate());
    }

    private static QuestDisplay display(DataInputStream input) throws IOException {
        Material material = enumValue(Material.class, string(input));
        int amount = input.readInt();
        boolean glow = input.readBoolean();
        Integer model = input.readBoolean() ? input.readInt() : null;
        String texture = string(input);
        String serialized = string(input);
        return new QuestDisplay(
                new IconDefinition(material, amount, glow, model, texture, serialized),
                string(input), string(input), string(input));
    }

    private static void objective(DataOutputStream output, ObjectiveDefinition objective) throws IOException {
        string(output, objective.id());
        string(output, objective.type().name());
        output.writeLong(objective.amount());
        string(output, objective.display());
        filters(output, objective.filters());
    }

    private static ObjectiveDefinition objective(DataInputStream input) throws IOException {
        return new ObjectiveDefinition(
                string(input), enumValue(ObjectiveType.class, string(input)), input.readLong(), string(input), filters(input));
    }

    private static void filters(DataOutputStream output, ObjectiveFilters filters) throws IOException {
        enums(output, filters.materials());
        enums(output, filters.caughtMaterials());
        enums(output, filters.entityTypes());
        enums(output, filters.damageCauses());
        enums(output, filters.spawnReasons());
        enums(output, filters.gameModes());
        strings(output, filters.worlds());
        enums(output, filters.worldEnvironments());
        strings(output, filters.movementTypes());
        strings(output, filters.advancementKeys());
        strings(output, filters.requiredPermissions());
        strings(output, filters.blockedPermissions());
        string(output, filters.origin().name());
        output.writeBoolean(filters.matureOnly());
        output.writeBoolean(filters.hostileOnly());
        output.writeBoolean(filters.uniqueOnly());
        output.writeBoolean(filters.excludeTeleports());
        output.writeLong(filters.minimumContribution());
        output.writeLong(filters.maximumContribution());
        output.writeLong(filters.cooldownMillis());
        stringMap(output, filters.extras());
    }

    private static ObjectiveFilters filters(DataInputStream input) throws IOException {
        Set<Material> materials = enums(input, Material.class);
        Set<Material> caught = enums(input, Material.class);
        Set<EntityType> entities = enums(input, EntityType.class);
        Set<EntityDamageEvent.DamageCause> causes = enums(input, EntityDamageEvent.DamageCause.class);
        Set<CreatureSpawnEvent.SpawnReason> reasons = enums(input, CreatureSpawnEvent.SpawnReason.class);
        Set<GameMode> modes = enums(input, GameMode.class);
        Set<String> worlds = strings(input);
        Set<World.Environment> environments = enums(input, World.Environment.class);
        Set<String> movement = strings(input);
        Set<String> advancements = strings(input);
        Set<String> requiredPermissions = strings(input);
        Set<String> blockedPermissions = strings(input);
        OriginPolicy origin = enumValue(OriginPolicy.class, string(input));
        boolean mature = input.readBoolean();
        boolean hostile = input.readBoolean();
        boolean unique = input.readBoolean();
        boolean excludeTeleports = input.readBoolean();
        long minimum = input.readLong();
        long maximum = input.readLong();
        long cooldown = input.readLong();
        Map<String, String> extras = stringMap(input);
        return new ObjectiveFilters(
                materials, caught, entities, causes, reasons, modes, worlds, environments, movement, advancements,
                requiredPermissions, blockedPermissions, origin, mature, hostile, unique, excludeTeleports,
                minimum, maximum, cooldown, extras);
    }

    private static void rewards(DataOutputStream output, RewardBundle rewards) throws IOException {
        string(output, rewards.mode().name());
        output.writeInt(rewards.entries().size());
        for (RewardDefinition reward : rewards.entries()) {
            string(output, reward.id());
            string(output, reward.type().name());
            output.writeLong(reward.amount());
            output.writeDouble(reward.decimalAmount());
            string(output, reward.material() == null ? "" : reward.material().name());
            string(output, reward.command());
            string(output, reward.permission());
            output.writeLong(reward.permissionDuration().toMillis());
            string(output, reward.keyCategory());
            string(output, reward.fallbackCommand());
            string(output, reward.serializedItem());
            string(output, reward.display());
            output.writeInt(reward.weight());
        }
    }

    private static RewardBundle rewards(DataInputStream input) throws IOException {
        RewardMode mode = enumValue(RewardMode.class, string(input));
        int rewardCount = count(input);
        List<RewardDefinition> rewards = new ArrayList<>(rewardCount);
        for (int index = 0; index < rewardCount; index++) {
            String id = string(input);
            RewardType type = enumValue(RewardType.class, string(input));
            long amount = input.readLong();
            double decimal = input.readDouble();
            String materialName = string(input);
            Material material = materialName.isBlank() ? null : enumValue(Material.class, materialName);
            String command = string(input);
            String permission = string(input);
            Duration duration = Duration.ofMillis(input.readLong());
            String category = string(input);
            String fallback = string(input);
            String serialized = string(input);
            String display = string(input);
            int weight = input.readInt();
            rewards.add(new RewardDefinition(
                    id, type, amount, decimal, material, command, permission, duration, category,
                    fallback, serialized, display, weight));
        }
        return new RewardBundle(mode, rewards);
    }

    private static void string(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("Snapshot string is too large");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String string(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > input.available()) {
            throw new IOException("Invalid snapshot string length");
        }
        return new String(input.readNBytes(length), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void strings(DataOutputStream output, Set<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) {
            string(output, value);
        }
    }

    private static Set<String> strings(DataInputStream input) throws IOException {
        int count = count(input);
        Set<String> values = new LinkedHashSet<>(count);
        for (int index = 0; index < count; index++) {
            values.add(string(input));
        }
        return Set.copyOf(values);
    }

    private static void stringMap(DataOutputStream output, Map<String, String> values) throws IOException {
        output.writeInt(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            string(output, entry.getKey());
            string(output, entry.getValue());
        }
    }

    private static Map<String, String> stringMap(DataInputStream input) throws IOException {
        int count = count(input);
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            values.put(string(input), string(input));
        }
        return Map.copyOf(values);
    }

    private static void enums(DataOutputStream output, Set<? extends Enum<?>> values) throws IOException {
        output.writeInt(values.size());
        for (Enum<?> value : values) {
            string(output, value.name());
        }
    }

    private static <E extends Enum<E>> Set<E> enums(DataInputStream input, Class<E> type) throws IOException {
        int count = count(input);
        Set<E> values = new LinkedHashSet<>(count);
        for (int index = 0; index < count; index++) {
            values.add(enumValue(type, string(input)));
        }
        return Set.copyOf(values);
    }

    private static int count(DataInputStream input) throws IOException {
        int value = input.readInt();
        if (value < 0 || value > MAX_COLLECTION) {
            throw new IOException("Invalid snapshot collection size");
        }
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) throws IOException {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Unknown " + type.getSimpleName() + " value " + name, exception);
        }
    }
}


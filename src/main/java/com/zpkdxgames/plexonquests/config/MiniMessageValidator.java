package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;

final class MiniMessageValidator {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MiniMessageValidator() {}

    static void validateYaml(String file, YamlConfiguration yaml, List<String> errors) {
        for (Map.Entry<String, Object> entry : yaml.getValues(true).entrySet()) {
            if (entry.getValue() instanceof String value && value.indexOf('<') >= 0) {
                validate(file + "." + entry.getKey(), value, errors);
            }
        }
    }

    static void validateRegistry(QuestRegistrySnapshot registry, List<String> errors) {
        registry.quests().values().forEach(quest -> {
            validate(quest.source() + ".display.name", quest.display().name(), errors);
            validate(quest.source() + ".display.short-description", quest.display().shortDescription(), errors);
            quest.objectives().values().forEach(objective ->
                    validate(quest.source() + ".objectives." + objective.id() + ".display", objective.display(), errors));
            quest.rewards().entries().forEach(reward ->
                    validate(quest.source() + ".rewards.entries." + reward.id() + ".display", reward.display(), errors));
            validate(quest.source() + ".effects.complete", quest.completeEffect(), errors);
            validate(quest.source() + ".effects.claim", quest.claimEffect(), errors);
        });
        registry.rarities().forEach((id, rarity) ->
                validate("rarities." + id + ".display", rarity.display(), errors));
    }

    private static void validate(String path, String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!balancedAngles(value)) {
            errors.add(path + " contains malformed MiniMessage tag delimiters");
            return;
        }
        try {
            MINI_MESSAGE.deserialize(value);
        } catch (RuntimeException exception) {
            errors.add(path + " contains invalid MiniMessage: "
                    + (exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private static boolean balancedAngles(String value) {
        boolean escaped = false;
        int open = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '<') {
                open++;
            } else if (current == '>') {
                if (open == 0) return false;
                open--;
            }
        }
        return open == 0;
    }
}

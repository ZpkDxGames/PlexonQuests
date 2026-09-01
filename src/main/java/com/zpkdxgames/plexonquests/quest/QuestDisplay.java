package com.zpkdxgames.plexonquests.quest;

import java.util.Objects;

public record QuestDisplay(IconDefinition icon, String name, String shortDescription, String loreTemplate) {
    public QuestDisplay {
        icon = Objects.requireNonNull(icon, "icon");
        name = Objects.requireNonNullElse(name, "<white>Unnamed Quest");
        shortDescription = Objects.requireNonNullElse(shortDescription, "");
        loreTemplate = Objects.requireNonNullElse(loreTemplate, "default-quest-card");
    }
}


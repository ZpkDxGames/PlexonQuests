package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.quest.QuestRegistrySnapshot;
import java.time.Instant;

public record ConfigSnapshot(
        PluginSettings settings,
        QuestRegistrySnapshot registry,
        FlatConfiguration messages,
        FlatConfiguration menus,
        FlatConfiguration effects,
        Instant loadedAt) {}


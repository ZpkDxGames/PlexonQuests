package com.zpkdxgames.plexonquests.config;

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

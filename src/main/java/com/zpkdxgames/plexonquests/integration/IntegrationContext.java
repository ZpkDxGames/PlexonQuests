package com.zpkdxgames.plexonquests.integration;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.rotation.RotationService;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import org.bukkit.plugin.java.JavaPlugin;

public record IntegrationContext(
        JavaPlugin plugin,
        ConfigManager configs,
        ProgressService progress,
        ProfileService profiles,
        RotationService rotations) {}

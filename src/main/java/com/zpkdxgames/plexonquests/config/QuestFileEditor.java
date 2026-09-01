package com.zpkdxgames.plexonquests.config;

import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.util.AtomicFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class QuestFileEditor {
    private final Path dataDirectory;

    public QuestFileEditor(Path dataDirectory) {
        this.dataDirectory = dataDirectory.toAbsolutePath().normalize();
    }

    public void toggleEnabled(QuestDefinition definition) throws IOException, InvalidConfigurationException {
        Path source = AtomicFiles.resolveInside(dataDirectory, definition.source());
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(source));
        if (!definition.id().equals(yaml.getString("id"))) {
            throw new IOException("Quest source changed while the editor was open");
        }
        yaml.set("enabled", !yaml.getBoolean("enabled", true));
        AtomicFiles.writeUtf8(source, yaml.saveToString());
    }
}


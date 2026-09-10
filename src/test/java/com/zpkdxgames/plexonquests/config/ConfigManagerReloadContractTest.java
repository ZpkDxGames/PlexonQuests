package com.zpkdxgames.plexonquests.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.PlexonQuestsPlugin;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class ConfigManagerReloadContractTest {
    private ServerMock server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void invalidGraphReloadPreservesKnownGoodSnapshotAndRepeatedValidReloadsSucceed() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        ConfigManager manager = new ConfigManager(plugin);
        ConfigSnapshot knownGood = manager.loadInitial();
        Path quest = manager.dataDirectory().resolve("quests/daily/stonebound.yml");
        String original = Files.readString(quest);

        Files.writeString(quest, original + "\nprerequisites:\n  completed-quests: [stonebound]\n");
        ReloadResult rejected = manager.reloadAsync(Runnable::run).join();

        assertFalse(rejected.success());
        assertSame(knownGood, manager.snapshot());
        assertTrue(manager.lastActivationErrors().stream().anyMatch(error -> error.contains("cannot depend on itself")));

        Files.writeString(quest, original);
        ReloadResult recovered = manager.reloadAsync(Runnable::run).join();
        assertTrue(recovered.success());
        assertTrue(manager.lastActivationErrors().isEmpty());
        assertTrue(manager.reloadAsync(Runnable::run).join().success());
    }
}

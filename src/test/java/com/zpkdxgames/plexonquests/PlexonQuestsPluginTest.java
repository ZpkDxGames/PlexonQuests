package com.zpkdxgames.plexonquests;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.api.PlexonQuestsAPI;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PlexonQuestsPluginTest {
    private ServerMock server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void bundledConfigurationStartsAndRegistersPublicApi() {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getCommand("quests"));
        assertNotNull(Bukkit.getServicesManager().load(PlexonQuestsAPI.class));
    }
}

package com.zpkdxgames.plexonquests.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.PlexonQuestsPlugin;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class Phase2ApiRegistrationTest {
    private boolean mocked;

    @AfterEach
    void stop() {
        if (mocked) {
            MockBukkit.unmock();
        }
    }

    @Test
    void pluginRegistersAdditiveJournalApiWithoutReplacingLegacyApi() {
        MockBukkit.mock();
        mocked = true;
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);

        assertTrue(plugin.isEnabled());
        assertNotNull(Bukkit.getServicesManager().load(PlexonQuestsAPI.class));
        assertNotNull(Bukkit.getServicesManager().load(PlexonQuestsJournalAPI.class));
    }
}

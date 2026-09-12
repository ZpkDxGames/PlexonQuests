package com.zpkdxgames.plexonquests.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zpkdxgames.plexonquests.PlexonQuestsPlugin;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TextServicePresentationContractTest {
    private ServerMock server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void lowNonZeroProgressAlwaysHasVisibleFilledSegment() throws Exception {
        TextService text = service();

        String sixPercent = text.progressBarMarkup(6D);
        assertEquals(1L, sixPercent.chars().filter(value -> value == '▰').count());
        assertEquals(9L, sixPercent.chars().filter(value -> value == '▱').count());
        assertTrue(sixPercent.startsWith("<#FF6B6B>"));

        String zeroPercent = text.progressBarMarkup(0D);
        assertEquals(0L, zeroPercent.chars().filter(value -> value == '▰').count());

        String complete = text.progressBarMarkup(100D);
        assertEquals(10L, complete.chars().filter(value -> value == '▰').count());
        assertEquals(0L, complete.chars().filter(value -> value == '▱').count());
    }

    @Test
    void configuredRewardMiniMessageRendersAsColorInsteadOfLiteralMarkup() throws Exception {
        TextService text = service();
        Component rendered = text.parse("<aqua>2,500 experience points");

        assertEquals(NamedTextColor.AQUA, rendered.color());
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        assertEquals("2,500 experience points", plain);
        assertFalse(plain.contains("<aqua>"));
    }

    private TextService service() throws Exception {
        server = MockBukkit.mock();
        PlexonQuestsPlugin plugin = MockBukkit.load(PlexonQuestsPlugin.class);
        ConfigManager configs = new ConfigManager(plugin);
        configs.loadInitial();
        return new TextService(configs);
    }
}

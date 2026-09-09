package com.zpkdxgames.plexonquests.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.integration.IntegrationRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import com.zpkdxgames.plexonquests.PlexonQuestsPlugin;
import java.time.Instant;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class PlexonCoreBridgeTest {
    private ServerMock server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    @Test
    void coreOneRegistersAsLegacyCompatibilityMode() {
        PlexonQuestsPlugin plugin = loadQuests();
        CoreVersion version = CoreVersion.of(1, 0, "1.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = new IntegrationRegistry(Bukkit.getPluginManager());
        integrations.refresh();
        registerCoreApi(plugin, version, modules, integrations);

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin);
        bridge.registerStarting();

        assertTrue(bridge.compatible());
        assertFalse(bridge.runtimeAvailable());
        assertEquals("CORE_LEGACY", bridge.mode());
        assertEquals(ModuleState.STARTING, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());
        assertEquals(CoreBridge.ProviderHint.MISSING, bridge.providerHint("PLEXON_RANKS"));

        bridge.markReady("ready");
        assertEquals(ModuleState.READY, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.markDegraded("optional provider unavailable");
        assertEquals(ModuleState.DEGRADED, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.markFailed("storage failed");
        assertEquals(ModuleState.FAILED, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.unregister();
        assertTrue(modules.find(CoreBridge.MODULE_ID).isEmpty());
    }

    @Test
    void coreTwoRegistersRuntimeMode() {
        PlexonQuestsPlugin plugin = loadQuests();
        CoreVersion version = CoreVersion.of(2, 0, "2.0.1");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = new IntegrationRegistry(Bukkit.getPluginManager());
        integrations.refresh();
        registerCoreApi(plugin, version, modules, integrations);

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin);
        bridge.registerStarting();

        assertTrue(bridge.compatible());
        assertTrue(bridge.runtimeAvailable());
        assertEquals("CORE_RUNTIME", bridge.mode());
        assertEquals(ModuleState.STARTING, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.unregister();
        assertTrue(modules.find(CoreBridge.MODULE_ID).isEmpty());
    }

    @Test
    void coreThreeRegistersIncompatibleAndNeverBecomesReady() {
        PlexonQuestsPlugin plugin = loadQuests();
        CoreVersion version = CoreVersion.of(3, 0, "3.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = new IntegrationRegistry(Bukkit.getPluginManager());
        integrations.refresh();
        registerCoreApi(plugin, version, modules, integrations);

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin);
        bridge.registerStarting();

        assertFalse(bridge.compatible());
        assertFalse(bridge.runtimeAvailable());
        assertEquals("STANDALONE", bridge.mode());
        assertEquals(ModuleState.INCOMPATIBLE, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.markReady("must not override incompatibility");
        assertEquals(ModuleState.INCOMPATIBLE, modules.find(CoreBridge.MODULE_ID).orElseThrow().state());

        bridge.unregister();
        assertTrue(modules.find(CoreBridge.MODULE_ID).isEmpty());
    }

    @Test
    void duplicateRegistrationOwnedByAnotherPluginIsNotReplacedOrRemoved() {
        PlexonQuestsPlugin plugin = loadQuests();
        CoreVersion version = CoreVersion.of(1, 0, "1.0.0");
        ModuleRegistry modules = new ModuleRegistry(version);
        IntegrationRegistry integrations = new IntegrationRegistry(Bukkit.getPluginManager());
        integrations.refresh();
        registerCoreApi(plugin, version, modules, integrations);

        Plugin other = mock(Plugin.class);
        ModuleDescriptor existing = new ModuleDescriptor(
                CoreBridge.MODULE_ID,
                "Other Quests",
                "OtherPlugin",
                "9.9.9",
                other,
                ModuleVersionRange.parse(CoreBridge.SUPPORTED_API_RANGE),
                Set.of("other"),
                ModuleState.READY,
                "Already registered",
                Instant.now());
        assertTrue(modules.register(existing).success());

        PlexonCoreBridge bridge = new PlexonCoreBridge(plugin);
        bridge.registerStarting();

        assertEquals("STANDALONE", bridge.mode());
        assertEquals(other, modules.find(CoreBridge.MODULE_ID).orElseThrow().plugin());

        bridge.unregister();
        assertEquals(other, modules.find(CoreBridge.MODULE_ID).orElseThrow().plugin());
    }

    private PlexonQuestsPlugin loadQuests() {
        server = MockBukkit.mock();
        return MockBukkit.load(PlexonQuestsPlugin.class);
    }

    private static void registerCoreApi(
            PlexonQuestsPlugin plugin,
            CoreVersion version,
            ModuleRegistry modules,
            IntegrationRegistry integrations) {
        PlexonCoreAPI api = mock(PlexonCoreAPI.class);
        when(api.version()).thenReturn(version);
        when(api.modules()).thenReturn(modules);
        when(api.integrations()).thenReturn(integrations);
        Bukkit.getServicesManager().register(PlexonCoreAPI.class, api, plugin, ServicePriority.Normal);
    }
}

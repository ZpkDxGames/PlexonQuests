package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI.CoreVersion;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleDescriptor;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleState;
import com.zpkdxgames.plexoncore.module.ModuleRegistry.ModuleVersionRange;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonCoreBridge implements CoreBridge {
    private static final String CORE2_RUNTIME_CLASS =
            "com.zpkdxgames.plexonquests.integration.core.PlexonCore2Runtime";
    private static final Set<String> CAPABILITIES = Set.of(
            "quest-engine",
            "quest-rotations",
            "quest-objectives",
            "quest-rewards",
            "quest-integrations",
            "sqlite-persistence",
            "minimessage",
            "placeholderapi",
            "core-runtime-block-break",
            "core-shared-block-origin");

    private final JavaPlugin plugin;
    private final PlexonCoreAPI core;
    private final CoreVersion version;
    private final boolean compatible;
    private final CoreRuntime runtime;
    private boolean ownsRegistration;
    private String registrationState = "NOT_REGISTERED";
    private String detail = "PlexonCore API resolved";

    public PlexonCoreBridge(JavaPlugin plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<PlexonCoreAPI> registration =
                Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null) {
            throw new IllegalStateException("PlexonCore API service is not registered");
        }
        this.core = registration.getProvider();
        this.version = core.version();
        this.compatible = ModuleVersionRange.parse(SUPPORTED_API_RANGE).contains(version);
        if (!compatible) {
            detail = "Core API " + version.apiVersion() + " is outside supported range " + SUPPORTED_API_RANGE;
        }
        this.runtime = resolveRuntime();
    }

    private CoreRuntime resolveRuntime() {
        if (!compatible || version.apiMajor() < 2) {
            return CoreRuntime.unavailable("Core Runtime requires PlexonCore API 2.x");
        }
        try {
            Class<?> type = Class.forName(CORE2_RUNTIME_CLASS, true, PlexonCoreBridge.class.getClassLoader());
            return (CoreRuntime) type.getConstructor(JavaPlugin.class).newInstance(plugin);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            plugin.getLogger().log(Level.WARNING, "PlexonCore 2 Runtime adapter could not start", cause);
            return CoreRuntime.unavailable("Core 2 Runtime adapter failed: " + cause.getClass().getSimpleName());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "PlexonCore 2 Runtime adapter could not be linked", exception);
            return CoreRuntime.unavailable("Core 2 Runtime linkage failed: " + exception.getClass().getSimpleName());
        }
    }

    @Override
    public boolean installed() {
        return true;
    }

    @Override
    public boolean available() {
        return compatible;
    }

    @Override
    public boolean compatible() {
        return compatible;
    }

    @Override
    public String pluginVersion() {
        return version.pluginVersion();
    }

    @Override
    public String apiVersion() {
        return version.apiVersion();
    }

    @Override
    public String mode() {
        if (!compatible || !ownsRegistration) {
            return "STANDALONE";
        }
        return runtime.available() ? "CORE_RUNTIME" : "CORE_LEGACY";
    }

    @Override
    public String registrationState() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID).map(descriptor -> descriptor.state().name()).orElse("NOT_REGISTERED");
        }
        return registrationState;
    }

    @Override
    public String detail() {
        if (ownsRegistration) {
            return core.modules().find(MODULE_ID).map(ModuleDescriptor::detail).orElse(detail);
        }
        return detail;
    }

    @Override
    public boolean runtimeAvailable() {
        return compatible && runtime.available();
    }

    @Override
    public CoreRuntime runtime() {
        return runtime;
    }

    @Override
    public void registerStarting() {
        ModuleDescriptor descriptor = new ModuleDescriptor(
                MODULE_ID,
                "PlexonQuests",
                plugin.getName(),
                plugin.getPluginMeta().getVersion(),
                plugin,
                ModuleVersionRange.parse(SUPPORTED_API_RANGE),
                CAPABILITIES,
                ModuleState.STARTING,
                "Initializing PlexonQuests",
                Instant.now());

        ModuleRegistry.RegistrationResult result = core.modules().register(descriptor);
        ModuleDescriptor registered = result.descriptor();
        ownsRegistration = registered != null && registered.plugin() == plugin;
        registrationState = registered == null ? "NOT_REGISTERED" : registered.state().name();
        detail = result.message();

        if (!result.success() && !ownsRegistration) {
            plugin.getLogger().warning("PlexonCore module registration rejected: " + result.message());
        } else if (!compatible) {
            plugin.getLogger().warning("PlexonCore API " + version.apiVersion()
                    + " is incompatible with supported range " + SUPPORTED_API_RANGE
                    + "; quest gameplay will continue in standalone compatibility mode.");
        }
    }

    @Override
    public void markReady(String detail) {
        update(ModuleState.READY, detail);
    }

    @Override
    public void markDegraded(String detail) {
        update(ModuleState.DEGRADED, detail);
    }

    @Override
    public void markFailed(String detail) {
        update(ModuleState.FAILED, detail);
    }

    private void update(ModuleState state, String newDetail) {
        if (!compatible || !ownsRegistration) {
            return;
        }
        core.modules().updateState(MODULE_ID, state, newDetail);
        registrationState = state.name();
        detail = newDetail == null ? "" : newDetail;
    }

    @Override
    public void unregister() {
        if (!ownsRegistration) {
            return;
        }
        core.modules().find(MODULE_ID)
                .filter(descriptor -> descriptor.plugin() == plugin)
                .ifPresent(descriptor -> core.modules().unregister(MODULE_ID));
        ownsRegistration = false;
        registrationState = "UNREGISTERED";
    }

    @Override
    public ProviderHint providerHint(String integrationId) {
        if (!compatible) {
            return ProviderHint.UNKNOWN;
        }
        return core.integrations().get(integrationId).map(view -> switch (view.state()) {
            case READY -> ProviderHint.PRESENT;
            case MISSING -> ProviderHint.MISSING;
            case DEGRADED, INCOMPATIBLE, FAILED -> ProviderHint.UNKNOWN;
        }).orElse(ProviderHint.UNKNOWN);
    }
}

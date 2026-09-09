package com.zpkdxgames.plexonquests.integration.core;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.OriginPolicy;
import com.zpkdxgames.plexonquests.objective.block.BlockObjectiveProcessor;
import java.io.File;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns the optional Core 2 block subscription and the synchronous hand-off into PlexonQuests'
 * MONITOR cancellation gate.
 */
public final class CoreRuntimeCoordinator implements AutoCloseable {
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final CoreBridge core;
    private final ThreadLocal<ArrayDeque<CoreRuntime.BlockFact>> pending =
            ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong callbackFailures = new AtomicLong();
    private CoreRuntime.Subscription subscription;
    private ConfigSnapshot subscriptionSnapshot;
    private Mode requestedMode = Mode.AUTO;
    private boolean active;
    private int subscribedMaterials;
    private boolean originRequested;

    public CoreRuntimeCoordinator(JavaPlugin plugin, ConfigManager configs, CoreBridge core) {
        this.plugin = plugin;
        this.configs = configs;
        this.core = core;
    }

    public void start() {
        rebuild(true);
    }

    public void refresh() {
        rebuild(false);
    }

    private void rebuild(boolean initial) {
        ConfigSnapshot snapshot = configs.snapshot();
        Mode mode = readMode();
        if (!initial && snapshot == subscriptionSnapshot && mode == requestedMode) {
            return;
        }

        if (mode == Mode.CORE && !core.runtimeAvailable()) {
            throw new IllegalStateException("core-runtime.mode=CORE but PlexonCore 2 Runtime API is unavailable");
        }

        boolean nextActive = mode != Mode.LOCAL && core.runtimeAvailable();
        Plan plan = buildPlan(snapshot);
        CoreRuntime.Subscription candidate = null;
        if (nextActive && !plan.materials().isEmpty()) {
            candidate = core.runtime().subscribeBlockBreak(
                    plan.materials(), plan.requiresOrigin(), this::receive);
        }

        closeSubscription();
        subscription = candidate;
        subscriptionSnapshot = snapshot;
        requestedMode = mode;
        active = nextActive;
        subscribedMaterials = plan.materials().size();
        originRequested = plan.requiresOrigin();
        pending.get().clear();
        epoch.incrementAndGet();

        plugin.getLogger().info("PlexonQuests block acquisition mode: " + acquisitionMode()
                + "; Core routes=" + subscribedMaterials
                + "; origin requested=" + originRequested + '.');
    }

    private void receive(CoreRuntime.BlockFact fact) {
        received.incrementAndGet();
        if (!Bukkit.isPrimaryThread()) {
            callbackFailures.incrementAndGet();
            plugin.getLogger().warning("Ignored asynchronous PlexonCore block callback; quest progress remains main-thread owned");
            return;
        }
        pending.get().addLast(fact);
    }

    /**
     * Consume the Core fact matching this exact Bukkit event. The local MONITOR listener still owns
     * final cancellation correctness because Core 2.0 currently acquires its immutable fact at HIGHEST.
     */
    public CoreRuntime.BlockFact consume(BlockBreakEvent event) {
        if (!active) {
            return null;
        }
        ArrayDeque<CoreRuntime.BlockFact> facts = pending.get();
        CoreRuntime.BlockFact fact = facts.peekLast();
        if (fact == null || !matches(fact, event)) {
            fallbacks.incrementAndGet();
            return null;
        }
        facts.removeLast();
        consumed.incrementAndGet();
        return fact;
    }

    private static boolean matches(CoreRuntime.BlockFact fact, BlockBreakEvent event) {
        return fact.playerId().equals(event.getPlayer().getUniqueId())
                && fact.worldId().equals(event.getBlock().getWorld().getUID())
                && fact.x() == event.getBlock().getX()
                && fact.y() == event.getBlock().getY()
                && fact.z() == event.getBlock().getZ()
                && fact.material() == event.getBlock().getType();
    }

    public BlockObjectiveProcessor.OriginState origin(CoreRuntime.BlockFact fact) {
        if (fact == null) {
            return BlockObjectiveProcessor.OriginState.UNKNOWN;
        }
        return switch (fact.origin()) {
            case NATURAL -> BlockObjectiveProcessor.OriginState.NATURAL;
            case PLAYER_PLACED -> BlockObjectiveProcessor.OriginState.PLAYER_PLACED;
            case UNKNOWN -> BlockObjectiveProcessor.OriginState.UNKNOWN;
        };
    }

    public boolean active() {
        return active;
    }

    public boolean coreOriginAuthoritative() {
        return active;
    }

    public String acquisitionMode() {
        if (requestedMode == Mode.LOCAL) {
            return core.installed() ? "CORE_LEGACY/LOCAL" : "STANDALONE";
        }
        if (active) {
            return "CORE_RUNTIME";
        }
        return core.installed() && core.compatible() ? "CORE_LEGACY" : "STANDALONE";
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(
                acquisitionMode(),
                epoch.get(),
                subscribedMaterials,
                originRequested,
                received.get(),
                consumed.get(),
                fallbacks.get(),
                callbackFailures.get(),
                coreOriginAuthoritative());
    }

    private Plan buildPlan(ConfigSnapshot snapshot) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        boolean wildcardBreak = false;
        boolean requiresOrigin = false;

        for (var quest : snapshot.registry().quests().values()) {
            if (!quest.enabled()) {
                continue;
            }
            for (var objective : quest.objectives().values()) {
                ObjectiveType type = objective.type();
                if (type != ObjectiveType.BREAK_BLOCK && type != ObjectiveType.HARVEST_CROP) {
                    continue;
                }
                if (objective.filters().origin() != OriginPolicy.ANY) {
                    requiresOrigin = true;
                }
                Set<Material> configured = objective.filters().materials();
                if (type == ObjectiveType.BREAK_BLOCK) {
                    if (configured.isEmpty()) {
                        wildcardBreak = true;
                    } else {
                        materials.addAll(configured);
                    }
                } else if (configured.isEmpty()) {
                    materials.addAll(CROPS);
                } else {
                    configured.stream().filter(BlockObjectiveProcessor::isCrop).forEach(materials::add);
                }
            }
        }

        if (wildcardBreak) {
            for (Material material : Material.values()) {
                if (material.isBlock()) {
                    materials.add(material);
                }
            }
        }
        return new Plan(Set.copyOf(materials), requiresOrigin);
    }

    private Mode readMode() {
        File file = configs.dataDirectory().resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String value = yaml.getString("core-runtime.mode", "AUTO");
        try {
            return Mode.valueOf(value == null ? "AUTO" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown core-runtime.mode: " + value + " (expected AUTO, CORE, or LOCAL)");
        }
    }

    private void closeSubscription() {
        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }

    @Override
    public void close() {
        closeSubscription();
        pending.remove();
        active = false;
        epoch.incrementAndGet();
    }

    private enum Mode {
        AUTO,
        CORE,
        LOCAL
    }

    private record Plan(Set<Material> materials, boolean requiresOrigin) {}

    public record Diagnostics(
            String mode,
            long epoch,
            int subscribedMaterials,
            boolean originRequested,
            long eventsReceived,
            long eventsConsumed,
            long fallbackCount,
            long callbackFailures,
            boolean coreOriginAuthoritative) {}
}

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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the optional Core 2 block subscription and synchronous hand-off into the quest block gate. */
public final class CoreRuntimeCoordinator implements AutoCloseable {
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final CoreBridge core;
    private final ThreadLocal<ArrayDeque<CoreRuntime.BlockFact>> pending = ThreadLocal.withInitial(ArrayDeque::new);
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong consumed = new AtomicLong();
    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong callbackFailures = new AtomicLong();
    private final AtomicLong shadowComparisons = new AtomicLong();
    private final AtomicLong shadowMismatches = new AtomicLong();
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

    public void start() { rebuild(true); }
    public void refresh() { rebuild(false); }

    private void rebuild(boolean initial) {
        ConfigSnapshot snapshot = configs.snapshot();
        Mode configuredMode = readMode();
        Mode mode = configuredMode;
        if (!initial && configuredMode != requestedMode) {
            plugin.getLogger().warning("core-runtime.mode changed from " + requestedMode + " to " + configuredMode
                    + "; runtime authority changes require a server restart, so the current mode is retained");
            mode = requestedMode;
        }
        if (!initial && snapshot == subscriptionSnapshot) return;
        if ((mode == Mode.CORE || mode == Mode.SHADOW) && !core.runtimeAvailable()) {
            throw new IllegalStateException("core-runtime.mode=" + mode + " but PlexonCore 2 Runtime API is unavailable");
        }
        Plan plan = buildPlan(snapshot);
        if (mode == Mode.CORE && plan.requiresOrigin() && core.runtimeAvailable() && !core.runtime().originImportAvailable()) {
            throw new IllegalStateException("core-runtime.mode=CORE requires PlexonCore origin import support for natural/player-placed quest filters");
        }
        boolean nextActive = mode != Mode.LOCAL && core.runtimeAvailable();
        CoreRuntime.Subscription candidate = null;
        if (nextActive && !plan.materials().isEmpty()) {
            candidate = core.runtime().subscribeBlockBreak(plan.materials(), plan.requiresOrigin(), this::receive);
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
                + "; Core routes=" + subscribedMaterials + "; origin requested=" + originRequested
                + "; origin provider=" + originProvider() + '.');
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
     * Consumes the Core fact matching this exact Bukkit event. PlexonCore 2.0.2 dispatches its
     * callback at final MONITOR and suppresses finally-cancelled breaks; the local listener keeps an
     * additional cancellation check as a defensive quest-side invariant.
     */
    public CoreRuntime.BlockFact consume(BlockBreakEvent event) {
        if (!active) return null;
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
                && fact.x() == event.getBlock().getX() && fact.y() == event.getBlock().getY()
                && fact.z() == event.getBlock().getZ() && fact.material() == event.getBlock().getType();
    }

    public BlockObjectiveProcessor.OriginState origin(CoreRuntime.BlockFact fact) {
        if (fact == null) return BlockObjectiveProcessor.OriginState.UNKNOWN;
        return switch (fact.origin()) {
            case NATURAL -> BlockObjectiveProcessor.OriginState.NATURAL;
            case PLAYER_PLACED -> BlockObjectiveProcessor.OriginState.PLAYER_PLACED;
            case UNKNOWN -> BlockObjectiveProcessor.OriginState.UNKNOWN;
        };
    }

    public void recordShadowComparison(BlockObjectiveProcessor.OriginState local, BlockObjectiveProcessor.OriginState coreState) {
        if (!shadowMode()) return;
        shadowComparisons.incrementAndGet();
        if (local != coreState) shadowMismatches.incrementAndGet();
    }

    public boolean active() { return active; }
    public boolean shadowMode() { return active && requestedMode == Mode.SHADOW; }
    public boolean originMigrationAvailable() { return active && core.runtime().originImportAvailable(); }
    public boolean coreOriginAuthoritative() { return originMigrationAvailable() && requestedMode != Mode.SHADOW; }
    public boolean localOriginAuthoritative() { return !coreOriginAuthoritative(); }
    public String originProvider() { return shadowMode() ? "SHADOW" : (coreOriginAuthoritative() ? "CORE" : "LOCAL"); }

    public String acquisitionMode() {
        if (requestedMode == Mode.LOCAL) return core.installed() ? "CORE_LEGACY/LOCAL" : "STANDALONE";
        if (active) return "CORE_RUNTIME";
        return core.installed() && core.compatible() ? "CORE_LEGACY" : "STANDALONE";
    }

    public Diagnostics diagnostics() {
        return new Diagnostics(acquisitionMode(), originProvider(), epoch.get(), subscribedMaterials, originRequested,
                received.get(), consumed.get(), fallbacks.get(), callbackFailures.get(), coreOriginAuthoritative(),
                shadowComparisons.get(), shadowMismatches.get());
    }

    private Plan buildPlan(ConfigSnapshot snapshot) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        boolean wildcardBreak = false;
        boolean requiresOrigin = false;
        for (var quest : snapshot.registry().quests().values()) {
            if (!quest.enabled()) continue;
            for (var objective : quest.objectives().values()) {
                ObjectiveType type = objective.type();
                if (type != ObjectiveType.BREAK_BLOCK && type != ObjectiveType.HARVEST_CROP) continue;
                if (objective.filters().origin() != OriginPolicy.ANY) requiresOrigin = true;
                Set<Material> configured = objective.filters().materials();
                if (type == ObjectiveType.BREAK_BLOCK) {
                    if (configured.isEmpty()) wildcardBreak = true; else materials.addAll(configured);
                } else if (configured.isEmpty()) {
                    materials.addAll(CROPS);
                } else {
                    configured.stream().filter(BlockObjectiveProcessor::isCrop).forEach(materials::add);
                }
            }
        }
        if (wildcardBreak) for (Material material : Material.values()) if (material.isBlock()) materials.add(material);
        return new Plan(Set.copyOf(materials), requiresOrigin);
    }

    private Mode readMode() {
        File file = configs.dataDirectory().resolve("config.yml").toFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String value = yaml.getString("core-runtime.mode", "AUTO");
        try {
            return Mode.valueOf(value == null ? "AUTO" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown core-runtime.mode: " + value + " (expected AUTO, CORE, LOCAL, or SHADOW)");
        }
    }

    private void closeSubscription() {
        if (subscription != null) { subscription.close(); subscription = null; }
    }

    @Override
    public void close() {
        closeSubscription();
        pending.remove();
        active = false;
        epoch.incrementAndGet();
    }

    private enum Mode { AUTO, CORE, LOCAL, SHADOW }
    private record Plan(Set<Material> materials, boolean requiresOrigin) {}
    public record Diagnostics(String mode, String originProvider, long epoch, int subscribedMaterials,
            boolean originRequested, long eventsReceived, long eventsConsumed, long fallbackCount,
            long callbackFailures, boolean coreOriginAuthoritative, long shadowComparisons, long shadowMismatches) {}
}

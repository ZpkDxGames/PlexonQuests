package com.zpkdxgames.plexonquests.objective.tracker;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ActivitySampler implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProgressService progress;
    private final Map<UUID, Sample> samples = new HashMap<>();
    private BukkitTask task;

    public ActivitySampler(JavaPlugin plugin, ConfigManager configs, ProgressService progress) {
        this.plugin = plugin;
        this.configs = configs;
        this.progress = progress;
    }

    public void start() {
        long interval = configs.snapshot().settings().tracking().travelSampleTicks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sample, interval, interval);
        Bukkit.getOnlinePlayers().forEach(player -> samples.put(player.getUniqueId(), Sample.initial(player)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        samples.put(event.getPlayer().getUniqueId(), Sample.initial(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        samples.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Sample sample = samples.get(event.getPlayer().getUniqueId());
        if (sample != null) {
            sample.reset(event.getTo(), System.nanoTime());
        }
    }

    private void sample() {
        long now = System.nanoTime();
        var settings = configs.snapshot().settings().tracking();
        double maximumDelta = settings.travelMaximumDelta();
        long afkNanos = settings.afkTimeout().toNanos();
        long intervalTicks = settings.travelSampleTicks();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Sample sample = samples.computeIfAbsent(player.getUniqueId(), ignored -> Sample.initial(player));
            Location current = player.getLocation();
            if (sample.worldId == null || !sample.worldId.equals(current.getWorld().getUID())) {
                sample.reset(current, now);
                continue;
            }
            double deltaSquared = distanceSquared(sample, current);
            double delta = Math.sqrt(deltaSquared);
            if (delta > 0.05D && delta <= maximumDelta) {
                sample.lastActivityNanos = now;
                sample.fractionalDistance += delta;
                long wholeBlocks = (long) sample.fractionalDistance;
                if (wholeBlocks > 0L) {
                    sample.fractionalDistance -= wholeBlocks;
                    progress.contribute(player, movementContribution(
                            ObjectiveType.TRAVEL_DISTANCE, wholeBlocks, player, movementType(player), false));
                }
            }
            if (now - sample.lastActivityNanos <= afkNanos) {
                sample.fractionalSeconds += intervalTicks / 20D;
                long seconds = (long) sample.fractionalSeconds;
                if (seconds > 0L) {
                    sample.fractionalSeconds -= seconds;
                    progress.contribute(player, movementContribution(
                            ObjectiveType.PLAY_TIME, seconds, player, movementType(player), false));
                }
            }
            sample.x = current.getX();
            sample.y = current.getY();
            sample.z = current.getZ();
        }
    }

    private static Contribution movementContribution(
            ObjectiveType type, long amount, Player player, String movementType, boolean teleport) {
        return new Contribution(
                type,
                amount,
                null,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                false,
                false,
                true,
                false,
                teleport,
                true,
                movementType,
                "",
                "");
    }

    private static String movementType(Player player) {
        if (player.isFlying() || player.getGameMode() == GameMode.SPECTATOR) {
            return "FLY";
        }
        if (player.isSwimming()) {
            return "SWIM";
        }
        if (player.isSprinting()) {
            return "SPRINT";
        }
        if (player.isInsideVehicle()) {
            return "VEHICLE";
        }
        return "WALK";
    }

    private static double distanceSquared(Sample sample, Location location) {
        double x = location.getX() - sample.x;
        double y = location.getY() - sample.y;
        double z = location.getZ() - sample.z;
        return x * x + y * y + z * z;
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
        samples.clear();
    }

    private static final class Sample {
        private UUID worldId;
        private double x;
        private double y;
        private double z;
        private double fractionalDistance;
        private double fractionalSeconds;
        private long lastActivityNanos;

        private static Sample initial(Player player) {
            Sample sample = new Sample();
            sample.reset(player.getLocation(), System.nanoTime());
            return sample;
        }

        private void reset(Location location, long now) {
            this.worldId = location.getWorld().getUID();
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.lastActivityNanos = now;
        }
    }
}


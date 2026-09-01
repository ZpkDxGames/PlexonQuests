package com.zpkdxgames.plexonquests.presentation;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.quest.ProgressResult;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.service.FeedbackChannel;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressObserver;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EffectService implements ProgressObserver, AutoCloseable {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final TextService text;
    private final Map<UUID, DisplayState> displays = new HashMap<>();
    private BukkitTask cleanupTask;

    public EffectService(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            TextService text) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.text = text;
    }

    public void start() {
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanup, 10L, 10L);
    }

    @Override
    public void onProgress(Player player, QuestAssignment assignment, ProgressResult result) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return;
        }
        double percentage = assignment.percentage();
        Map<String, String> values = text.placeholders(
                "current", text.formatNumber(assignment.currentTotal()),
                "required", text.formatNumber(assignment.requiredTotal()),
                "percentage", Integer.toString((int) Math.floor(percentage)),
                "progress_color", text.progressColor(percentage),
                "objective", result.objectiveId());
        Map<String, Component> components = Map.of(
                "quest_name", text.parse(assignment.definition().display().name()),
                "progress_bar", text.progressBar(percentage));
        long now = System.nanoTime();
        DisplayState state = displays.computeIfAbsent(player.getUniqueId(), ignored -> new DisplayState());
        long actionbarNanos = configs.snapshot().settings().tracking().actionbarThrottleTicks() * 50_000_000L;
        if (profile.preferences().enabled(FeedbackChannel.ACTIONBAR)
                && now - state.lastActionbarNanos >= actionbarNanos) {
            String template = effectValue("quest-progress", "actionbar", "<gray><quest_name> <progress_bar>");
            player.sendActionBar(text.parse(player, template, values, components));
            state.lastActionbarNanos = now;
        }
        long bossbarNanos = configs.snapshot().settings().tracking().bossbarThrottleTicks() * 50_000_000L;
        if (profile.preferences().enabled(FeedbackChannel.BOSSBAR)
                && now - state.lastBossbarNanos >= bossbarNanos) {
            showProgressBossBar(player, state, assignment, percentage, values, components);
            state.lastBossbarNanos = now;
        }
        if (!result.objectiveCompleted() && crossedThreshold(assignment, result)) {
            playConfiguredSound(player, profile, "quest-objective-complete", FeedbackChannel.PROGRESS_SOUNDS);
        }
        if (result.objectiveCompleted() && profile.preferences().enabled(FeedbackChannel.PROGRESS_SOUNDS)) {
            playConfiguredSound(player, profile, "quest-objective-complete", FeedbackChannel.PROGRESS_SOUNDS);
        }
        if (result.questCompleted()) {
            complete(player, profile, assignment, state);
        }
    }

    public void claimed(Player player, QuestAssignment assignment) {
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile == null) {
            return;
        }
        playConfiguredSound(player, profile, assignment.definition().claimEffect(), FeedbackChannel.COMPLETION_SOUNDS);
        String actionbar = effectValue(assignment.definition().claimEffect(), "actionbar", "<green>Rewards claimed.");
        player.sendActionBar(text.parse(
                player,
                actionbar,
                Map.of(),
                Map.of("quest_name", text.parse(assignment.definition().display().name()))));
    }

    private void complete(
            Player player, PlayerProfile profile, QuestAssignment assignment, DisplayState display) {
        String preset = assignment.definition().completeEffect();
        if (preset.isBlank()) {
            preset = configs.snapshot().registry().rarities()
                    .get(assignment.definition().rarity()).completeEffect();
        }
        if (profile.preferences().enabled(FeedbackChannel.COMPLETION_SOUNDS)) {
            playConfiguredSound(player, profile, preset, FeedbackChannel.COMPLETION_SOUNDS);
        }
        boolean reducedMotion = profile.preferences().enabled(FeedbackChannel.REDUCED_MOTION);
        Map<String, Component> components = Map.of(
                "quest_name", text.parse(assignment.definition().display().name()));
        if (!reducedMotion && profile.preferences().enabled(FeedbackChannel.TITLES)) {
            Component title = text.parse(
                    player, effectValue(preset, "title.title", "<green><bold>QUEST COMPLETE</bold>"), Map.of(), components);
            Component subtitle = text.parse(
                    player, effectValue(preset, "title.subtitle", "<gray><quest_name>"), Map.of(), components);
            int fadeIn = effectInt(preset, "title.fade-in", 5);
            int stay = effectInt(preset, "title.stay", 35);
            int fadeOut = effectInt(preset, "title.fade-out", 10);
            player.showTitle(Title.title(
                    title,
                    subtitle,
                    Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L))));
        }
        if (!reducedMotion && profile.preferences().enabled(FeedbackChannel.PARTICLES)) {
            try {
                Particle particle = Particle.valueOf(effectValue(preset, "particles.type", "HAPPY_VILLAGER"));
                int count = Math.max(0, Math.min(64, effectInt(preset, "particles.count", 10)));
                double spread = Math.max(0D, Math.min(2D, effectDecimal(preset, "particles.spread", 0.5D)));
                player.getWorld().spawnParticle(
                        particle, player.getLocation().add(0D, 1D, 0D), count, spread, spread, spread, 0D);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid particle in effect preset " + preset);
            }
        }
        if (profile.preferences().enabled(FeedbackChannel.BOSSBAR)) {
            Component name = text.parse(
                    player,
                    effectValue(preset, "bossbar.text", "<green><bold>Complete!</bold> <white><quest_name>"),
                    Map.of(),
                    components);
            BossBar bar = display.bossBar;
            if (bar == null) {
                bar = BossBar.bossBar(name, 1F, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                display.bossBar = bar;
                player.showBossBar(bar);
            } else {
                bar.name(name);
                bar.progress(1F);
                bar.color(BossBar.Color.GREEN);
            }
            display.hideAtNanos = System.nanoTime() + duration(preset, "bossbar.duration", "4s").toNanos();
        }
    }

    private void showProgressBossBar(
            Player player,
            DisplayState state,
            QuestAssignment assignment,
            double percentage,
            Map<String, String> values,
            Map<String, Component> components) {
        Component name = text.parse(
                player,
                effectValue("quest-progress", "actionbar", "<gray><quest_name> <progress_bar>"),
                values,
                components);
        float progress = (float) Math.max(0D, Math.min(1D, percentage / 100D));
        BossBar.Color color = percentage >= 100D
                ? BossBar.Color.GREEN
                : percentage >= 50D ? BossBar.Color.YELLOW : BossBar.Color.BLUE;
        if (state.bossBar == null) {
            state.bossBar = BossBar.bossBar(name, progress, color, BossBar.Overlay.PROGRESS);
            player.showBossBar(state.bossBar);
        } else {
            state.bossBar.name(name);
            state.bossBar.progress(progress);
            state.bossBar.color(color);
        }
        state.hideAtNanos = System.nanoTime()
                + configs.snapshot().settings().tracking().bossbarQuietPeriod().toNanos();
    }

    private boolean crossedThreshold(QuestAssignment assignment, ProgressResult result) {
        if (result.newValue() <= result.oldValue()) {
            return false;
        }
        long required = assignment.objective(result.objectiveId())
                .map(objective -> objective.required())
                .orElse(Math.max(1L, result.newValue()));
        double oldPercentage = result.oldValue() * 100D / required;
        double newPercentage = result.newValue() * 100D / required;
        return configs.snapshot().settings().feedback().progressThresholds().stream()
                .anyMatch(threshold -> oldPercentage < threshold && newPercentage >= threshold);
    }

    private void playConfiguredSound(
            Player player, PlayerProfile profile, String preset, FeedbackChannel channel) {
        if (!profile.preferences().enabled(channel)) {
            return;
        }
        String key = effectValue(preset, "sound.key", "");
        if (key.isBlank()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(key.toUpperCase(Locale.ROOT));
            float volume = (float) Math.max(0D, Math.min(2D, effectDecimal(preset, "sound.volume", 0.8D)));
            float pitch = (float) Math.max(0.5D, Math.min(2D, effectDecimal(preset, "sound.pitch", 1D)));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid sound in effect preset " + preset + ": " + key);
        }
    }

    private void cleanup() {
        long now = System.nanoTime();
        displays.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            DisplayState state = entry.getValue();
            if (state.bossBar != null && state.hideAtNanos > 0L && now >= state.hideAtNanos) {
                player.hideBossBar(state.bossBar);
                state.bossBar = null;
                state.hideAtNanos = 0L;
            }
            return false;
        });
    }

    private String effectValue(String preset, String suffix, String fallback) {
        String current = preset;
        for (int depth = 0; depth < 8 && current != null && !current.isBlank(); depth++) {
            String path = "presets." + current + "." + suffix;
            if (configs.snapshot().effects().values().containsKey(path)) {
                return configs.snapshot().effects().string(path, fallback);
            }
            current = configs.snapshot().effects().string("presets." + current + ".inherit", "");
        }
        return fallback;
    }

    private int effectInt(String preset, String suffix, int fallback) {
        String value = effectValue(preset, suffix, Integer.toString(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double effectDecimal(String preset, String suffix, double fallback) {
        String value = effectValue(preset, suffix, Double.toString(fallback));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private Duration duration(String preset, String suffix, String fallback) {
        try {
            return com.zpkdxgames.plexonquests.util.DurationParser.parse(effectValue(preset, suffix, fallback));
        } catch (IllegalArgumentException ignored) {
            return com.zpkdxgames.plexonquests.util.DurationParser.parse(fallback);
        }
    }

    @Override
    public void close() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        displays.forEach((playerId, state) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && state.bossBar != null) {
                player.hideBossBar(state.bossBar);
            }
        });
        displays.clear();
    }

    private static final class DisplayState {
        private BossBar bossBar;
        private long hideAtNanos;
        private long lastActionbarNanos = Long.MIN_VALUE;
        private long lastBossbarNanos = Long.MIN_VALUE;
    }
}

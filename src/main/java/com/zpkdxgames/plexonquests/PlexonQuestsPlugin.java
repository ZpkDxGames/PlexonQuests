package com.zpkdxgames.plexonquests;

import com.zpkdxgames.plexonquests.api.PlexonQuestsAPI;
import com.zpkdxgames.plexonquests.api.PlexonQuestsAPIImpl;
import com.zpkdxgames.plexonquests.api.PlexonQuestsJournalAPI;
import com.zpkdxgames.plexonquests.api.PlexonQuestsJournalAPIImpl;
import com.zpkdxgames.plexonquests.command.Phase2Diagnostics;
import com.zpkdxgames.plexonquests.command.Phase2QuestCommand;
import com.zpkdxgames.plexonquests.command.QuestCommand;
import com.zpkdxgames.plexonquests.command.RuntimeAwareQuestCommand;
import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.gui.MenuListener;
import com.zpkdxgames.plexonquests.gui.MenuService;
import com.zpkdxgames.plexonquests.gui.Phase2JournalService;
import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.integration.PlexonQuestsExpansion;
import com.zpkdxgames.plexonquests.integration.core.CoreBridge;
import com.zpkdxgames.plexonquests.integration.core.CoreBridgeFactory;
import com.zpkdxgames.plexonquests.integration.core.CoreOriginMigrator;
import com.zpkdxgames.plexonquests.integration.core.CoreRuntimeCoordinator;
import com.zpkdxgames.plexonquests.objective.tracker.ActivitySampler;
import com.zpkdxgames.plexonquests.objective.tracker.CoreObjectiveListener;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.EffectService;
import com.zpkdxgames.plexonquests.presentation.QuestNotificationListener;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.reward.RewardService;
import com.zpkdxgames.plexonquests.rotation.RerollService;
import com.zpkdxgames.plexonquests.rotation.RotationService;
import com.zpkdxgames.plexonquests.service.AssignmentService;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.CompletionHistoryCache;
import com.zpkdxgames.plexonquests.service.FeedbackChannel;
import com.zpkdxgames.plexonquests.service.PlayerLifecycleListener;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonquests.service.QuestEligibilityService;
import com.zpkdxgames.plexonquests.service.QuestPrerequisiteService;
import com.zpkdxgames.plexonquests.service.QuestTrackingService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class PlexonQuestsPlugin extends JavaPlugin {
    private ExecutorService configExecutor;
    private ConfigManager configs;
    private StorageService storage;
    private IntegrationManager integrations;
    private ProfileService profiles;
    private ProgressService progress;
    private RotationService rotations;
    private RerollService rerolls;
    private BlockOriginService origins;
    private EffectService effects;
    private ActivitySampler activity;
    private PlexonQuestsExpansion expansion;
    private CoreBridge core;
    private CoreRuntimeCoordinator coreRuntime;
    private CoreOriginMigrator coreOriginMigrator;
    private QuestPrerequisiteService prerequisites;
    private CompletionHistoryCache completionHistory;
    private QuestTrackingService tracking;
    private Phase2JournalService phase2Journal;
    private boolean localOriginAuthority;
    private final List<BukkitTask> maintenanceTasks = new ArrayList<>();
    private BukkitTask configWatchTask;
    private ConfigSnapshot runtimeScheduleSnapshot;
    private boolean started;

    @Override
    public void onEnable() {
        try {
            core = CoreBridgeFactory.resolve(this);
            core.registerStarting();

            configExecutor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PlexonQuests-Config");
                thread.setDaemon(true);
                return thread;
            });
            configs = new ConfigManager(this);
            var initial = configs.loadInitial();

            integrations = new IntegrationManager(this, core);
            integrations.detect();
            storage = new StorageService(configs.dataDirectory(), initial.settings().storage(), getLogger());
            storage.start();

            profiles = new ProfileService(this, storage, configs, integrations);
            progress = new ProgressService(this, profiles, storage, configs);
            AssignmentService assignments = new AssignmentService(this, storage, progress);
            prerequisites = new QuestPrerequisiteService(configs);
            QuestPrerequisiteService.Snapshot graph = prerequisites.snapshot();
            completionHistory = new CompletionHistoryCache(this, storage);
            tracking = new QuestTrackingService(configs, profiles);
            QuestEligibilityService eligibility = new QuestEligibilityService(integrations, prerequisites, completionHistory);
            rotations = new RotationService(this, configs, storage, assignments, progress, eligibility);
            TextService text = new TextService(configs);
            effects = new EffectService(this, configs, profiles, text);
            progress.observer(effects);
            rerolls = new RerollService(this, configs, storage, profiles, progress, eligibility, text);
            RewardService rewards = new RewardService(this, configs, storage, profiles, progress, text, effects);
            origins = new BlockOriginService(this, configs);

            coreRuntime = new CoreRuntimeCoordinator(this, configs, core);
            coreRuntime.start();
            localOriginAuthority = coreRuntime.localOriginAuthoritative();
            if (coreRuntime.originMigrationAvailable()) {
                coreOriginMigrator = new CoreOriginMigrator(this, core.runtime(), configs);
            }

            MenuService menus = new MenuService(
                    this, configs, profiles, storage, rewards, rerolls, integrations, origins, tracking, text, configExecutor);
            phase2Journal = new Phase2JournalService(
                    this,
                    configs,
                    profiles,
                    storage,
                    rewards,
                    rerolls,
                    eligibility,
                    prerequisites,
                    completionHistory,
                    tracking,
                    text);

            registerListeners(rewards, text, menus, phase2Journal);
            registerCommand(assignments, rewards, menus, phase2Journal, text);
            registerApi(assignments, menus, phase2Journal);
            registerPlaceholderApi(text);
            integrations.registerProgressBridges(progress, profiles, rotations, configs);

            effects.start();
            activity = new ActivitySampler(this, configs, progress);
            activity.start();
            if (localOriginAuthority) {
                origins.loadExistingChunks();
            }
            configureProfiles(text);
            scheduleMaintenance();
            startRuntimeConfigWatcher();
            Bukkit.getOnlinePlayers().forEach(profiles::load);
            started = true;
            core.markReady("Quest engine ready; block acquisition=" + coreRuntime.acquisitionMode()
                    + "; origin=" + coreRuntime.originProvider() + "; prerequisite-graph=" + graph.questCount());

            getLogger().info("PlexonQuests " + getPluginMeta().getVersion() + " enabled with "
                    + initial.registry().quests().size() + " quests, " + initial.registry().pools().size()
                    + " pools, and " + initial.registry().errorCount() + " quarantined definition error(s). Runtime: "
                    + coreRuntime.acquisitionMode() + "; origin provider: " + coreRuntime.originProvider()
                    + "; prerequisite graph: " + graph.questCount() + " quest(s).");
        } catch (Exception exception) {
            if (core != null) {
                core.markFailed("Quest startup failed: " + exception.getClass().getSimpleName());
            }
            getLogger().log(Level.SEVERE, "PlexonQuests could not start safely; disabling without partial operation", exception);
            shutdown();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerListeners(
            RewardService rewards, TextService text, MenuService menus, Phase2JournalService journal) {
        var manager = Bukkit.getPluginManager();
        manager.registerEvents(new MenuListener(this, configs), this);
        manager.registerEvents(journal, this);
        manager.registerEvents(tracking, this);
        manager.registerEvents(completionHistory, this);
        manager.registerEvents(
                new CoreObjectiveListener(this, progress, origins, configs, coreRuntime, coreOriginMigrator), this);
        if (localOriginAuthority) {
            manager.registerEvents(origins, this);
        }
        if (coreOriginMigrator != null) {
            manager.registerEvents(coreOriginMigrator, this);
        }
        manager.registerEvents(new PlayerLifecycleListener(profiles, progress, rerolls), this);
        manager.registerEvents(new QuestNotificationListener(profiles, rewards, text), this);
    }

    private void registerCommand(
            AssignmentService assignments,
            RewardService rewards,
            MenuService menus,
            Phase2JournalService journal,
            TextService text) {
        PluginCommand command = Objects.requireNonNull(getCommand("quests"), "quests command missing from plugin.yml");
        QuestCommand baseHandler = new QuestCommand(
                this,
                configs,
                profiles,
                assignments,
                progress,
                rotations,
                rewards,
                menus,
                storage,
                integrations,
                origins,
                text,
                configExecutor);
        Phase2Diagnostics phase2Diagnostics = new Phase2Diagnostics(
                this, configs, profiles, storage, prerequisites, completionHistory, tracking, text);
        Phase2QuestCommand phase2Handler = new Phase2QuestCommand(
                baseHandler, journal, profiles, tracking, phase2Diagnostics, text);
        RuntimeAwareQuestCommand handler = new RuntimeAwareQuestCommand(
                phase2Handler, phase2Handler, coreRuntime, coreOriginMigrator, text);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void registerApi(AssignmentService assignments, MenuService menus, Phase2JournalService journal) {
        PlexonQuestsAPI api = new PlexonQuestsAPIImpl(
                this, configs, profiles, assignments, progress, journal, integrations);
        Bukkit.getServicesManager().register(PlexonQuestsAPI.class, api, this, ServicePriority.Normal);

        PlexonQuestsJournalAPI journalApi = new PlexonQuestsJournalAPIImpl(
                this, configs, profiles, journal.stateResolver(), prerequisites);
        Bukkit.getServicesManager().register(PlexonQuestsJournalAPI.class, journalApi, this, ServicePriority.Normal);
    }

    private void registerPlaceholderApi(TextService text) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        expansion = new PlexonQuestsExpansion(
                configs, profiles, integrations, rerolls, text, getPluginMeta().getVersion());
        if (!expansion.register()) {
            getLogger().warning("PlaceholderAPI was present but the plexonquests expansion did not register");
            expansion = null;
        }
    }

    private void configureProfiles(TextService text) {
        profiles.readyHandler((player, profile) -> {
            tracking.reconcile(player, profile);
            progress.reindex(profile);
            rerolls.warm(player);
            completionHistory.load(player, profile).whenComplete((ignored, failure) ->
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (failure != null
                                || !player.isOnline()
                                || profiles.profile(player).orElse(null) != profile) {
                            return;
                        }
                        rotations.ensure(player, profile);
                        progress.reindex(profile);
                    }));
            long delay = configs.snapshot().settings().feedback().joinReminderDelayTicks();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()
                        || profiles.profile(player).orElse(null) != profile
                        || !profile.preferences().enabled(FeedbackChannel.JOIN_REMINDERS)) {
                    return;
                }
                long active = profile.assignments().stream()
                        .filter(assignment -> assignment.state() == AssignmentState.ACTIVE).count();
                player.sendMessage(text.rawMessage("join-reminder", Map.of(
                        "active", Long.toString(active),
                        "claimable", Long.toString(profile.claimableCount()))));
            }, delay);
        });
    }

    private void startRuntimeConfigWatcher() {
        runtimeScheduleSnapshot = configs.snapshot();
        if (configWatchTask != null) {
            configWatchTask.cancel();
        }
        configWatchTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshRuntimeConfig, 20L, 20L);
    }

    private void refreshRuntimeConfig() {
        ConfigSnapshot snapshot = configs.snapshot();
        if (snapshot == runtimeScheduleSnapshot) {
            return;
        }
        runtimeScheduleSnapshot = snapshot;

        if (coreRuntime != null) {
            try {
                coreRuntime.refresh();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE,
                        "Core Runtime subscription refresh failed; previous runtime remains active", exception);
                core.markDegraded("Core Runtime refresh failed; restart recommended");
            }
        }
        integrations.detect();
        completionHistory.invalidateAll();
        profiles.onlineProfiles().forEach(profile -> {
            profiles.refreshRankCategory(profile);
            Player player = Bukkit.getPlayer(profile.playerId());
            if (player != null && player.isOnline()) {
                tracking.reconcile(player, profile);
                completionHistory.load(player, profile).whenComplete((ignored, failure) -> {
                    if (failure != null) {
                        getLogger().log(Level.WARNING, "Could not reload completion history after configuration activation", failure);
                    }
                });
            }
            progress.reindex(profile);
        });
        if (activity != null) {
            activity.restart();
        }
        scheduleMaintenance();
    }

    private void scheduleMaintenance() {
        cancelMaintenanceTasks();

        long flushTicks = ticks(configs.snapshot().settings().storage().flushInterval(), 20L);
        maintenanceTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> storage.flushDirty().exceptionally(failure -> {
                    getLogger().log(Level.WARNING, "Scheduled quest progress flush failed", failure);
                    return 0;
                }),
                flushTicks,
                flushTicks));

        long checkpointTicks = ticks(configs.snapshot().settings().storage().checkpointInterval(), 1_200L);
        maintenanceTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> storage.checkpoint().exceptionally(failure -> {
                    getLogger().log(Level.WARNING, "Scheduled SQLite checkpoint failed", failure);
                    return null;
                }),
                checkpointTicks,
                checkpointTicks));

        maintenanceTasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> storage.maintenance().exceptionally(failure -> {
                    getLogger().log(Level.WARNING, "Scheduled quest storage maintenance failed", failure);
                    return null;
                }),
                1_728_000L,
                1_728_000L));

        maintenanceTasks.add(Bukkit.getScheduler().runTaskTimer(
                this,
                () -> profiles.onlineProfiles().forEach(profile -> {
                    Player player = Bukkit.getPlayer(profile.playerId());
                    if (player != null && player.isOnline()) {
                        profiles.refreshRankCategory(profile);
                        rotations.ensure(player, profile);
                    }
                }),
                1_200L,
                1_200L));
    }

    private void cancelMaintenanceTasks() {
        maintenanceTasks.forEach(BukkitTask::cancel);
        maintenanceTasks.clear();
    }

    private static long ticks(Duration duration, long minimum) {
        return Math.max(minimum, Math.max(1L, duration.toMillis() / 50L));
    }

    @Override
    public void onDisable() {
        shutdown();
    }

    private void shutdown() {
        if (configWatchTask != null) {
            configWatchTask.cancel();
            configWatchTask = null;
        }
        cancelMaintenanceTasks();
        runtimeScheduleSnapshot = null;
        if (coreRuntime != null) {
            try {
                coreRuntime.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Could not close Core Runtime subscriptions cleanly", exception);
            }
            coreRuntime = null;
        }
        if (coreOriginMigrator != null) {
            coreOriginMigrator.close();
            coreOriginMigrator = null;
        }
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (activity != null) {
            activity.close();
            activity = null;
        }
        if (completionHistory != null) {
            completionHistory.close();
            completionHistory = null;
        }
        if (effects != null) {
            effects.close();
            effects = null;
        }
        if (origins != null && localOriginAuthority) {
            try {
                origins.saveAll();
            } catch (RuntimeException exception) {
                getLogger().log(Level.SEVERE, "Could not save block-origin data during shutdown", exception);
            }
        }
        if (profiles != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                profiles.unload(player);
            }
        }
        if (storage != null) {
            storage.close();
            storage = null;
        }
        if (configExecutor != null) {
            configExecutor.shutdown();
            try {
                if (!configExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
                    getLogger().warning("Configuration executor did not stop within five seconds");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            configExecutor = null;
        }
        if (core != null) {
            core.unregister();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        if (started) {
            started = false;
            getLogger().info("PlexonQuests disabled after flushing persistent state.");
        }
    }
}

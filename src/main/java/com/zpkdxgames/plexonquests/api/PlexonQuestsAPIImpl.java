package com.zpkdxgames.plexonquests.api;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.gui.MenuService;
import com.zpkdxgames.plexonquests.integration.IntegrationManager;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.quest.QuestDefinition;
import com.zpkdxgames.plexonquests.quest.QuestScope;
import com.zpkdxgames.plexonquests.service.AssignmentService;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlexonQuestsAPIImpl implements PlexonQuestsAPI {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final ProfileService profiles;
    private final AssignmentService assignments;
    private final ProgressService progress;
    private final MenuService menus;
    private final IntegrationManager integrations;

    public PlexonQuestsAPIImpl(
            JavaPlugin plugin,
            ConfigManager configs,
            ProfileService profiles,
            AssignmentService assignments,
            ProgressService progress,
            MenuService menus,
            IntegrationManager integrations) {
        this.plugin = plugin;
        this.configs = configs;
        this.profiles = profiles;
        this.assignments = assignments;
        this.progress = progress;
        this.menus = menus;
        this.integrations = integrations;
    }

    @Override
    public CompletableFuture<List<AssignmentView>> activeAssignments(UUID playerId) {
        return onPrimary(() -> profiles.profile(playerId).map(profile -> profile.assignments().stream()
                        .filter(assignment -> assignment.state() == AssignmentState.ACTIVE)
                        .map(PlexonQuestsAPIImpl::view)
                        .toList())
                .orElseGet(List::of));
    }

    @Override
    public CompletableFuture<Optional<AssignmentView>> assignment(UUID playerId, UUID assignmentId) {
        return onPrimary(() -> profiles.profile(playerId)
                .flatMap(profile -> profile.assignment(assignmentId))
                .map(PlexonQuestsAPIImpl::view));
    }

    @Override
    public Optional<QuestDefinitionView> questDefinition(String questId) {
        if (questId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(configs.snapshot().registry().quests().get(questId.toLowerCase(Locale.ROOT)))
                .map(PlexonQuestsAPIImpl::view);
    }

    @Override
    public CompletableFuture<Boolean> assignManual(UUID playerId, String questId) {
        return onPrimary(() -> {
            Player player = Bukkit.getPlayer(playerId);
            PlayerProfile profile = profiles.profile(playerId).orElse(null);
            QuestDefinition definition = questId == null
                    ? null
                    : configs.snapshot().registry().quests().get(questId.toLowerCase(Locale.ROOT));
            if (player == null || profile == null || definition == null || definition.scope() != QuestScope.MANUAL) {
                return CompletableFuture.completedFuture(false);
            }
            return assignments.add(
                    player, profile, definition, "", "manual:" + UUID.randomUUID(), Instant.now(), null);
        }).thenCompose(future -> future);
    }

    @Override
    public CompletableFuture<Void> submitProgress(UUID playerId, ExternalProgressContribution external) {
        return onPrimary(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && profiles.profile(playerId).isPresent()) {
                Contribution contribution = new Contribution(
                        ObjectiveType.valueOf(external.type().name()),
                        external.amount(),
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
                        false,
                        external.unique(),
                        "",
                        "",
                        external.sourceToken());
                return progress.contributeAsync(player, contribution);
            }
            return CompletableFuture.completedFuture(false);
        }).thenCompose(future -> future).thenApply(ignored -> null);
    }

    @Override
    public CompletableFuture<Boolean> isComplete(UUID playerId, UUID assignmentId) {
        return assignment(playerId, assignmentId).thenApply(view -> view.map(AssignmentView::complete).orElse(false));
    }

    @Override
    public CompletableFuture<Boolean> isClaimable(UUID playerId, UUID assignmentId) {
        return assignment(playerId, assignmentId).thenApply(view -> view.map(AssignmentView::claimable).orElse(false));
    }

    @Override
    public CompletableFuture<Boolean> pin(UUID playerId, UUID assignmentId) {
        return onPrimary(() -> {
            PlayerProfile profile = profiles.profile(playerId).orElse(null);
            if (profile == null || profile.assignment(assignmentId).isEmpty()) {
                return false;
            }
            profile.pinnedAssignment(assignmentId);
            profiles.persistPreferences(profile);
            return true;
        });
    }

    @Override
    public CompletableFuture<Boolean> unpin(UUID playerId) {
        return onPrimary(() -> {
            PlayerProfile profile = profiles.profile(playerId).orElse(null);
            if (profile == null || profile.pinnedAssignment().isEmpty()) {
                return false;
            }
            profile.pinnedAssignment(null);
            profiles.persistPreferences(profile);
            return true;
        });
    }

    @Override
    public CompletableFuture<Void> openJournal(UUID playerId, String rawScope) {
        return onPrimary(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.hasPermission("plexonquests.use")) {
                QuestScope scope = parseScope(rawScope);
                menus.openJournal(player, scope);
            }
            return null;
        });
    }

    @Override
    public Map<String, IntegrationView> integrationStates() {
        Map<String, IntegrationView> output = new LinkedHashMap<>();
        integrations.states().forEach((id, state) -> output.put(id, new IntegrationView(
                state.id(), state.pluginName(), state.status().name(), state.detectedVersion(), state.detail())));
        return Map.copyOf(output);
    }

    private <T> CompletableFuture<T> onPrimary(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (RuntimeException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private static QuestScope parseScope(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("all")) {
            return null;
        }
        try {
            return QuestScope.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static AssignmentView view(QuestAssignment assignment) {
        return new AssignmentView(
                assignment.id(),
                assignment.definition().id(),
                assignment.definition().scope().name(),
                assignment.state().name(),
                assignment.definition().display().name(),
                assignment.definition().rarity(),
                assignment.periodKey(),
                assignment.assignedAt(),
                assignment.expiresAt().orElse(null),
                assignment.percentage(),
                assignment.objectives().stream().map(objective -> new ObjectiveView(
                                objective.definition().id(),
                                objective.definition().type().name(),
                                objective.definition().display(),
                                objective.current(),
                                objective.required(),
                                objective.complete()))
                        .toList());
    }

    private static QuestDefinitionView view(QuestDefinition quest) {
        return new QuestDefinitionView(
                quest.id(),
                quest.revision(),
                quest.enabled(),
                quest.scope().name(),
                quest.category(),
                quest.rarity(),
                quest.display().name(),
                quest.display().shortDescription(),
                quest.completionMode().name(),
                quest.claimMode().name(),
                List.copyOf(quest.objectives().keySet()));
    }
}

package com.zpkdxgames.plexonquests.reward;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.OverflowPolicy;
import com.zpkdxgames.plexonquests.event.QuestClaimUncertainEvent;
import com.zpkdxgames.plexonquests.event.QuestClaimedEvent;
import com.zpkdxgames.plexonquests.event.QuestExpireEvent;
import com.zpkdxgames.plexonquests.event.QuestPreClaimEvent;
import com.zpkdxgames.plexonquests.integration.EconomyBridge;
import com.zpkdxgames.plexonquests.integration.PermissionBridge;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.persistence.StorageService;
import com.zpkdxgames.plexonquests.presentation.EffectService;
import com.zpkdxgames.plexonquests.presentation.TextService;
import com.zpkdxgames.plexonquests.quest.AssignmentState;
import com.zpkdxgames.plexonquests.quest.QuestAssignment;
import com.zpkdxgames.plexonquests.service.PlayerProfile;
import com.zpkdxgames.plexonquests.service.ProfileService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import com.zpkdxgames.plexonquests.util.Hashing;
import com.zpkdxgames.plexonquests.util.LogSanitizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class RewardService {
    private final JavaPlugin plugin;
    private final ConfigManager configs;
    private final StorageService storage;
    private final ProfileService profiles;
    private final ProgressService progress;
    private final TextService text;
    private final EffectService effects;
    private final EconomyBridge economy = new EconomyBridge();
    private final PermissionBridge permissions;

    public RewardService(
            JavaPlugin plugin,
            ConfigManager configs,
            StorageService storage,
            ProfileService profiles,
            ProgressService progress,
            TextService text,
            EffectService effects) {
        this.plugin = plugin;
        this.configs = configs;
        this.storage = storage;
        this.profiles = profiles;
        this.progress = progress;
        this.text = text;
        this.effects = effects;
        this.permissions = new PermissionBridge(plugin.getLogger());
    }

    public void claim(Player player, QuestAssignment assignment) {
        if (!Bukkit.isPrimaryThread()) {
            UUID playerId = player.getUniqueId();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null) {
                    claim(current, assignment);
                }
            });
            return;
        }
        if (assignment.state() == AssignmentState.COMPLETED && claimDeadlinePassed(assignment, Instant.now())) {
            expireBeforeClaim(player, assignment);
            return;
        }
        if (assignment.state() != AssignmentState.COMPLETED) {
            player.sendMessage(text.message("claims.not-complete", Map.of()));
            return;
        }
        QuestPreClaimEvent preClaim = new QuestPreClaimEvent(
                player, assignment.id(), assignment.definition().id());
        Bukkit.getPluginManager().callEvent(preClaim);
        if (preClaim.isCancelled()) {
            return;
        }
        DeliveryPlan plan = plan(assignment);
        Preflight preflight = preflight(player, plan);
        if (!preflight.success()) {
            player.sendMessage(text.message(preflight.messagePath(), Map.of("provider", preflight.detail())));
            return;
        }
        if (!assignment.markClaiming()) {
            player.sendMessage(text.message("claims.not-complete", Map.of()));
            return;
        }
        String transactionId = UUID.randomUUID().toString();
        player.sendMessage(text.message("claims.preparing", Map.of()));
        storage.reserveClaim(transactionId, assignment).whenComplete((reserved, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (failure != null || !Boolean.TRUE.equals(reserved)) {
                        assignment.rollbackClaim();
                        if (current != null) {
                            current.sendMessage(text.message("claims.already-claimed", Map.of()));
                        }
                        if (failure != null) {
                            plugin.getLogger().log(Level.WARNING, "Claim reservation failed", failure);
                        }
                        return;
                    }
                    if (current == null) {
                        assignment.rollbackClaim();
                        storage.rollbackClaim(transactionId, assignment, "player disconnected before delivery");
                        return;
                    }
                    DeliveryResult delivered = deliver(current, plan);
                    if (!delivered.success()) {
                        if (!delivered.sideEffectOccurred()) {
                            assignment.rollbackClaim();
                            storage.rollbackClaim(transactionId, assignment, delivered.detail());
                            current.sendMessage(text.message("general.internal-error", Map.of()));
                        } else {
                            markUncertain(current, assignment, transactionId, delivered.detail());
                        }
                        return;
                    }
                    finishSuccessfulClaim(current, assignment, transactionId);
                }));
    }

    private void finishSuccessfulClaim(Player player, QuestAssignment assignment, String transactionId) {
        storage.completeClaim(transactionId, assignment).whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player current = Bukkit.getPlayer(player.getUniqueId());
                    if (failure != null) {
                        if (current != null) {
                            markUncertain(current, assignment, transactionId, "delivery succeeded but commit failed");
                        }
                        plugin.getLogger().log(Level.SEVERE, "Delivered claim could not be committed: " + transactionId, failure);
                        return;
                    }
                    assignment.markClaimed(java.time.Instant.now());
                    PlayerProfile profile = profiles.profile(player.getUniqueId()).orElse(null);
                    if (profile != null) {
                        profile.incrementCompletedTotal();
                        if (profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()) {
                            profile.pinnedAssignment(null);
                            profiles.persistPreferences(profile);
                        }
                        progress.reindex(profile);
                    }
                    if (current != null) {
                        effects.claimed(current, assignment);
                        current.sendMessage(text.message(
                                "claims.success",
                                Map.of("quest_name", text.plain(text.parse(assignment.definition().display().name())))));
                        Bukkit.getPluginManager().callEvent(new QuestClaimedEvent(
                                current, assignment.id(), assignment.definition().id(), transactionId));
                        progress.contribute(current, Contribution.simple(ObjectiveType.QUESTS_CLAIMED, 1L, current));
                    }
                }));
    }

    private void markUncertain(
            Player player, QuestAssignment assignment, String transactionId, String detail) {
        storage.uncertainClaim(transactionId, assignment, detail);
        player.sendMessage(text.message("claims.uncertain", Map.of("transaction", transactionId)));
        Bukkit.getPluginManager().callEvent(new QuestClaimUncertainEvent(
                player, assignment.id(), assignment.definition().id(), transactionId, detail));
    }

    private boolean claimDeadlinePassed(QuestAssignment assignment, Instant now) {
        return assignment.expiresAt()
                .map(expiry -> now.isAfter(expiry.plus(configs.snapshot().settings().rotation().claimGrace())))
                .orElse(false);
    }

    private void expireBeforeClaim(Player player, QuestAssignment assignment) {
        if (!assignment.expire()) {
            return;
        }
        PlayerProfile profile = profiles.profile(player).orElse(null);
        if (profile != null) {
            if (profile.pinnedAssignment().filter(assignment.id()::equals).isPresent()) {
                profile.pinnedAssignment(null);
                profiles.persistPreferences(profile);
            }
            progress.reindex(profile);
        }
        storage.archive(assignment, AssignmentState.EXPIRED).exceptionally(failure -> {
            plugin.getLogger().log(Level.SEVERE, "Could not archive expired quest before claim", failure);
            return null;
        });
        Bukkit.getPluginManager().callEvent(
                new QuestExpireEvent(player, assignment.id(), assignment.definition().id()));
    }

    private DeliveryPlan plan(QuestAssignment assignment) {
        RewardBundle bundle = assignment.definition().rewards();
        if (bundle.mode() == RewardMode.ALL) {
            return new DeliveryPlan(bundle.entries());
        }
        long total = bundle.entries().stream().mapToLong(RewardDefinition::weight).sum();
        SplittableRandom random = new SplittableRandom(Hashing.stableLong(assignment.id().toString()));
        long roll = random.nextLong(total);
        long cumulative = 0L;
        for (RewardDefinition reward : bundle.entries()) {
            cumulative += reward.weight();
            if (roll < cumulative) {
                return new DeliveryPlan(List.of(reward));
            }
        }
        return new DeliveryPlan(List.of(bundle.entries().getLast()));
    }

    private Preflight preflight(Player player, DeliveryPlan plan) {
        List<ItemStack> items = new ArrayList<>();
        for (RewardDefinition reward : plan.rewards()) {
            try {
                switch (reward.type()) {
                    case ITEM -> items.addAll(items(reward));
                    case MONEY -> {
                        if (!economy.available()) {
                            return Preflight.failure("claims.provider-unavailable", "Vault economy");
                        }
                    }
                    case PERMISSION -> {
                        if (!permissions.available()) {
                            return Preflight.failure("claims.provider-unavailable", "LuckPerms");
                        }
                    }
                    case PLAYER_COMMAND -> {
                        return Preflight.failure("claims.provider-unavailable", "PLAYER_COMMAND rewards are disabled");
                    }
                    case COMMAND -> {
                        if (reward.command().isBlank() || reward.command().contains("\n") || reward.command().contains("\r")) {
                            return Preflight.failure("general.internal-error", "invalid command reward");
                        }
                    }
                    case PLEXON_KEY -> {
                        if (reward.fallbackCommand().isBlank()) {
                            return Preflight.failure("claims.provider-unavailable", "PlexonKeys delivery API");
                        }
                    }
                    case EXPERIENCE_POINTS, EXPERIENCE_LEVELS, MESSAGE, SOUND, EFFECT -> {
                        // Fully preflighted by configuration validation.
                    }
                }
            } catch (IllegalArgumentException exception) {
                return Preflight.failure("general.internal-error", exception.getMessage());
            }
        }
        if (configs.snapshot().settings().claims().overflowPolicy() == OverflowPolicy.CANCEL
                && !canFit(player, items)) {
            return Preflight.failure("claims.inventory-full", "inventory");
        }
        return Preflight.ok();
    }

    private DeliveryResult deliver(Player player, DeliveryPlan plan) {
        boolean sideEffect = false;
        try {
            for (RewardDefinition reward : plan.rewards()) {
                switch (reward.type()) {
                    case ITEM -> {
                        for (ItemStack item : items(reward)) {
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                            if (!leftover.isEmpty()) {
                                if (configs.snapshot().settings().claims().overflowPolicy() == OverflowPolicy.DROP) {
                                    leftover.values().forEach(stack ->
                                            player.getWorld().dropItemNaturally(player.getLocation(), stack));
                                } else {
                                    return new DeliveryResult(false, sideEffect, "inventory capacity changed during delivery");
                                }
                            }
                            sideEffect = true;
                        }
                    }
                    case EXPERIENCE_POINTS -> {
                        player.giveExp(Math.toIntExact(reward.amount()));
                        sideEffect = true;
                    }
                    case EXPERIENCE_LEVELS -> {
                        player.giveExpLevels(Math.toIntExact(reward.amount()));
                        sideEffect = true;
                    }
                    case MONEY -> {
                        EconomyBridge.Result result = economy.deposit(player, reward.decimalAmount());
                        if (!result.success()) {
                            return new DeliveryResult(false, sideEffect, "economy: " + result.detail());
                        }
                        sideEffect = true;
                    }
                    case PERMISSION -> {
                        PermissionBridge.Result result = permissions.grant(
                                player, reward.permission(), reward.permissionDuration());
                        if (!result.success()) {
                            return new DeliveryResult(false, sideEffect, "permission: " + result.detail());
                        }
                        sideEffect = true;
                    }
                    case COMMAND -> {
                        if (!dispatch(reward.command(), player)) {
                            return new DeliveryResult(false, sideEffect, "console command returned false");
                        }
                        sideEffect = true;
                    }
                    case PLEXON_KEY -> {
                        if (!dispatch(reward.fallbackCommand(), player)) {
                            return new DeliveryResult(false, sideEffect, "Plexon key fallback command returned false");
                        }
                        sideEffect = true;
                    }
                    case MESSAGE -> {
                        player.sendMessage(text.parse(reward.display()));
                        sideEffect = true;
                    }
                    case SOUND, EFFECT -> sideEffect = true;
                    case PLAYER_COMMAND -> {
                        return new DeliveryResult(false, sideEffect, "PLAYER_COMMAND rewards are disabled");
                    }
                }
            }
            return new DeliveryResult(true, sideEffect, "delivered");
        } catch (RuntimeException exception) {
            return new DeliveryResult(
                    false, sideEffect, LogSanitizer.clean(exception.getClass().getSimpleName() + ": " + exception.getMessage()));
        }
    }

    private List<ItemStack> items(RewardDefinition reward) {
        ItemStack base;
        if (!reward.serializedItem().isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(reward.serializedItem());
            if (bytes.length > configs.snapshot().settings().security().maximumSerializedItemBytes()) {
                throw new IllegalArgumentException("Serialized reward item exceeds the configured size limit");
            }
            base = ItemStack.deserializeBytes(bytes);
        } else {
            Material material = reward.material();
            if (material == null || material.isAir()) {
                throw new IllegalArgumentException("Reward item has no valid material");
            }
            base = new ItemStack(material);
        }
        long remaining = reward.amount();
        List<ItemStack> stacks = new ArrayList<>();
        while (remaining > 0L) {
            int amount = (int) Math.min(remaining, base.getMaxStackSize());
            ItemStack stack = base.clone();
            stack.setAmount(amount);
            stacks.add(stack);
            remaining -= amount;
            if (stacks.size() > 4096) {
                throw new IllegalArgumentException("Reward item bundle is unreasonably large");
            }
        }
        return List.copyOf(stacks);
    }

    private static boolean canFit(Player player, List<ItemStack> rewards) {
        ItemStack[] simulated = player.getInventory().getStorageContents().clone();
        for (ItemStack reward : rewards) {
            int remaining = reward.getAmount();
            for (int index = 0; index < simulated.length && remaining > 0; index++) {
                ItemStack current = simulated[index];
                if (current != null && !current.getType().isAir() && current.isSimilar(reward)) {
                    int space = Math.max(0, current.getMaxStackSize() - current.getAmount());
                    int moved = Math.min(space, remaining);
                    if (moved > 0) {
                        current = current.clone();
                        current.setAmount(current.getAmount() + moved);
                        simulated[index] = current;
                        remaining -= moved;
                    }
                }
            }
            for (int index = 0; index < simulated.length && remaining > 0; index++) {
                ItemStack current = simulated[index];
                if (current == null || current.getType().isAir()) {
                    int moved = Math.min(reward.getMaxStackSize(), remaining);
                    ItemStack placed = reward.clone();
                    placed.setAmount(moved);
                    simulated[index] = placed;
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean dispatch(String commandTemplate, Player player) {
        String command = commandTemplate
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private record DeliveryPlan(List<RewardDefinition> rewards) {
        private DeliveryPlan {
            rewards = List.copyOf(rewards);
        }
    }

    private record Preflight(boolean success, String messagePath, String detail) {
        private static Preflight ok() { return new Preflight(true, "", ""); }

        private static Preflight failure(String messagePath, String detail) {
            return new Preflight(false, messagePath, detail == null ? "unknown" : detail);
        }
    }

    private record DeliveryResult(boolean success, boolean sideEffectOccurred, String detail) {}
}

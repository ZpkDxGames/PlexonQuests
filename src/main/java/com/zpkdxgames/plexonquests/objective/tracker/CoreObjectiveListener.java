package com.zpkdxgames.plexonquests.objective.tracker;

import com.zpkdxgames.plexonquests.config.ConfigManager;
import com.zpkdxgames.plexonquests.config.ConfigSnapshot;
import com.zpkdxgames.plexonquests.integration.core.CoreOriginMigrator;
import com.zpkdxgames.plexonquests.integration.core.CoreRuntime;
import com.zpkdxgames.plexonquests.integration.core.CoreRuntimeCoordinator;
import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.objective.block.BlockObjectiveProcessor;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

public final class CoreObjectiveListener implements Listener {
    private final ProgressService progress;
    private final BlockOriginService origins;
    private final ConfigManager configs;
    private final BlockObjectiveProcessor blockProcessor;
    private final CoreRuntimeCoordinator coreRuntime;
    private final CoreOriginMigrator coreOrigins;
    private final NamespacedKey spawnReasonKey;
    private volatile ConfigSnapshot spawnReasonSnapshot;
    private volatile boolean spawnReasonTracking = true;

    public CoreObjectiveListener(
            JavaPlugin plugin,
            ProgressService progress,
            BlockOriginService origins,
            ConfigManager configs,
            CoreRuntimeCoordinator coreRuntime,
            CoreOriginMigrator coreOrigins) {
        this.progress = progress;
        this.origins = origins;
        this.configs = configs;
        this.blockProcessor = new BlockObjectiveProcessor(progress);
        this.coreRuntime = coreRuntime;
        this.coreOrigins = coreOrigins;
        this.spawnReasonKey = new NamespacedKey(plugin, "spawn_reason");
    }

    public CoreObjectiveListener(
            JavaPlugin plugin,
            ProgressService progress,
            BlockOriginService origins,
            ConfigManager configs,
            CoreRuntimeCoordinator coreRuntime) {
        this(plugin, progress, origins, configs, coreRuntime, null);
    }

    public CoreObjectiveListener(
            JavaPlugin plugin,
            ProgressService progress,
            BlockOriginService origins,
            ConfigManager configs) {
        this(plugin, progress, origins, configs, null, null);
    }

    /** Compatibility constructor retained for isolated listener tests. */
    public CoreObjectiveListener(JavaPlugin plugin, ProgressService progress, BlockOriginService origins) {
        this(plugin, progress, origins, null, null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        CoreRuntime.BlockFact coreFact = coreRuntime == null ? null : coreRuntime.consume(event);
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        Material material = event.getBlock().getType();
        boolean coreOrigin = coreRuntime != null && coreRuntime.coreOriginAuthoritative();
        try {
            blockProcessor.breakBlock(
                    player,
                    material,
                    () -> mature(event),
                    () -> {
                        if (coreOrigin) {
                            return coreOrigins == null
                                    ? BlockObjectiveProcessor.OriginState.UNKNOWN
                                    : coreOrigins.resolve(event.getBlock(), coreFact);
                        }
                        BlockOriginService.OriginResult local = origins.origin(event.getBlock());
                        return local.known()
                                ? (local.natural()
                                        ? BlockObjectiveProcessor.OriginState.NATURAL
                                        : BlockObjectiveProcessor.OriginState.PLAYER_PLACED)
                                : BlockObjectiveProcessor.OriginState.UNKNOWN;
                    });
        } finally {
            if (!coreOrigin) {
                // Local provenance remains authoritative in standalone/Core legacy/forced LOCAL modes.
                origins.markBroken(event.getBlock());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        blockProcessor.placeBlock(event.getPlayer(), event.getBlockPlaced().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!spawnReasonTrackingRequired()) {
            return;
        }
        event.getEntity().getPersistentDataContainer().set(
                spawnReasonKey, PersistentDataType.STRING, event.getSpawnReason().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null
                || !progress.interested(killer, ObjectiveType.KILL_ENTITY, event.getEntity().getType())) {
            return;
        }
        CreatureSpawnEvent.SpawnReason spawnReason = progress.requiresSpawnReason(
                        killer, ObjectiveType.KILL_ENTITY, event.getEntity().getType())
                ? spawnReason(event.getEntity())
                : null;
        progress.contribute(killer, entityContribution(
                ObjectiveType.KILL_ENTITY,
                killer,
                event.getEntity(),
                1L,
                null,
                spawnReason));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = damagingPlayer(event);
        if (player == null || !(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!progress.interested(player, ObjectiveType.DAMAGE_ENTITY, living.getType())) {
            return;
        }
        long normalizedDamage = Math.max(1L, Math.round(event.getFinalDamage()));
        CreatureSpawnEvent.SpawnReason spawnReason = progress.requiresSpawnReason(
                        player, ObjectiveType.DAMAGE_ENTITY, living.getType())
                ? spawnReason(living)
                : null;
        progress.contribute(player, entityContribution(
                ObjectiveType.DAMAGE_ENTITY,
                player,
                living,
                normalizedDamage,
                event.getCause(),
                spawnReason));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!progress.interested(player, ObjectiveType.CATCH_FISH)) {
            return;
        }
        Material material = null;
        long amount = 1L;
        if (event.getCaught() instanceof Item item) {
            material = item.getItemStack().getType();
            amount = item.getItemStack().getAmount();
        }
        boolean candidateInterested = material != null
                && progress.interested(player, ObjectiveType.CATCH_FISH, material);
        candidateInterested |= progress.interested(
                player, ObjectiveType.CATCH_FISH, event.getCaught().getType());
        if (!candidateInterested) {
            return;
        }
        progress.contribute(player, new Contribution(
                ObjectiveType.CATCH_FISH,
                amount,
                material,
                event.getCaught().getType(),
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
                true,
                "",
                "",
                ""));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getRecipe() == null) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (!progress.interested(player, ObjectiveType.CRAFT_ITEM, result.getType())) {
            return;
        }
        long amount = result.getAmount();
        if (event.isShiftClick()) {
            CraftingInventory crafting = event.getInventory();
            int crafts = maximumCrafts(crafting);
            int capacity = inventoryCapacity(player, result);
            amount = (long) result.getAmount() * Math.min(crafts, capacity / Math.max(1, result.getAmount()));
        }
        if (amount <= 0L) {
            return;
        }
        progress.contribute(player, itemContribution(ObjectiveType.CRAFT_ITEM, player, result.getType(), amount));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!progress.interested(player, ObjectiveType.SMELT_ITEM, event.getItemType())) {
            return;
        }
        progress.contribute(player, itemContribution(
                ObjectiveType.SMELT_ITEM, player, event.getItemType(), event.getItemAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        Material material = event.getItem().getType();
        if (!progress.interested(player, ObjectiveType.ENCHANT_ITEM, material)) {
            return;
        }
        progress.contribute(player, itemContribution(
                ObjectiveType.ENCHANT_ITEM, player, material, 1L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingExtract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !progress.interested(player, ObjectiveType.BREW_POTION)
                || !(event.getView().getTopInventory() instanceof BrewerInventory)
                || event.getClickedInventory() != event.getView().getTopInventory()
                || event.getRawSlot() < 0
                || event.getRawSlot() > 2
                || !removesItem(event.getAction())) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null
                || item.getType().isAir()
                || !progress.interested(player, ObjectiveType.BREW_POTION, item.getType())) {
            return;
        }
        int amount = removedAmount(
                event.getAction(), item, event.getCursor(), inventoryCapacity(player, item));
        if (amount <= 0) {
            return;
        }
        progress.contribute(player, itemContribution(
                ObjectiveType.BREW_POTION, player, item.getType(), amount));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!progress.interested(player, ObjectiveType.VISIT_WORLD)) {
            return;
        }
        progress.contribute(player, Contribution.simple(ObjectiveType.VISIT_WORLD, 1L, player));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        if (!progress.interested(player, ObjectiveType.COMPLETE_ADVANCEMENT)) {
            return;
        }
        progress.contribute(player, new Contribution(
                ObjectiveType.COMPLETE_ADVANCEMENT,
                1L,
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
                true,
                "",
                event.getAdvancement().getKey().toString().toLowerCase(Locale.ROOT),
                ""));
    }

    private boolean spawnReasonTrackingRequired() {
        if (configs == null) {
            return true;
        }
        ConfigSnapshot snapshot = configs.snapshot();
        if (snapshot != spawnReasonSnapshot) {
            boolean required = snapshot.registry().quests().values().stream()
                    .filter(quest -> quest.enabled())
                    .flatMap(quest -> quest.objectives().values().stream())
                    .anyMatch(objective -> !objective.filters().spawnReasons().isEmpty());
            spawnReasonTracking = required;
            spawnReasonSnapshot = snapshot;
        }
        return spawnReasonTracking;
    }

    private static Contribution entityContribution(
            ObjectiveType type,
            Player player,
            LivingEntity entity,
            long amount,
            org.bukkit.event.entity.EntityDamageEvent.DamageCause cause,
            CreatureSpawnEvent.SpawnReason spawnReason) {
        return new Contribution(
                type,
                amount,
                null,
                entity.getType(),
                cause,
                spawnReason,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                false,
                false,
                true,
                entity instanceof Monster,
                false,
                true,
                "",
                "",
                "");
    }

    private static Contribution itemContribution(ObjectiveType type, Player player, Material material, long amount) {
        return new Contribution(
                type,
                amount,
                material,
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
                true,
                "",
                "",
                "");
    }

    private CreatureSpawnEvent.SpawnReason spawnReason(LivingEntity entity) {
        String value = entity.getPersistentDataContainer().get(spawnReasonKey, PersistentDataType.STRING);
        if (value == null) {
            return null;
        }
        try {
            return CreatureSpawnEvent.SpawnReason.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Player damagingPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    private static boolean mature(BlockBreakEvent event) {
        return !(event.getBlock().getBlockData() instanceof Ageable ageable)
                || ageable.getAge() >= ageable.getMaximumAge();
    }

    private static int maximumCrafts(CraftingInventory inventory) {
        int maximum = Integer.MAX_VALUE;
        boolean ingredient = false;
        for (ItemStack item : inventory.getMatrix()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ingredient = true;
            maximum = Math.min(maximum, item.getAmount());
        }
        return ingredient ? maximum : 0;
    }

    private static int inventoryCapacity(Player player, ItemStack result) {
        int capacity = 0;
        int maximum = result.getMaxStackSize();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                capacity += maximum;
            } else if (item.isSimilar(result)) {
                capacity += Math.max(0, maximum - item.getAmount());
            }
        }
        return capacity;
    }

    private static boolean removesItem(InventoryAction action) {
        return action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD;
    }

    static int removedAmount(InventoryAction action, ItemStack clicked, ItemStack cursor, int shiftCapacity) {
        int available = clicked.getAmount();
        return switch (action) {
            case PICKUP_ONE -> Math.min(1, available);
            case PICKUP_HALF -> Math.min((available + 1) / 2, available);
            case PICKUP_SOME -> {
                int held = cursor == null || cursor.getType().isAir() ? 0 : cursor.getAmount();
                yield Math.min(available, Math.max(0, clicked.getMaxStackSize() - held));
            }
            case MOVE_TO_OTHER_INVENTORY -> Math.min(available, Math.max(0, shiftCapacity));
            case PICKUP_ALL, HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> available;
            default -> 0;
        };
    }
}

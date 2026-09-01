package com.zpkdxgames.plexonquests.objective.tracker;

import com.zpkdxgames.plexonquests.objective.Contribution;
import com.zpkdxgames.plexonquests.objective.ObjectiveType;
import com.zpkdxgames.plexonquests.service.BlockOriginService;
import com.zpkdxgames.plexonquests.service.ProgressService;
import java.util.Locale;
import org.bukkit.GameMode;
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
    private final NamespacedKey spawnReasonKey;

    public CoreObjectiveListener(JavaPlugin plugin, ProgressService progress, BlockOriginService origins) {
        this.progress = progress;
        this.origins = origins;
        this.spawnReasonKey = new NamespacedKey(plugin, "spawn_reason");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        BlockOriginService.OriginResult origin = origins.origin(event.getBlock());
        Contribution breakContribution = blockContribution(
                ObjectiveType.BREAK_BLOCK,
                player,
                event.getBlock().getType(),
                origin,
                mature(event));
        progress.contribute(player, breakContribution);
        if (mature(event) && isCrop(event.getBlock().getType())) {
            progress.contribute(player, blockContribution(
                    ObjectiveType.HARVEST_CROP,
                    player,
                    event.getBlock().getType(),
                    origin,
                    true));
        }
        origins.markBroken(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        progress.contribute(player, new Contribution(
                ObjectiveType.PLACE_BLOCK,
                1L,
                event.getBlockPlaced().getType(),
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                true,
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
    public void onSpawn(CreatureSpawnEvent event) {
        event.getEntity().getPersistentDataContainer().set(
                spawnReasonKey, PersistentDataType.STRING, event.getSpawnReason().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        CreatureSpawnEvent.SpawnReason spawnReason = spawnReason(event.getEntity());
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
        long normalizedDamage = Math.max(1L, Math.round(event.getFinalDamage()));
        progress.contribute(player, entityContribution(
                ObjectiveType.DAMAGE_ENTITY,
                player,
                living,
                normalizedDamage,
                event.getCause(),
                spawnReason(living)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || event.getCaught() == null) {
            return;
        }
        Material material = null;
        long amount = 1L;
        if (event.getCaught() instanceof Item item) {
            material = item.getItemStack().getType();
            amount = item.getItemStack().getAmount();
        }
        Player player = event.getPlayer();
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
        progress.contribute(event.getPlayer(), itemContribution(
                ObjectiveType.SMELT_ITEM, event.getPlayer(), event.getItemType(), event.getItemAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        progress.contribute(event.getEnchanter(), itemContribution(
                ObjectiveType.ENCHANT_ITEM, event.getEnchanter(), event.getItem().getType(), 1L));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingExtract(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !(event.getView().getTopInventory() instanceof BrewerInventory)
                || event.getClickedInventory() != event.getView().getTopInventory()
                || event.getRawSlot() < 0
                || event.getRawSlot() > 2
                || !removesItem(event.getAction())) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }
        progress.contribute(player, itemContribution(
                ObjectiveType.BREW_POTION, player, item.getType(), item.getAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        progress.contribute(event.getPlayer(), Contribution.simple(ObjectiveType.VISIT_WORLD, 1L, event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
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

    private static Contribution blockContribution(
            ObjectiveType type,
            Player player,
            Material material,
            BlockOriginService.OriginResult origin,
            boolean mature) {
        return new Contribution(
                type,
                1L,
                material,
                null,
                null,
                null,
                player.getWorld().getName(),
                player.getWorld().getEnvironment(),
                player.getGameMode(),
                origin.known(),
                origin.natural(),
                mature,
                false,
                false,
                true,
                "",
                "",
                "");
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

    private static boolean isCrop(Material material) {
        return material == Material.WHEAT
                || material == Material.CARROTS
                || material == Material.POTATOES
                || material == Material.BEETROOTS
                || material == Material.NETHER_WART;
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
}

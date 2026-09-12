package com.zpkdxgames.plexonquests.presentation;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class ItemFactory {
    public ItemStack create(Material material, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(material == null || material.isAir() ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        decorate(meta, name, lore, glow);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack playerHead(OfflinePlayer owner, Component name, List<Component> lore, boolean glow) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        decorate(meta, name, lore, glow);
        item.setItemMeta(meta);
        return item;
    }

    private static void decorate(ItemMeta meta, Component name, List<Component> lore, boolean glow) {
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        if (glow) meta.setEnchantmentGlintOverride(true);
    }
}

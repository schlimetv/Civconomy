package com.minecraftcivilizations.specialization.CustomItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

public class WoodShears extends CustomItem {

    private static final int MAX_DURABILITY = 60;

    public WoodShears(String id, String displayName) {
        super(id, displayName, Material.SHEARS);
    }

    @Override
    public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
        if (meta instanceof Damageable d) {
            d.setMaxDamage(MAX_DURABILITY);
            d.setDamage(0);
            itemStack.setItemMeta(meta);
        }
    }
}

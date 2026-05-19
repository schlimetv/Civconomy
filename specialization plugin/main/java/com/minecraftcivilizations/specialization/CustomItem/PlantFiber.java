package com.minecraftcivilizations.specialization.CustomItem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class PlantFiber extends CustomItem {

    public PlantFiber(String id, String displayName) {
        super(id, displayName, Material.STRING);
    }

    @Override
    public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
    }
}

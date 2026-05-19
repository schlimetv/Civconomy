package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class LeafShearListener implements Listener {

    private static final double PLANT_FIBER_DROP_CHANCE = 0.05;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShearLeaves(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Tag.LEAVES.isTagged(block.getType())) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType() != Material.SHEARS) return;

        if (Math.random() >= PLANT_FIBER_DROP_CHANCE) return;

        CustomItem fiberItem = CustomItemManager.getInstance().getCustomItem("plant_fiber");
        if (fiberItem == null) return;

        Location drop = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(drop, fiberItem.createItemStack());
    }
}

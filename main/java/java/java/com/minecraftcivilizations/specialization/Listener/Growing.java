package com.minecraftcivilizations.specialization.Listener;

import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

public class Growing implements Listener {

    @EventHandler
    public void onCropGrow(BlockGrowEvent e) {
        if(e.getBlock().getRelative(BlockFace.UP).getLightFromSky() <= 1) e.setCancelled(true);
    }
}

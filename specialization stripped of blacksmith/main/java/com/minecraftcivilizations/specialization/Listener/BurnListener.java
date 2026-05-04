package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementTier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;

public class BurnListener implements Listener {
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        ReinforcementTier tier = ReinforcementManager.getReinforcementTier(event.getBlock());
        // Only LIGHT and HEAVY reinforcements are fireproof; WOODEN is not
        if (tier != null && tier.isFireproof()) {
            event.setCancelled(true);
        }
    }
}

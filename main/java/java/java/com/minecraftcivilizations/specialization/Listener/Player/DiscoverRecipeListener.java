package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Listener.Player.Inventories.CraftingListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

public class DiscoverRecipeListener implements Listener {
    @EventHandler
    public void onDiscoverRecipe(PlayerRecipeDiscoverEvent event){

        if(CraftingListener.shouldBlockRecipe(event.getPlayer(), event.getRecipe())){
            event.setCancelled(true);
        }
    }
}

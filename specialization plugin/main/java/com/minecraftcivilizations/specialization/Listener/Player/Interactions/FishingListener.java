package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

public class FishingListener implements Listener {

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if(event.getCaught() != null && event.getCaught() instanceof Item item) {
            if(item.getItemStack().getType().toString().toLowerCase().matches("(?i)(Enchanted Book|Bow)")){
                if(Math.random() > 0.0005){
                    event.setCancelled(true);
                }
            }
        }
    }

}

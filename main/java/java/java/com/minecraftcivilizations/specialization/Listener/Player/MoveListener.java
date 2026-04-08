package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Distance.Town;
import com.minecraftcivilizations.specialization.Distance.TownManager;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.List;

public class MoveListener implements Listener {
    @EventHandler
    public void onMove(PlayerMoveEvent event) {

        Location from = event.getFrom();

        if (!event.hasChangedBlock()) return;

        List<Town> towns = TownManager.getTowns();

        if(towns.isEmpty()) return;

        double minDistanceSq = Double.MAX_VALUE;
        for (Town town : TownManager.getTowns()) {
            if(!town.getCenterLocation().getWorld().equals(from.getWorld())) continue;
            if (town.getCenterLocation().distanceSquared(from) < minDistanceSq) {
                minDistanceSq = town.getCenterLocation().distanceSquared(from);
            }
        }
    }
}

package com.minecraftcivilizations.specialization.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;
import java.util.UUID;

public class PreJoinEventListener implements Listener {
    @EventHandler
    public void onPreJoin(AsyncPlayerPreLoginEvent event) {
        onPreLogin(event.getPlayerProfile(), event.getUniqueId());
    }
    private void onPreLogin(PlayerProfile playerProfile, UUID uuid){
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);


            Player player = event.getPlayer();

            // Play your join sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);

            // List of all custom recipe keys you want players to always discover
            List<NamespacedKey> alwaysUnlockedRecipes = List.of(
                    new NamespacedKey(Specialization.getInstance(), "hearty_soup"),
                    new NamespacedKey(Specialization.getInstance(), "cat_spawn_egg"),
                    new NamespacedKey(Specialization.getInstance(), "bell")
                    // add more NamespacedKey objects for other recipes
            );

            // Unlock all recipes for the player if not already discovered
            for (NamespacedKey key : alwaysUnlockedRecipes) {
                if (!player.hasDiscoveredRecipe(key)) {
                    player.discoverRecipe(key);
                }
            }
        }

    }

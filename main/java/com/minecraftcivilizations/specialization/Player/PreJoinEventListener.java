package com.minecraftcivilizations.specialization.Player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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
        event.joinMessage(null); // suppress server-wide vanilla join message

        Player player = event.getPlayer();

        // Play join sound
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

        // Send localized join message to nearby players and to the joining player
        double radius = SpecializationConfig.getChatConfig().get("JOIN_QUIT_RADIUS", Double.class);
        String fmt    = SpecializationConfig.getChatConfig().get("JOIN_FORMAT", String.class);
        Component msg = MiniMessage.miniMessage().deserialize(fmt.formatted(player.getName()));
        player.getLocation().getNearbyPlayers(radius).forEach(near -> near.sendMessage(msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null); // suppress server-wide vanilla quit message

        Player player = event.getPlayer();
        double radius = SpecializationConfig.getChatConfig().get("JOIN_QUIT_RADIUS", Double.class);
        String fmt    = SpecializationConfig.getChatConfig().get("QUIT_FORMAT", String.class);
        Component msg = MiniMessage.miniMessage().deserialize(fmt.formatted(player.getName()));
        player.getLocation().getNearbyPlayers(radius).forEach(near -> near.sendMessage(msg));
        // Note: quitting player cannot receive their own quit message
    }

}

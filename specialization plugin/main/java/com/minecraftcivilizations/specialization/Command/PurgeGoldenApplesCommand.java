package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@CommandAlias("purgegoldenapples|purgega")
public class PurgeGoldenApplesCommand extends BaseCommand {

    @Default
    @CommandPermission("specialization.purgegoldenapples")
    public void onPurgeGoldenApples(Player sender) {
        int totalPurged = 0;
        int playersAffected = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            int playerPurged = purgePlayerBlessedGoldenApples(player);
            if (playerPurged > 0) {
                totalPurged += playerPurged;
                playersAffected++;
                player.sendMessage(ChatColor.YELLOW + "Your blessed golden apples have been converted back to normal golden apples (" + playerPurged + " items).");
            }
        }

        if (totalPurged > 0) {
            sender.sendMessage(ChatColor.GREEN + "Successfully purged " + totalPurged + " blessed golden apple(s) from " + playersAffected + " player(s).");
        } else {
            sender.sendMessage(ChatColor.GRAY + "No blessed golden apples found to purge.");
        }
    }

    private int purgePlayerBlessedGoldenApples(Player player) {
        int purgedCount = 0;
        
        // Check main inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isBlessedGoldenApple(item)) {
                ItemStack normalApple = createNormalGoldenApple(item);
                player.getInventory().setItem(i, normalApple);
                purgedCount += item.getAmount();
            }
        }
        
        return purgedCount;
    }

    private boolean isBlessedGoldenApple(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        
        // Check if it's a golden apple or enchanted golden apple
        if (item.getType() != Material.GOLDEN_APPLE && item.getType() != Material.ENCHANTED_GOLDEN_APPLE) {
            return false;
        }
        
        // Check if it has blessed food lore
        List<Component> lore = item.getItemMeta().lore();
        if (lore == null) return false;
        
        for (Component component : lore) {
            if (component instanceof net.kyori.adventure.text.TextComponent) {
                String content = ((net.kyori.adventure.text.TextComponent) component).content();
                if ("Blessed Food".equals(content)) return true;
            }
        }
        return false;
    }

    private ItemStack createNormalGoldenApple(ItemStack blessedApple) {
        // Create a new ItemStack with the same material and amount but no custom meta
        ItemStack normalApple = new ItemStack(blessedApple.getType(), blessedApple.getAmount());
        // Don't copy the meta to remove all blessed properties
        return normalApple;
    }
}

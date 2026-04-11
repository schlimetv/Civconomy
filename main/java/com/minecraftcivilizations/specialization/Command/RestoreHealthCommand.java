package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.Objects;

@CommandAlias("restorehealth|healall")
public class RestoreHealthCommand extends BaseCommand {

    @Default
    @CommandPermission("specialization.restorehealth")
    public void onRestoreHealth(Player sender) {
        int playersHealed = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            double currentMaxHealth = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();

            if (currentMaxHealth < 20.0) {
                Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
                playersHealed++;
                player.sendMessage("§aYour max health has been restored to 20!");
            }
        }

        if (playersHealed > 0) {
            sender.sendMessage("§aRestored max health for " + playersHealed + " player(s) to 20.");
        } else {
            sender.sendMessage("§7No players needed max health restoration.");
        }
    }
}

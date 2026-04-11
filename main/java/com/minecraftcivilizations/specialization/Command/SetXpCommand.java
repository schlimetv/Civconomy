package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@CommandAlias("setxp")
public class SetXpCommand extends BaseCommand {

    @Default
    @CommandPermission("specialization.setxp.self")
    public void onSetXP(@NotNull Player player, @NotNull SkillType type, double amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        
        if (customPlayer == null) {
            player.sendMessage("§cError: Could not find your player data.");
            return;
        }
        
        double oldXp = customPlayer.getSkill(type).getXp();
        double difference = amount - oldXp;
        customPlayer.addSkillXp(type, difference, null, true, false);
        double newXp = customPlayer.getSkill(type).getXp();
        
        player.sendMessage("§aSet your " + type.name() + " XP from §e" + String.format("%.2f", oldXp) + "§a to §e" + String.format("%.2f", newXp) + "§a (+" + String.format("%.2f", amount) + ")");
    }

    @Subcommand("other")
    @CommandPermission("specialization.setxp.other")
    public void onSetXPOther(@NotNull Player sender, @NotNull String targetName, @NotNull SkillType type, double amount) {
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            sender.sendMessage("§cPlayer '" + targetName + "' is not online or does not exist.");
            return;
        }
        
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(target.getUniqueId());
        
        if (customPlayer == null) {
            sender.sendMessage("§cError: Could not find player data for " + target.getName() + ".");
            return;
        }

        double oldXp = customPlayer.getSkill(type).getXp();
        double difference = amount - oldXp;
        customPlayer.addSkillXp(type, difference, null, true, false);
        double newXp = customPlayer.getSkill(type).getXp();
        
        // Send feedback to the command sender
        sender.sendMessage("§aSet " + target.getName() + "'s " + type.name() + " XP from §e" + String.format("%.2f", oldXp) + "§a to §e" + String.format("%.2f", newXp) + "§a (+" + String.format("%.2f", amount) + ")");
        
        // Notify the target player unless command sender
        if(target != sender)
            target.sendMessage("§aYour " + type.name() + " XP has been modified by " + sender.getName() + " from §e" + String.format("%.2f", oldXp) + "§a to §e" + String.format("%.2f", newXp) + "§a (+" + String.format("%.2f", amount) + ")");
    }

    @Subcommand("set")
    @CommandPermission("specialization.setxp.set")
    public void onSetXPAbsolute(@NotNull Player sender, @NotNull String targetName, @NotNull SkillType type, double amount) {
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            sender.sendMessage("§cPlayer '" + targetName + "' is not online or does not exist.");
            return;
        }
        
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(target.getUniqueId());
        
        if (customPlayer == null) {
            sender.sendMessage("§cError: Could not find player data for " + target.getName() + ".");
            return;
        }
        
        double oldXp = customPlayer.getSkill(type).getXp();
        // Calculate the difference needed to reach the target amount
        double difference = amount - oldXp;
        customPlayer.addSkillXp(type, difference, null, true, false);
        
        // Send feedback to the command sender
        sender.sendMessage("§aSet " + target.getName() + "'s " + type.name() + " XP from §e" + String.format("%.2f", oldXp) + "§a to §e" + String.format("%.2f", amount));
        // Notify the target player unless command sender
        if(target != sender)
            target.sendMessage("§aYour " + type.name() + " XP has been set by " + sender.getName() + " from §e" + String.format("%.2f", oldXp) + "§a to §e" + String.format("%.2f", amount));
    }

    @Subcommand("give")
    @CommandPermission("specialization.setxp.give")
    public void onGiveXP(@NotNull Player sender, @NotNull String targetName, @NotNull SkillType type, double amount) {
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            sender.sendMessage("§cPlayer '" + targetName + "' is not online or does not exist.");
            return;
        }

        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(target.getUniqueId());

        if (customPlayer == null) {
            sender.sendMessage("§cError: Could not find player data for " + target.getName() + ".");
            return;
        }

        double oldXp = customPlayer.getSkill(type).getXp();
        customPlayer.addSkillXp(type, amount, null, true, false);
        double newXp = customPlayer.getSkill(type).getXp();

        sender.sendMessage("§aGave " + target.getName() + " §e" + String.format("%.2f", amount) + "§a " + type.name() + " XP (§e" + String.format("%.2f", oldXp) + "§a → §e" + String.format("%.2f", newXp) + "§a)");

        if (target != sender)
            target.sendMessage("§aYou received §e" + String.format("%.2f", amount) + "§a " + type.name() + " XP from " + sender.getName() + " (§e" + String.format("%.2f", oldXp) + "§a → §e" + String.format("%.2f", newXp) + "§a)");
    }
}

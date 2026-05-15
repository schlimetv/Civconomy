package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import co.aikar.commands.bukkit.contexts.OnlinePlayer;
import com.minecraftcivilizations.specialization.GUI.ClassGUI;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.entity.Player;

@CommandAlias("class")
@CommandPermission("specialization.class")
public class ClassCommand extends BaseCommand {

    @Default
    @CommandCompletion("@players")
    public void onClass(Player commandSender, @Optional OnlinePlayer target) {
        // If target is null, targetPlayer becomes sender.
        Player targetPlayer = (target == null) ? commandSender : target.getPlayer();

        // Call our new overloaded open method
        new ClassGUI().open(commandSender, targetPlayer);
    }

}
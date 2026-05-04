package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.GUI.RecipesGUI;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.entity.Player;

@CommandAlias("recipes")
public class RecipesCommand extends BaseCommand {

    @Default
    public void onSendCommand(Player sender) {
        new RecipesGUI(CoreUtil.getPlayer(sender), null).open(sender);
    }

}

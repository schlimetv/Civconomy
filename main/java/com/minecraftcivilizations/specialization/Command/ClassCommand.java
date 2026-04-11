package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.GUI.ClassGUI;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import co.aikar.commands.annotation.CommandPermission;

@CommandAlias("class")
@CommandPermission("specialization.class")
public class ClassCommand extends BaseCommand {

    @Default
    public void onClass(@NotNull Player player) {
        new ClassGUI().open(player);
    }

}
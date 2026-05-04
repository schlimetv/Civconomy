package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

@CommandAlias("notifyrestart")
@CommandPermission("civlabs.notifyrestart")
public class NotifyRestartCommand extends BaseCommand {

    @Default
    @CommandPermission("civlabs.notifyrestart")
    public void notifyRestart(@NotNull Integer min) {
        Bukkit.getOnlinePlayers().forEach(player -> {
            PlayerUtil.message(player,"<red>This server will be shutting down in the next <white> " + min + "<red> min(s)!");
        });
    }

}

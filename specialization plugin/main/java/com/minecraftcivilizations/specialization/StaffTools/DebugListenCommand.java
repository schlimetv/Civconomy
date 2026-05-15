package com.minecraftcivilizations.specialization.StaffTools;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.PaperCommandManager;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.ChatColor.*;

/**
 * Homebrewed Debug System. Use it.
 * See Debug
 * @author alectriciti
 */
@CommandAlias("debug")
@CommandPermission("debug")
public class DebugListenCommand extends BaseCommand implements Listener {


    public DebugListenCommand(PaperCommandManager commandManager) {
        commandManager.registerCommand(this);
        registerCompletions(commandManager);
    }

    @Subcommand("on")
    @CommandCompletion("@debug_channels_register")
    public void onAdd(Player player, @Optional String debug_channel) {
        if (debug_channel == null || debug_channel.isBlank()) {
            Debug.getInstance().registerPlayerToAllChannels(player);
//            player.sendMessage(ChatColor.RED + "Usage: /debug on <debug_channel>");
            player.sendMessage(Debug.TITLE + GRAY + " Listening to all primary debug channels");
        }else {
            Debug.getInstance().registerPlayerChannel(player, debug_channel);
            player.sendMessage(Debug.TITLE + GRAY + "Listening to debug channel: " + YELLOW + debug_channel);
        }
        if(!Debug.DEBUG_ENABLED) {
            player.sendMessage(Debug.TITLE + GRAY + " Note: Debug is "+DARK_GRAY+" DISABLED");
        }
    }

    @Subcommand("off")
    @CommandCompletion("@debug_channels_unregister")
    public void onRemove(Player player, @Optional String debug_channel) {
        if (debug_channel == null || debug_channel.isBlank()) {
            Debug.getInstance().unregisterPlayerToAllChannels(player);
            player.sendMessage(Debug.TITLE + GRAY + " Removed from all debug channels");
            return;
        }
        Debug.getInstance().unregisterPlayerChannel(player, debug_channel);
        player.sendMessage(Debug.TITLE + GRAY + "Removed from debug channel: " + YELLOW + debug_channel);
    }

    @Subcommand("enable")
    public void enableDebug(CommandSender sender, @Optional String debug_channel) {
        Debug.DEBUG_ENABLED = true;
        Specialization.getInstance().getLogger().info("Debug was enabled by "+sender.getName());
        sender.sendMessage(Debug.TITLE + GRAY + "Debug Enabled (This might produce lag, disable when finished)");
    }
    @Subcommand("disable")
    public void disableDebug(CommandSender sender, @Optional String debug_channel) {
        Debug.DEBUG_ENABLED = false;
        Specialization.getInstance().getLogger().info("Debug was disabled by "+sender.getName());
        sender.sendMessage(Debug.TITLE + GRAY + "Debug Disabled: Messages will no longer be broadcast");
    }


    @Subcommand("reset")
    public void onReset(CommandSender sender, @Optional String debug_channel) {
        if(sender.isOp()) {
            Debug.resetAllValues(sender);
            sender.sendMessage(Debug.TITLE + GRAY + "Reset all Debug Channel mappings");
        }
    }

    @Subcommand("help")
    @CommandCompletion("@debug_help")
    public void onHelp(CommandSender sender, @Optional String debug_channel) {
        Component help_on = MiniMessage.miniMessage().deserialize(
                "<white>/debug on [channel]</white>\n"+
                        "<gray>"+"Adds you to a debug channel. When a debug message fires off, it will be displayed for you." +
                        "By not specifying a [channel], you register yourself to all standard debug channels"+"</gray>"
        );
        Component help_off = MiniMessage.miniMessage().deserialize(
                "<white>/debug off [channel]</white>\n"+
                        "<gray>"+"Removes you from a debug channel you're registered to." +
                        "By not specifying a [channel], you unregister yourself from all channels"+"</gray>"
        );
        Component help_reset = MiniMessage.miniMessage().deserialize(
                "<white>/debug reset</white>\n"+
                        "<gray>"+"Resets all global debug settings"+"</gray>"
        );
        sender.sendMessage(Debug.TITLE + WHITE + "basically just /debug on to listen to all debug channels - ask alec how to use until this cmd is finished");
    }

    public static void registerCompletions(PaperCommandManager manager) {
        if (manager == null) return;
        manager.getCommandCompletions().registerCompletion("debug_channels_register", c -> {
            try {
                return Debug.getChannelList();
            } catch (Exception e) {
                return new ArrayList<String>();
            }
        });
        manager.getCommandCompletions().registerCompletion("debug_channels_unregister", c -> {
            try {
                return Debug.getPlayerChannels(c.getPlayer());
            } catch (Exception e) {
                return new ArrayList<String>();
            }
        });
        manager.getCommandCompletions().registerCompletion("debug_help", c -> {
            try {
                return List.of("on", "off", "reset");
            } catch (Exception e) {
                return new ArrayList<String>();
            }
        });
    }
}
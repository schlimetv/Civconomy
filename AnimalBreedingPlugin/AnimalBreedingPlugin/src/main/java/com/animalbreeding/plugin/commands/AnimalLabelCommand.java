package com.animalbreeding.plugin.commands;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import com.animalbreeding.plugin.integration.FarmerLevelLookup;
import com.animalbreeding.plugin.managers.LabelPreferenceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class AnimalLabelCommand implements CommandExecutor, TabCompleter {

    private static final int REQUIRED_FARMER_LEVEL = 2;

    private final AnimalBreedingPlugin plugin;

    public AnimalLabelCommand(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (FarmerLevelLookup.getFarmerLevel(player) < REQUIRED_FARMER_LEVEL) {
            player.sendMessage("§cYou need Farmer level " + REQUIRED_FARMER_LEVEL + " to use this command.");
            return true;
        }

        LabelPreferenceManager prefs = plugin.getLabelPreferenceManager();

        if (args.length == 0) {
            player.sendMessage("§eAnimal labels: "
                + (prefs.isEnabled(player) ? "§aON" : "§cOFF")
                + " §7— use §f/animallabel on|off§7 to change.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                prefs.setEnabled(player, true);
                player.sendMessage("§aAnimal labels turned ON.");
            }
            case "off" -> {
                prefs.setEnabled(player, false);
                player.sendMessage("§cAnimal labels turned OFF.");
            }
            default -> player.sendMessage("§cUsage: /animallabel <on|off>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase();
        return Stream.of("on", "off").filter(s -> s.startsWith(prefix)).toList();
    }
}

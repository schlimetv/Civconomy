package com.animalbreeding.plugin.commands;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /abmutation <percentage>
 *
 * Operators-only command to adjust the genetic mutation chance.
 * e.g. "/abmutation 5" sets a 5 % chance of a random trait mutation per offspring.
 * The value is persisted in config.yml under breeding.mutation-chance.
 */
public class MutationCommand implements CommandExecutor, TabCompleter {

    private final AnimalBreedingPlugin plugin;

    public MutationCommand(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("animalbreeding.admin")) {
            sender.sendMessage("§cYou need operator privileges to use this command.");
            return true;
        }

        if (args.length != 1) {
            double current = plugin.getGeneticsManager().getMutationChance() * 100;
            sender.sendMessage("§6Current mutation chance: §e" + String.format("%.1f", current) + "%");
            sender.sendMessage("§7Usage: §f/abmutation <0-100>");
            return true;
        }

        try {
            double percent = Double.parseDouble(args[0]);
            if (percent < 0 || percent > 100) {
                sender.sendMessage("§cValue must be between 0 and 100.");
                return true;
            }
            plugin.getGeneticsManager().setMutationChance(percent / 100.0);
            sender.sendMessage("§aMutation chance set to §e" + String.format("%.1f", percent) + "%§a.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: §f" + args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("0", "1", "2", "5", "10");
        return List.of();
    }
}

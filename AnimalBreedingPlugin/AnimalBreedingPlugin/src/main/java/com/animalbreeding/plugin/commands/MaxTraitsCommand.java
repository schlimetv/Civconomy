package com.animalbreeding.plugin.commands;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import com.animalbreeding.plugin.managers.GeneticsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /abmaxtraits <n>
 *
 * Operators-only command to adjust the maximum non-behavior traits per animal.
 * Behaviors (DOCILE / SKITTISH / AGGRESSIVE) are separate and never count.
 * The value is persisted to config.yml under breeding.max-traits.
 */
public class MaxTraitsCommand implements CommandExecutor, TabCompleter {

    private final AnimalBreedingPlugin plugin;

    public MaxTraitsCommand(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("animalbreeding.admin")) {
            sender.sendMessage("§cYou need operator privileges to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§6Current max traits per animal: §e"
                + plugin.getGeneticsManager().getMaxTraits());
            sender.sendMessage("§7Usage: §f/abmaxtraits <0-" + GeneticsManager.HARD_MAX_TRAITS + ">");
            return true;
        }

        try {
            int n = Integer.parseInt(args[0]);
            if (n < 0 || n > GeneticsManager.HARD_MAX_TRAITS) {
                sender.sendMessage("§cValue must be between 0 and " + GeneticsManager.HARD_MAX_TRAITS + ".");
                return true;
            }
            plugin.getGeneticsManager().setMaxTraits(n);
            sender.sendMessage("§aMax traits per animal set to §e" + n + "§a.");
            sender.sendMessage("§7Existing animals keep their current traits; this affects future inheritance and spawns.");
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number: §f" + args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("0", "1", "2", "3", "4", "5");
        return List.of();
    }
}

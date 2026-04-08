package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.Recipe.Blueprints;
import com.minecraftcivilizations.specialization.Recipe.Recipes;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;


@CommandAlias("recipe-specialization")
public class RecipeRefreshCommand extends BaseCommand {



    private int refreshTaskId = -1; // Track the scheduled task

    @Subcommand("refresh")
    @Description("Re-initialize custom recipes immediately")
    @CommandPermission("specialization.recipe.refresh")
    public void onRefresh(CommandSender sender) {
        Recipes.init();
        Blueprints.init();
        Specialization.getInstance().getLogger().info("Custom recipes/blueprints have been refreshed.");

        sender.sendMessage("§aCustom recipes have been re-initialized.");
    }

    @Subcommand("autorefresh")
    @Description("Toggle automatic recipe refresh on/off, default 5 minutes if no value provided")
    @CommandPermission("specialization.recipe.autorefresh")
    @Syntax("[minutes]")
    public void onAutoRefresh(CommandSender sender, @Optional Integer minutes) {
        if (refreshTaskId != -1) {
            // Stop existing task
            Bukkit.getScheduler().cancelTask(refreshTaskId);
            refreshTaskId = -1;
            sender.sendMessage("§cAutomatic recipe refresh has been stopped.");
        } else {
            // Default to 5 minutes if no value provided
            int interval = (minutes != null && minutes > 0) ? minutes : 5;

            refreshTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    JavaPlugin.getPlugin(Specialization.class),
                    () -> {
                        Recipes.init();
                        Blueprints.init();
                        Specialization.getInstance().getLogger().info("Custom recipes/blueprints have been refreshed.");
                    },
                    0L, // initial delay
                    20L * 60L * interval // convert minutes to ticks
            );

            sender.sendMessage("§aAutomatic recipe refresh started, interval: " + interval + " minutes.");
        }
    }

    @Subcommand("unregister")
    @Description("Unregister all custom recipes")
    @CommandPermission("specialization.recipe.unregister")
    public void onUnregister(CommandSender sender) {
        int removed = 0;
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe r = iter.next();
            if (r instanceof Keyed keyed) {
                NamespacedKey key = keyed.getKey();
                if ("specialization".equals(key.getNamespace())) { // all your custom recipes
                    iter.remove();
                    removed++;
                }
            }
        }

        sender.sendMessage("§aUnregistered " + removed + " custom recipes.");
        Bukkit.getLogger().info("Unregistered " + removed + " custom recipes for testing.");
    }
}
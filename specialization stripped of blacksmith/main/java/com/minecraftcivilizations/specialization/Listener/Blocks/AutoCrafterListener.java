package com.minecraftcivilizations.specialization.Listener.Blocks;

import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class AutoCrafterListener implements Listener {

    // --- AUTO CRAFTER RECIPE FILTERING ---
    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        Recipe recipe = event.getRecipe();

        // Block non-vanilla recipes
        Keyed keyed = (Keyed) recipe;
        NamespacedKey key = keyed.getKey();
        if (!key.getNamespace().equalsIgnoreCase("minecraft")) {
            cancelCraft(event, "Auto Crafter blocked a custom recipe.");
            return;
        }

        // Block plates/plate stacks from being used in vanilla recipes
        if (event.getBlock().getState() instanceof org.bukkit.block.Container container) {
            for (ItemStack item : container.getInventory().getContents()) {
                if (item == null || item.getType().isAir()) continue;
                com.minecraftcivilizations.specialization.CustomItem.CustomItem custom =
                        CustomItemManager.getInstance().getCustomItem(item);
                if (custom != null) {
                    String id = custom.getId();
                    if (id.startsWith("armor_plate") || id.endsWith("_armor_plateset")) {
                        cancelCraft(event, "Auto Crafter cannot craft with plates.");
                        return;
                    }
                }
            }
        }

        // Block diamond/netherite recipes
        if (containsRestrictedIngredient(recipe)) {
            cancelCraft(event, "Auto Crafter cannot craft with diamond or netherite ingredients.");
        }
    }

    private void cancelCraft(CrafterCraftEvent event, String msg) {
        event.setCancelled(true);
        Location loc = event.getBlock().getLocation();

        double radius = 8.0;
        for (Player player : loc.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radius * radius) {
                PlayerUtil.message(player,"§c" + msg);
            }
        }
    }

    private boolean containsRestrictedIngredient(Recipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            Map<Character, RecipeChoice> map = shaped.getChoiceMap();
            for (RecipeChoice choice : map.values()) {
                if (isRestricted(choice)) return true;
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<RecipeChoice> list = shapeless.getChoiceList();
            for (RecipeChoice choice : list) {
                if (isRestricted(choice)) return true;
            }
        }

        // fallback: check result material
        ItemStack result = recipe.getResult();
        String name = result.getType().name();
        return name.contains("DIAMOND") || name.contains("NETHERITE");
    }

    private boolean isRestricted(RecipeChoice choice) {
        if (choice == null) return false;

        if (choice instanceof RecipeChoice.ExactChoice exact) {
            List<ItemStack> items = exact.getChoices();
            for (ItemStack item : items) {
                if (item == null) continue;
                String name = item.getType().name();
                if (name.contains("DIAMOND") || name.contains("NETHERITE")) return true;
            }
        } else if (choice instanceof RecipeChoice.MaterialChoice matChoice) {
            Collection<Material> mats = matChoice.getChoices();
            for (Material mat : mats) {
                if (mat == null) continue;
                String name = mat.name();
                if (name.contains("DIAMOND") || name.contains("NETHERITE")) return true;
            }
        }

        return false;
    }

//    // --- BLOCK HOPPERS FROM INTERACTING WITH AUTO CRAFTERS ---


//    @EventHandler
//    public void onHopperMoveItem(InventoryMoveItemEvent event) {
//        InventoryHolder source = event.getSource().getHolder();
//        InventoryHolder destination = event.getDestination().getHolder();
//
//        // Block hoppers inserting or extracting from Crafters
//        if (isCrafter(source) || isCrafter(destination)) {
//            event.setCancelled(true);
//        }
//    }
//
//    private boolean isCrafter(InventoryHolder holder) {
//        if (holder instanceof TileState tile) {
//            Block block = tile.getBlock();
//            return block.getType() == Material.CRAFTER && block.getBlockData() instanceof Crafter;
//        }
//        return false;
//    }
}

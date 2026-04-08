package com.minecraftcivilizations.specialization.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ItemStackUtils {

    /**
     * Returns true if the item has a non-empty lore line at the given index.
     */
    public static boolean hasLoreLine(@NotNull ItemStack item_stack, int line) {
        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;

        List<String> lore = meta.getLore();
        if (lore == null) return false;
        return line < lore.size() && lore.get(line) != null && !lore.get(line).isEmpty();
    }

    /**
     * Sets/overwrites a single lore line on the item.
     * Preserves other existing lines. Expands the lore list with empty strings if necessary.
     */
    public static void setLoreLine(@NotNull ItemStack item_stack, int line, String loreText) {
        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return;

        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Ensure list is large enough
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, loreText == null ? "" : loreText);
        meta.setLore(lore_list);
        item_stack.setItemMeta(meta);
    }

    /**
     * Sets/overwrites a single lore line on the item.
     * Preserves other existing lines. Expands the lore list with empty strings if necessary.
     */
    public static void setLoreLine(ItemMeta meta, int line, String loreText) {
        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Ensure list is large enough
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, loreText == null ? "" : loreText);
        meta.setLore(lore_list);
    }

    /**
     * Returns true if the item has the given namespaced key flag in its ItemMeta PDC.
     */
    public static boolean hasLoreTag(ItemStack item_stack, NamespacedKey key) {
        if (item_stack == null || key == null) return false;
        if (!item_stack.hasItemMeta()) return false;

        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * Sets/overwrites a single lore line on the item and marks it with the given NamespacedKey in the PDC.
     * If the flag already exists this method does nothing
     */
    public static void setLoreTag(ItemStack item_stack, NamespacedKey key, int line, String lore) {
        if (item_stack == null || key == null || line < 0) return;

        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return;

        // If already tagged, don't reapply
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Expand to required size (fill with empty strings)
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, lore == null ? "" : lore);

        meta.setLore(lore_list);
        // mark with a byte flag (value 1)
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        item_stack.setItemMeta(meta);
    }

    // Returns hunger points (food "nutrition") restored by one unit of the given Material.
// Values sourced from the Minecraft Wiki "Food" table (Java Edition values).
    public static int getFoodNutrition(Material food_type) {
        if (food_type == null) return 0;
        switch (food_type) {
            // crops / basic
            case APPLE: return 4;
            case CARROT: return 3;
            case POTATO: return 1;                 // raw potato
            case BAKED_POTATO: return 5;
            case POISONOUS_POTATO: return 2;
            case BEETROOT: return 1;
            case BEETROOT_SOUP: return 6;
            case WHEAT: return 0;                 // wheat is an ingredient, not consumed for hunger
            case MELON_SLICE: return 2;
            case PUMPKIN_PIE: return 8;
            case BREAD: return 5;
            case CAKE: return 2;                  // one slice = 2 hunger points (placed cake is eaten slice-by-slice)
            case COOKIE: return 2;

            // berries / plants
            case SWEET_BERRIES: return 2;
            case GLOW_BERRIES: return 2;
            case HONEY_BOTTLE: return 6;

            // meat (raw / cooked)
            case BEEF: return 3;
            case COOKED_BEEF: return 8;           // steak
            case CHICKEN: return 2;
            case COOKED_CHICKEN: return 6;
            case PORKCHOP: return 3;
            case COOKED_PORKCHOP: return 8;
            case RABBIT: return 3;
            case COOKED_RABBIT: return 5;
            case MUTTON: return 2;
            case COOKED_MUTTON: return 6;

            // fish
            case COD: return 2;                   // raw cod (Material.COD)
            case COOKED_COD: return 5;
            case SALMON: return 2;                // raw salmon
            case COOKED_SALMON: return 6;
            case TROPICAL_FISH: return 1;
            case PUFFERFISH: return 1;

            case ROTTEN_FLESH: return 1;

            // other stackable foods / miscellaneous
            case CHORUS_FRUIT: return 4;
            case SPIDER_EYE: return 2;
            case DRIED_KELP: return 1;
            case SUSPICIOUS_STEW: return 6;       // restores 6 hunger + status effect
            case MUSHROOM_STEW: return 6;
            case RABBIT_STEW: return 10;

            // golden items
            case GOLDEN_APPLE: return 4;
            case ENCHANTED_GOLDEN_APPLE: return 4;
            case GOLDEN_CARROT: return 6;

            // other consumables
            case MILK_BUCKET: return 0;           // clears effects; does not restore hunger
            case HONEYCOMB: return 0;             // not edible
            // (include bucket variants if you want)
            // fall-through default
            default:
                return 0;
        }
    }

}

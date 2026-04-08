package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.ItemStack;

public class FurnaceListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Material extracted = event.getItemType();
        int amount = event.getItemAmount();

        if (event.getBlock().getType() == Material.FURNACE) {
            furnaceSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.FURNACE_MINECART) {
            furnaceSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.SMOKER) {
            smokerSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.BLAST_FURNACE) {
            blastSmelt(player, new ItemStack(extracted, amount), amount);
        }

    }

    private void furnaceSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromSmeltingConfig().get(item.getType(), new TypeToken<>() {});
        if (pair == null || pair.firstValue() == null || pair.secondValue() == null) return;
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }

    private void smokerSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromSmokingConfig().get(item.getType(), new TypeToken<>() {});
        if (pair == null || pair.firstValue() == null || pair.secondValue() == null) return;
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }

    private void blastSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromBlastingConfig().get(item.getType().name(), new TypeToken<>() {});
        if (pair == null || pair.firstValue() == null || pair.secondValue() == null) return;
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }

    // ─────────────────────────────────────────────────────────────
    //  Bronze / custom item smelting override
    // ─────────────────────────────────────────────────────────────

    /**
     * Intercepts furnace/blast furnace smelting to replace the result
     * when the source item is a custom item that shares a vanilla base.
     *
     * Bronze items use GOLD_INGOT or GOLDEN_* bases, so vanilla smelting
     * would produce gold nuggets. This catches:
     *   - bronze_* items (bronze_ingot, bronze_helm, bronze_sword_head, etc.)
     *   - armor_plate_bronze (starts with armor_plate_, not bronze_)
     * and replaces the nugget result with a bronze ingot.
     *
     * All other custom items on smeltable bases are blocked entirely
     * to prevent exploit conversions.
     */
    @EventHandler(ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        CustomItem custom = CustomItemManager.getInstance().getCustomItem(source);
        if (custom == null) return;

        String customId = custom.getId();

        // Skip items handled by IronBloomSystem (runs at HIGH priority before this)
        if (customId.equals("iron_bloom") || customId.equals("wrought_iron_ingot")
                || customId.equals("armor_plate_iron") || customId.equals("crushed_iron_ore")
                || customId.startsWith("iron_") && customId.endsWith("_raw")) {
            return;
        }

        // Bronze items → bronze ingot
        if (customId.startsWith("bronze_") || customId.equals("armor_plate_bronze")) {
            ItemStack bronzeIngot = CustomItemManager.getInstance().getCustomItem("bronze_ingot").createItemStack();
            event.setResult(bronzeIngot);
            return;
        }

        // Block all other custom items from vanilla smelting (prevent gold/iron nugget exploits)
        Material base = source.getType();
        if (base == Material.GOLD_INGOT || base.name().startsWith("GOLDEN_")
                || base.name().startsWith("IRON_")) {
            event.setCancelled(true);
        }
    }
}

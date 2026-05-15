package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager.*;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementTier;

public class BreakBlockListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        // ─── Miner level gate: cancel the break entirely if insufficient level ───
        minerListener(event);
        if (event.isCancelled()) return;

        /**
         * This resets the block break progress done by mobs
         */
        Block block = event.getBlock();
        Collection<Player> nearbyPlayers = block.getLocation().getNearbyPlayers(16);
        nearbyPlayers.stream()
                .filter(player -> player.getGameMode().equals(GameMode.SURVIVAL))
                .collect(Collectors.toSet());
        if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
            nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), 0));
        }

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType().name().endsWith("_PICKAXE")) { // ensure it's a pickaxe
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return; // Exit early: do not give miner XP
            }
        }

        AttributeInstance breakSpeedAttr = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);

        if (breakSpeedAttr != null) {
            breakSpeedAttr.setBaseValue(SpecializationConfig.getBlockHardnessConfig().get(event.getBlock().getType(), Double.class));
            Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromBreakingConfig().get(event.getBlock().getType(), new TypeToken<>() {
            });
            CustomPlayer player = CoreUtil.getPlayer(event.getPlayer().getUniqueId());
            BlockData blockData = event.getBlock().getBlockData();

            if (isReinforced(event.getBlock())) {
                handleReinforcedDrop(event.getBlock(), event.getPlayer());
            }

            if (pair != null && pair.firstValue() != null && pair.secondValue() != null) {
                if (blockData instanceof Ageable age) {
                    if (age.getMaximumAge() == age.getAge()) {
                        player.addSkillXp(pair.firstValue(), pair.secondValue(), event.getBlock().getLocation());
                    }
                } else {
                    player.addSkillXp(pair.firstValue(), pair.secondValue(), event.getBlock().getLocation());
                }
            }
        }
        farmerListener(event);
    }

    private void handleReinforcedDrop(Block block, org.bukkit.entity.Player player) {
        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

        ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
        if (tier == null) return;

        // 50% chance to give the reward item back
        switch (tier) {
            case HEAVY -> {
                if (Math.random() < 0.5) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.IRON_INGOT));
                }
                PlayerUtil.message(player, "Iron Reinforcement Broke");
            }
            case LIGHT -> {
                if (Math.random() < 0.5) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.COPPER_INGOT));
                }
                PlayerUtil.message(player, "Copper Reinforcement Broke");
            }
            case WOODEN -> {
                if (Math.random() < 0.5) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.OAK_PLANKS));
                }
                PlayerUtil.message(player, "Wooden Reinforcement Broke");
            }
        }

        // Always remove reinforcement
        removeReinforcement(block);
    }

    public void minerListener(BlockBreakEvent event) {
        CustomPlayer player = CoreUtil.getPlayer(event.getPlayer());
        Material materialName = event.getBlock().getType();
        SkillLevel skillRequired = SpecializationConfig.getCanMinerLvlBreakConfig().get(materialName.toString(), new TypeToken<>() {
        });
        if (skillRequired != null && player.getSkillLevel(SkillType.MINER) < skillRequired.getLevel()) {
            event.setCancelled(true);
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL)
                PlayerUtil.message(event.getPlayer(),"You are unable to mine this ore.");
        }
    }

    public void farmerListener(BlockBreakEvent event) {
        CustomPlayer player = CoreUtil.getPlayer(event.getPlayer());
        Material materialName = event.getBlock().getType();
        SkillLevel skillRequired = SpecializationConfig.getCanFarmerBreakConfig().get(materialName.toString(), new TypeToken<>() {
        });

        if (skillRequired != null && player.getSkillLevel(SkillType.FARMER) < skillRequired.getLevel()) {
            event.setDropItems(false);
            PlayerUtil.message(event.getPlayer(), org.bukkit.ChatColor.RED + "You are unable to farm this");
        }

        List<Material> otherFarmables = List.of(Material.COCOA_BEANS, Material.SUGAR_CANE, Material.CACTUS, Material.MELON, Material.PUMPKIN);
        double chance = SpecializationConfig.getFarmerConfig().get("FARMER_GET_DROPS_CHANCE_" + player.getSkillLevelEnum(SkillType.FARMER), Double.class);
        double random = Math.random();

        if (random < chance) {
            event.setDropItems(true);
        } else if (event.getBlock().getBlockData() instanceof Ageable || otherFarmables.contains(materialName)) {
            event.setDropItems(false);
        }
    }

    /**
     * 5% chance to drop Plant Fiber when any leaves block is broken with shears.
     * Runs as a separate handler at MONITOR priority so it is unaffected by
     * exceptions in the main onBlockBreak handler (e.g. config NPEs for leaves).
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onShearLeaves(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!Tag.LEAVES.isTagged(block.getType())) return;

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType() != Material.SHEARS) return;

        if (Math.random() < 0.05) {
            com.minecraftcivilizations.specialization.CustomItem.CustomItem fiberItem =
                    CustomItemManager.getInstance().getCustomItem("plant_fiber");
            if (fiberItem != null) {
                Location drop = block.getLocation().add(0.5, 0.5, 0.5);
                block.getWorld().dropItemNaturally(drop, fiberItem.createItemStack());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Pickaxe tier restrictions
    // ─────────────────────────────────────────────────────────────

    private static final Set<Material> COPPER_ORES = Set.of(
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE
    );
    private static final Set<Material> IRON_ORES = Set.of(
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE
    );

    /**
     * Enforces custom pickaxe tier restrictions:
     *   - Wooden pickaxe: cannot mine copper ore
     *   - Stone pickaxe (vanilla AND copper_pickaxe): cannot mine iron ore
     *
     * Bronze pickaxe handles diamond blocking via its own Tool component rules.
     * Checks base material type, so copper_pickaxe (STONE_PICKAXE base) is
     * caught by the iron ore check automatically.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onPickaxeTierCheck(BlockBreakEvent event) {
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        Material toolType = tool.getType();
        Material blockType = event.getBlock().getType();

        // Wooden pickaxe (vanilla): cannot mine copper ore
        if (toolType == Material.WOODEN_PICKAXE && COPPER_ORES.contains(blockType)) {
            event.setDropItems(false);
        }

        // Stone pickaxe (vanilla + copper_pickaxe): cannot mine iron ore
        if (toolType == Material.STONE_PICKAXE && IRON_ORES.contains(blockType)) {
            event.setDropItems(false);
        }
    }

    private List<Block> getMultiBlocks(Block b) {
        List<Block> l = new ArrayList<>();
        l.add(b);
        BlockData d = b.getBlockData();
        switch (d) {
            case Door door -> l.add(b.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
            case Bed bed ->
                    l.add(b.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing()));
            case Bisected bi -> l.add(b.getRelative(bi.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
            default -> {
            }
        }
        return l;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        List<Block> block_list_copy = new ArrayList<>(blocks.size());
        block_list_copy.addAll(blocks);
        block_list_copy.forEach(block -> {
            if (!isReinforced(block)) return;

            ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
            if (tier == null) return;

            // WOODEN reinforcement offers no blast resistance – block is destroyed normally
            if (!tier.hasBlastResistance()) {
                for (Block b : getMultiBlocks(block)) removeReinforcement(b);
                return;
            }

            Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

            double factor;
            Material dropMaterial;
            if (tier == ReinforcementTier.HEAVY) {
                factor = SpecializationConfig.getReinforcementConfig().get("HEAVY_EXPLOSION_RESISTANCE", Double.class);
                dropMaterial = Material.IRON_INGOT;
            } else {
                factor = SpecializationConfig.getReinforcementConfig().get("LIGHT_EXPLOSION_RESISTANCE", Double.class);
                dropMaterial = Material.COPPER_INGOT;
            }

            if (Math.random() < factor) {
                blocks.remove(block); // protect the block from the explosion
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(dropMaterial));
                for (Block b : getMultiBlocks(block)) removeReinforcement(b);
            }
        });
    }
}

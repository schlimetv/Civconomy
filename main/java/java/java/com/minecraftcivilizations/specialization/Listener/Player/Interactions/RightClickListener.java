package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Listener.Player.LocalChat;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementTier;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RightClickListener implements Listener {
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        if(event.getClickedBlock() == null) return;
        if(event.getClickedBlock().getType().equals(Material.SPAWNER)){
            event.setCancelled(true);
        }

        Player player = event.getPlayer();
        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        // Sweet berry bush damage only applies to non-reinforced bushes
        if(!ReinforcementManager.isReinforced(event.getClickedBlock())) {
            if (event.getClickedBlock().getType().equals(Material.SWEET_BERRY_BUSH) && cPlayer.getSkillLevel(SkillType.FARMER) < 2) {
                if (Math.random() < 0.2) {
                    player.damage(1);
                }
            }
        }

        Material heldItem = player.getInventory().getItemInMainHand().getType();

        // ---- WOODEN REINFORCEMENT: stick + planks in inventory ----
        if (heldItem == Material.STICK) {
            // Find any plank type in the player's inventory
            int plankSlot = findPlankSlot(player);
            if (plankSlot != -1) {
                List<Block> blocks = getMultiBlocks(event.getClickedBlock());
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, ReinforcementTier.WOODEN)) {
                        success = true;
                    }
                }
                if (success) {
                    player.swingHand(EquipmentSlot.HAND);
                    // Consume one plank
                    ItemStack plankStack = player.getInventory().getItem(plankSlot);
                    if (plankStack != null) {
                        plankStack.setAmount(plankStack.getAmount() - 1);
                    }
                    PlayerUtil.message(player, Component.text("§6Wooden Reinforced").color(NamedTextColor.GOLD)
                            .decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }
        // ---- LIGHT REINFORCEMENT: copper ingot, Builder level 1+ ----
        else if (heldItem == Material.COPPER_INGOT) {
            CustomPlayer customPlayer = CoreUtil.getPlayer(player.getUniqueId());
            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().get("LIGHT_REINFORCEMENT_LEVEL", Integer.class)) {
                List<Block> blocks = getMultiBlocks(event.getClickedBlock());
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, ReinforcementTier.LIGHT)) {
                        success = true;
                    }
                }
                if (success) {
                    player.swingHand(EquipmentSlot.HAND);
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    PlayerUtil.message(player, Component.text("§7Lightly Reinforced").color(NamedTextColor.WHITE).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }
        // ---- HEAVY REINFORCEMENT: iron ingot, Builder level 2+ ----
        else if (heldItem == Material.IRON_INGOT) {
            CustomPlayer customPlayer = CoreUtil.getPlayer(player.getUniqueId());
            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().get("HEAVY_REINFORCEMENT_LEVEL", Integer.class)) {
                List<Block> blocks = getMultiBlocks(event.getClickedBlock());
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, ReinforcementTier.HEAVY)) {
                        success = true;
                    }
                }
                if (success) {
                    player.swingHand(EquipmentSlot.HAND);
                    player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    PlayerUtil.message(player, Component.text("§7Heavily Reinforced").color(NamedTextColor.WHITE).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }

    }

    /**
     * Finds the first inventory slot containing any plank type.
     * Returns -1 if no planks are found.
     */
    private int findPlankSlot(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && Tag.PLANKS.isTagged(item.getType())) {
                return i;
            }
        }
        return -1;
    }

    private List<Block> getMultiBlocks(Block block) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(block);
        if (block.getBlockData() instanceof Door door) {
            blocks.add(block.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
        } else if (block.getBlockData() instanceof Bed bed) {
            blocks.add(block.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing()));
        } else if (block.getBlockData() instanceof Bisected bisected) {
            blocks.add(block.getRelative(bisected.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
        }
        return blocks;
    }
}

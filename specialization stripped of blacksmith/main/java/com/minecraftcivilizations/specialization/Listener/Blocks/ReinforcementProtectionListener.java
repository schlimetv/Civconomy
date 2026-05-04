package com.minecraftcivilizations.specialization.Listener.Blocks;

import com.minecraftcivilizations.specialization.Reinforcement.Reinforcement;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementTier;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ReinforcementProtectionListener implements Listener {

    /**
     * Stores tier + placedAtTick for falling blocks so reinforcement can be re-applied
     * when the block lands.
     */
    private static class StoredReinforcement {
        final ReinforcementTier tier;
        final long placedAtTick;
        StoredReinforcement(ReinforcementTier tier, long placedAtTick) {
            this.tier = tier;
            this.placedAtTick = placedAtTick;
        }
    }

    private final Map<Location, StoredReinforcement> temporaryReinforcementStorage = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        Location fallingBlockLocation = fallingBlock.getLocation();
        if (temporaryReinforcementStorage.containsKey(fallingBlockLocation)) {
            StoredReinforcement stored = temporaryReinforcementStorage.get(fallingBlockLocation);
            boolean success = ReinforcementManager.addReinforcementSilent(block, stored.tier, stored.placedAtTick);
            if (success) {
                temporaryReinforcementStorage.remove(fallingBlockLocation);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockStartFalling(org.bukkit.event.block.BlockPhysicsEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Block block = event.getBlock();
        if (isFallingBlockType(block.getType()) && ReinforcementManager.isReinforced(block)) {
            ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
            // We need to get the placed tick before removing the reinforcement
            Reinforcement r = ReinforcementManager.getReinforcement(block);
            long placedAtTick = r != null ? r.getPlacedAtTick() : block.getWorld().getFullTime();
            Bukkit.getScheduler().runTaskLater(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
                block.getWorld().getEntitiesByClass(FallingBlock.class).stream()
                    .filter(fb -> fb.getLocation().distance(block.getLocation()) < 2.0)
                    .forEach(fb -> {
                        temporaryReinforcementStorage.put(fb.getLocation(),
                                new StoredReinforcement(tier, placedAtTick));
                    });
            }, 1L);
            ReinforcementManager.removeReinforcement(block);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) {
            return;
        }

        List<Block> blocks = event.getBlocks();
        Vector direction = event.getDirection().getDirection();
        Map<Block, StoredReinforcement> reinforcementData = new HashMap<>();
        
        for (Block block : blocks) {
            if (ReinforcementManager.isReinforced(block)) {
                ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
                Reinforcement r = ReinforcementManager.getReinforcement(block);
                long placedAtTick = r != null ? r.getPlacedAtTick() : block.getWorld().getFullTime();
                reinforcementData.put(block, new StoredReinforcement(tier, placedAtTick));
                ReinforcementManager.removeReinforcement(block);
            }
        }
        if (!reinforcementData.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
                for (Map.Entry<Block, StoredReinforcement> entry : reinforcementData.entrySet()) {
                    Block originalBlock = entry.getKey();
                    StoredReinforcement stored = entry.getValue();
                    Location newLocation = originalBlock.getLocation().add(direction);
                    Block newBlock = newLocation.getBlock();
                    if (!ReinforcementManager.addReinforcementSilent(newBlock, stored.tier, stored.placedAtTick)) {
                        // Failed to add reinforcement to new location, restore to original
                        ReinforcementManager.addReinforcementSilent(originalBlock, stored.tier, stored.placedAtTick);
                    }
                }
            }, 2L);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) {
            return;
        }

        List<Block> blocks = event.getBlocks();
        Vector direction = event.getDirection().getDirection();
        Map<Block, StoredReinforcement> reinforcementData = new HashMap<>();
        
        for (Block block : blocks) {
            if (ReinforcementManager.isReinforced(block)) {
                ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
                Reinforcement r = ReinforcementManager.getReinforcement(block);
                long placedAtTick = r != null ? r.getPlacedAtTick() : block.getWorld().getFullTime();
                reinforcementData.put(block, new StoredReinforcement(tier, placedAtTick));
                ReinforcementManager.removeReinforcement(block);
            }
        }
        if (!reinforcementData.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
                for (Map.Entry<Block, StoredReinforcement> entry : reinforcementData.entrySet()) {
                    Block originalBlock = entry.getKey();
                    StoredReinforcement stored = entry.getValue();
                    Location newLocation = originalBlock.getLocation().add(direction);
                    Block newBlock = newLocation.getBlock();
                    if (!ReinforcementManager.addReinforcementSilent(newBlock, stored.tier, stored.placedAtTick)) {
                        // Failed to add reinforcement to new location, restore to original
                        ReinforcementManager.addReinforcementSilent(originalBlock, stored.tier, stored.placedAtTick);
                    }
                }
            }, 2L);
        }
    }


    /**
     * Pickaxe is required only for LIGHT and HEAVY reinforcements (and iron/brick blocks).
     * WOODEN reinforcement does NOT require a pickaxe.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockDamageEvent event) {
        Block block = event.getBlock();

        ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
        boolean reinforcedRequiresPickaxe = tier != null && tier.requiresPickaxe();
        boolean ironType = isIronBlock(block.getType());
        boolean isBrick = isBrickBlock(block.getType());

        if (!reinforcedRequiresPickaxe && !ironType && !isBrick) {
            return;
        }

        Player player = event.getPlayer();
        Material item = player.getInventory().getItemInMainHand().getType();

        if (!isPickaxe(item)) {
            PlayerUtil.message(player, "Pickaxe is <gold>required</gold> to break reinforced blocks", 1);
            event.setCancelled(true);
        }
    }

    private boolean isPickaxe(Material mat) {
        return mat.name().endsWith("_PICKAXE");
    }

    private boolean isIronBlock(Material mat) {
        return mat.name().contains("IRON");
    }

    private boolean isBrickBlock(Material mat) {
        return mat.name().contains("BRICKS");
    }


    private boolean isFallingBlockType(Material material) {
        return material == Material.SAND || 
               material == Material.RED_SAND || 
               material == Material.GRAVEL || 
               material == Material.ANVIL || 
               material == Material.CHIPPED_ANVIL || 
               material == Material.DAMAGED_ANVIL ||
               material == Material.DRAGON_EGG ||
               material == Material.POINTED_DRIPSTONE ||
               material.name().contains("CONCRETE_POWDER");
    }
}

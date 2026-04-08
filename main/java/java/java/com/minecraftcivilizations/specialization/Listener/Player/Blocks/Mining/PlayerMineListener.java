package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementTier;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.UUID;

public class PlayerMineListener implements Listener {

    private static HashMap<UUID, Double> originalBreakSpeed = new HashMap<>();


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEarly(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null && event.getClickedBlock().getType().isBlock() && !event.getClickedBlock().getType().isAir()) {
            AttributeInstance breakSpeedAttr = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
            if (breakSpeedAttr != null && !originalBreakSpeed.containsKey(event.getPlayer().getUniqueId())){
                originalBreakSpeed.put(event.getPlayer().getUniqueId(), breakSpeedAttr.getBaseValue());
            }
            double multiplier = 1;
            try {
                multiplier = getBlockBreakMultiplier(event.getClickedBlock());
            } catch (Exception e) {
                Bukkit.getLogger().info("AAAAAAAAAA: " + event.getClickedBlock());
            }
            

            if (multiplier == 0) {
                event.setCancelled(true);
                return;
            }

            // Apply attribute
            if (breakSpeedAttr != null) {
                breakSpeedAttr.setBaseValue(multiplier);
            }
        }
    }

    @EventHandler
    private void startBreakingBlock(BlockDamageEvent event) {
        if(!event.getBlock().getType().isBlock() || event.getBlock().getType().isAir()) return;
        double multiplier = getBlockBreakMultiplier(event.getBlock());

        if (multiplier == 0) {
            event.setCancelled(true);
            return;
        }


        AttributeInstance breakSpeedAttr = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (breakSpeedAttr != null) {
            if (!originalBreakSpeed.containsKey(event.getPlayer().getUniqueId())) {
                originalBreakSpeed.put(event.getPlayer().getUniqueId(), breakSpeedAttr.getBaseValue());
            }
            breakSpeedAttr.setBaseValue(multiplier);
        }
    }

    @EventHandler
    public void onBlockBreakAbort(BlockDamageAbortEvent event) {
        resetPlayerBreakSpeed(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction().isRightClick()) {
            resetPlayerBreakSpeed(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        originalBreakSpeed.remove(event.getPlayer().getUniqueId());
    }

    private double getBlockBreakMultiplier(org.bukkit.block.Block block) {
        double multiplier = SpecializationConfig.getBlockHardnessConfig().get(block.getType(), Double.class);

        ReinforcementTier tier = ReinforcementManager.getReinforcementTier(block);
        if (tier != null && tier.slowsBreaking()) {
            if (tier == ReinforcementTier.LIGHT) {
                multiplier = SpecializationConfig.getReinforcementConfig().get("LIGHT_REINFORCEMENT_MULTIPLIER", Double.class);
            } else if (tier == ReinforcementTier.HEAVY) {
                multiplier = SpecializationConfig.getReinforcementConfig().get("HEAVY_REINFORCEMENT_MULTIPLIER", Double.class);
            }
        }
        // WOODEN reinforcement does NOT slow breaking – uses normal block hardness

        return multiplier;
    }

    private void resetPlayerBreakSpeed(UUID playerId) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
        if (player != null && originalBreakSpeed.containsKey(playerId)) {
            AttributeInstance breakSpeedAttr = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
            if (breakSpeedAttr != null) {
                breakSpeedAttr.setBaseValue(1.0); // Reset to normal speed
            }
            originalBreakSpeed.remove(playerId);
        }
    }
}

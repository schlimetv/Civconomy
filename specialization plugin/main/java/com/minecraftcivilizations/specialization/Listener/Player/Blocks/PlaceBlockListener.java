package com.minecraftcivilizations.specialization.Listener.Player.Blocks;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceBlockListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (applyBuilderRestrictions(event)) return;

        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromPlacingConfig().get(event.getBlockPlaced().getType(), new TypeToken<>() {});
        if (pair != null) {
            if(!event.getBlock().getType().isBlock()) return;
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(event.getPlayer().getUniqueId());
            customPlayer.addSkillXp(pair.firstValue(), pair.secondValue());
        }
    }

    /**
     * Enforces builder-level placement limits:
     *   - No placement in ocean biomes.
     *   - Cannot place more than N blocks above the worldgen surface, where N
     *     scales with the player's BUILDER skill level (configurable).
     * Creative/spectator and players with the bypass permission are exempt.
     *
     * @return true if the event was cancelled (caller should stop processing).
     */
    private boolean applyBuilderRestrictions(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return false;
        if (player.hasPermission("specialization.builder.bypass")) return false;

        Block placed = event.getBlockPlaced();
        World world = placed.getWorld();

        // Ocean biome restriction
        if (SpecializationConfig.getBuilderConfig().get("BUILDER_OCEAN_PLACEMENT_DISALLOWED", Boolean.class)) {
            Biome biome = world.getBiome(placed.getLocation());
            NamespacedKey biomeKey = biome.getKey();
            if (biomeKey != null && biomeKey.getKey().endsWith("ocean")) {
                event.setCancelled(true);
                PlayerUtil.message(player, Component.text("You cannot place blocks in ocean biomes.", NamedTextColor.RED));
                return true;
            }
        }

        // Height cap above worldgen terrain surface
        CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
        int level = cp == null ? 0 : cp.getSkillLevel(SkillType.BUILDER);
        if (level < 0) level = 0;
        if (level > 5) level = 5;
        Integer cap = SpecializationConfig.getBuilderConfig().get("BUILDER_HEIGHT_CAP_LEVEL_" + level, Integer.class);
        if (cap == null) return false;

        int surfaceY = world.getHighestBlockYAt(placed.getX(), placed.getZ(), HeightMap.WORLD_SURFACE_WG);
        int heightAbove = placed.getY() - surfaceY;
        if (heightAbove > cap) {
            event.setCancelled(true);
            PlayerUtil.message(player, Component.text(
                    "You cannot build more than " + cap + " blocks above the terrain surface at your builder level.",
                    NamedTextColor.RED));
            return true;
        }
        return false;
    }
}

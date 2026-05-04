package com.minecraftcivilizations.specialization.Listener.Player.Blocks;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceBlockListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromPlacingConfig().get(event.getBlockPlaced().getType(), new TypeToken<>() {});
        if (pair != null) {
            if(!event.getBlock().getType().isBlock()) return;
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(event.getPlayer().getUniqueId());
            customPlayer.addSkillXp(pair.firstValue(), pair.secondValue());
        }
    }

}

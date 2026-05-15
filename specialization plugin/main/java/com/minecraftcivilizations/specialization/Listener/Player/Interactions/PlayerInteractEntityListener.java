package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Listener.Player.PlayerDownedListener;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerInteractEntityListener implements Listener {


    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBreed(EntityBreedEvent e){
        if(e.getBreeder() instanceof Player player) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            Integer level = SpecializationConfig.getFarmerConfig().get("FARMER_BREED_LEVEL_" + e.getMother().getType(), Integer.class);
            if(level != null && cPlayer.getSkillLevel(SkillType.FARMER) < level){
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerTame(EntityTameEvent e){
        if(e.getOwner() instanceof Player player) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            Pair<SkillType,Integer> level = SpecializationConfig.getTameableConfig().get("TAME_" + e.getEntity().getType(), new TypeToken<>(){});
            if(level != null && cPlayer.getSkillLevel(level.firstValue()) < level.secondValue()){
                e.setCancelled(true);
            }
        }
    }

}
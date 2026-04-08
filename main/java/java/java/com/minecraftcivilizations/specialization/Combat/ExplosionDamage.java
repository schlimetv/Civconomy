package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.SmartEntity.SmartEntity;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;

public class ExplosionDamage implements Listener {


    private final CombatManager combatManager;

    public ExplosionDamage(CombatManager combatManager) {
        this.combatManager = combatManager;
        this.combatManager.plugin.getServer().getPluginManager().registerEvents(this, combatManager.plugin);
    }

    private final Map<Location, Material> lastExplosions = new HashMap<Location, Material>();

    @EventHandler
    public void onExplosion(EntityDamageEvent event){
//        if(event.getCause()== EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
//            if(event.getDamageSource().getDamageType()== DamageType.BAD_RESPAWN_POINT){
////                event.setDamage(BASE, event.get);
//            }
//            String modifiers = "";
//
//            for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
////            if(event.getDamage(m)!=0)
//                if(event.isApplicable(m))
//                    modifiers += "\n<gray>"+m.name()+"</gray>: "+Debug.formatDecimal(event.getDamage(m));
//            }
//            Debug.broadcast("damage", "<gold>Explosion at "+   ": "+event.getDamage()+"</gold>", modifiers);
//            Debug.broadcast("explosion", "Block Explosion Damage cause: "+event.getDamageSource().getDamageType().toString());
//
//        }

    }

    @EventHandler
    public void onBlockBreak(BlockPlaceEvent event){
        Material mat = event.getBlockPlaced().getType();
        if(mat == Material.RESPAWN_ANCHOR){
            event.getPlayer().setCooldown(Material.GLOWSTONE, 60);
            event.getPlayer().getWorld().playSound(event.getPlayer().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1, 1);
        }else if(mat.name().contains("_BED")){
            Player player = event.getPlayer();
            if(player.getWorld().getEnvironment() != World.Environment.NORMAL){
                PlayerUtil u = PlayerUtil.getPlayerUtil(player);
                u.setCooldown("bed_place", 30);
                player.setCooldown(mat, 30);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        if(event.getAction()== Action.RIGHT_CLICK_BLOCK){
            Material type = event.getClickedBlock().getType();
            if(type.equals(Material.RESPAWN_ANCHOR)){
                if(event.getPlayer().getCooldown(Material.GLOWSTONE)>0){
                    event.setCancelled(true);
                }
                Player player = event.getPlayer();
                PlayerUtil u = PlayerUtil.getPlayerUtil(player);
                if(u.isOnCooldown("respawn_anchor")) {
                    event.setCancelled(true);
                }else{
                    u.setCooldown("respawn_anchor", 8);
                }
            }else if(type.name().contains("_BED")){

                Player player = event.getPlayer();
                PlayerUtil u = PlayerUtil.getPlayerUtil(player);
                if(u.isOnCooldown("bed_place")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onExplosion(BlockExplodeEvent event){
//        lastExplosions.put(event.getBlock().getLocation(), event.getBlock().getType());
//        BlockState explodedBlockState = event.getExplodedBlockState();
//        if(explodedBlockState != null) {
//            Debug.broadcast("explosion", "<light_purple>Explosion at "+Debug.formatLocation(explodedBlockState.getLocation())+"</light_purple>");
//
//            Material type = explodedBlockState.getType();
//            if (type == Material.RESPAWN_ANCHOR) {
//                event.setCancelled(true);
//            }
//        }

    }

    @EventHandler
    public void onExplosion(EntityDamageByBlockEvent event){

        //first stop


        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            Location hit = event.getEntity().getLocation();
            // find nearest recorded explosion
            Location nearest = null;
            double min = Double.MAX_VALUE;
            for (Location loc : lastExplosions.keySet()) {
                double dist = loc.distanceSquared(hit);
                if (dist < min) {
                    min = dist;
                    nearest = loc;
                }
            }
            if (nearest != null && min < 16) { // within 4 blocks
                Material cause = lastExplosions.get(nearest);
                if (cause == Material.RESPAWN_ANCHOR || cause.name().contains("_BED")) {
                    // Custom damage logic for respawn anchors or beds
                    event.setDamage(1);
                }
            }
        }

        BlockState damagerBlockState = event.getDamagerBlockState();
        if(damagerBlockState != null) {
            Material type = damagerBlockState.getType();
            if (type == Material.RESPAWN_ANCHOR) {

            } else if (type.name().contains("_BED")) {
                event.setDamage(BASE, event.getDamage(BASE) * 0.33);
            }
        }
            String modifiers = "";

            for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
//            if(event.getDamage(m)!=0)
                if(event.isApplicable(m))
                modifiers += "\n<gray>"+m.name()+"</gray>: "+Debug.formatDecimal(event.getDamage(m));
            }
//            Debug.broadcast("damage", "<gold>Explosion at "+   ": "+event.getDamage()+"</gold>", modifiers);



    }



    @EventHandler
    public void onExplosionPrime(ExplosionPrimeEvent event){
        if(event.getEntityType() == EntityType.END_CRYSTAL) {
//            event.getEntity().getPersistentDataContainer().set(new NamespacedKey(Specialization.getInstance(), "endcrystal"), PersistentDataType.LONG, time);
//            event.setCancelled(true);
//            SmartEntity sme = new SmartEntity(event.getEntity(), event.getEntity().getLocation());
        }
    }




}

package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.bukkit.ChatColor.LIGHT_PURPLE;
import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;

/**
 * Manages armor being broken with various tools
 * @author Alectriciti ⚡
 */
public class ArmorBreakSystem {

    CombatManager combatManager;

    public ArmorBreakSystem(CombatManager combatManager){
        this.combatManager = combatManager;
    }


    /**
     * IF the attacker's weapon is an [ArmorBreaker], apply that effect here
     * return using the debug msg
     */
    String breakArmorWithItem(LivingEntity victim, EntityDamageByEntityEvent event) {
        // Armor Bonus for EXPERT and above
        if(!(event.getDamager() instanceof HumanEntity attacker)) return "";


        if(attacker.getAttackCooldown()<0.25){
            return "not ready";
        }
        ItemStack itemInMainHand = attacker.getEquipment().getItemInMainHand();
        if(victim instanceof Player) {
            if (itemInMainHand.getType().name().contains("_PICKAXE")) {
                event.setDamage(BASE, event.getDamage(BASE) * 0.75); //nerf pickaxes to account for armor
            }
        }

        int armor_rolls = 3;//1+ ThreadLocalRandom.current().nextInt(3);
        ArmorBreakStats break_stats = getItemArmorBreakStats(itemInMainHand.getType());
        double armor_damage = (double) break_stats.armor_break;
        CustomPlayer customPlayer = CoreUtil.getPlayer(attacker);
        if(customPlayer!=null){
            armor_damage = armor_damage + break_stats.getSkillBonus(customPlayer);
            if(attacker.getAttackCooldown()<1){
                armor_damage *= 0.5; //non full charges should break less armor
            }
        }
        if(armor_rolls == 0) return "";

        double total_extra_penetration = 0;
        EntityEquipment equipment = victim.getEquipment();
        if(equipment == null) return "[bad equipment]";
        World w = victim.getWorld();
        if(victim instanceof Player pls) {
            if(event.isApplicable(BLOCKING)) {
                if (event.getDamage(BLOCKING) < -0.1) {
                    boolean shield_break = false;
                    if (equipment.getItemInOffHand().getType() == Material.SHIELD) {
                        equipment.getItemInOffHand().damage(break_stats.shield_break, attacker);
                        shield_break = true;
                    }else if (equipment.getItemInMainHand().getType() == Material.SHIELD) {
                        equipment.getItemInMainHand().damage(break_stats.shield_break, attacker);
                        shield_break = true;
                    }
                    if (shield_break) {
                        w.playSound(victim.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.PLAYERS, 0.895f, 1.1f + ThreadLocalRandom.current().nextFloat(0.2f));
                        w.spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), (int) break_stats.shield_break, 0.25, 0.25, 0.25, 0, Material.PISTON_HEAD.createBlockData(), true);
                        return "[🛡" + break_stats.shield_break + "]";
                    }
                }
            }
        }
        if (event.getDamage(ARMOR) < -0.1) {


            Set<EquipmentSlot> set = new HashSet<EquipmentSlot>();
            EnumMap<EquipmentSlot, Material> map = new EnumMap<>(EquipmentSlot.class);
            for (int i = 0; i < armor_rolls; i++) {
                EquipmentSlot piece = pickRandomArmorSlot();
                set.add(piece);
//                Debug.broadcast("armor", "<gray>Rolled Armor to break: <white>"+piece.name());
            }
            for (EquipmentSlot slot : set) {
                ItemStack item = equipment.getItem(slot);
                Material m = ArmorStats.getMaterialBlockType(item.getType());
                if (m != Material.AIR) {
                    map.put(slot, m);
                    if (item.getType().getMaxDurability() > 0) {
                        // Paper automatically breaks items at 0 durability and plays effects
                        int unbreaking = item.getEnchantmentLevel(Enchantment.UNBREAKING);
                        double scale = 1.0;
                        switch (unbreaking){
                            case 1:
                                scale = 0.9;
                                break;
                            case 2:
                                scale = 0.8;
                                break;
                            case 3:
                                scale = 0.7;
                                break;
                        }
                        if(m.name().contains("DIAMOND") || m.name().contains("NETHERITE")){
                            scale *= 2.0;
                        }
                        int current_damage_amount = (int) (armor_damage * scale);
                        item.damage(current_damage_amount, attacker);
                        total_extra_penetration += current_damage_amount;
//                        if(attacker instanceof Player px) {
//                            Debug.message(px, "armor", "<white>" + slot.name() + "</white> broke by <light_purple>" + current_damage_amount);
//                        }
                        armor_damage *= 0.5; //for the next armor piece
                    }
                }
            }
            if (total_extra_penetration > 0) {
//                    new_armor = Math.min(0, new_armor + total_extra_penetration);
//                    double mine_hit = 1;
                total_extra_penetration *= attacker.getAttackCooldown();
                //Negate Armor Break from Base Damage

                Sound sound = null;
                for (Map.Entry<EquipmentSlot, Material> slot : map.entrySet()) {
                    Material mat = slot.getValue();
                    if(sound==null){
                        if(mat==Material.SOUL_SOIL){
                            sound = Sound.BLOCK_NYLIUM_FALL;
                        }else if(mat==Material.NETHERITE_BLOCK){
                            sound = Sound.BLOCK_NETHER_BRICKS_BREAK;
                        }else if(mat==Material.CHAIN){
                            sound = Sound.BLOCK_CHAIN_BREAK;
                        }else{
                            if(ThreadLocalRandom.current().nextBoolean()) {
                                sound = Sound.BLOCK_COPPER_GRATE_HIT;
                            }else{
                                sound = Sound.BLOCK_COPPER_GRATE_HIT;
                            }
                        }
                    }
                    double y = ArmorStats.getArmorHeight(slot.getKey());
                    w.spawnParticle(Particle.BLOCK, victim.getLocation().add(0, y, 0), (int)total_extra_penetration, 0.125, 0.125, 0.125, 0, mat.createBlockData(), true);
                }
                if(sound!=null) {
                    w.playSound(victim.getLocation(), sound, SoundCategory.PLAYERS, 0.95f, 1.2f + ThreadLocalRandom.current().nextFloat(0.2f));
                }
//                    Particle.DustOptions dust =new Particle.DustOptions(Color color, 10);
//                    w.spawnParticle(Particle.ANGRY_VILLAGER, monster.getEyeLocation(), 4, 0.5,0.6,0.5,0);

            }
        }
//        if(event.isApplicable(BLOCKING)){
//            double blocking_damage = event.getDamage(BLOCKING);
        if(total_extra_penetration > 0) {
            return "[<gray>⛏</gray>" + LIGHT_PURPLE + Debug.formatDecimal(total_extra_penetration) + "]";
        }else{
            return ""; // "bad";
        }
    }


    private EquipmentSlot pickRandomArmorSlot() {
        switch(ThreadLocalRandom.current().nextInt(4)){
            case 0: return EquipmentSlot.HEAD;
            case 1: return EquipmentSlot.CHEST;
            case 2: return EquipmentSlot.LEGS;
            case 3: return EquipmentSlot.FEET;
            default: return null;
        }
    }

    private ArmorBreakStats getItemArmorBreakStats(Material type) {
//        if(type.equals(Material.NETHERITE_PICKAXE)){
//            return 10;
//        }
//        return 0;

        switch(type) {
            case STONE_SWORD: return new ArmorBreakStats(1, 1);
            case GOLDEN_SWORD: return new ArmorBreakStats(1, 1);
            case IRON_SWORD: return new ArmorBreakStats(3, 2);
            case DIAMOND_SWORD: case NETHERITE_SWORD: return new ArmorBreakStats(4, 2);

            case STONE_AXE: return new ArmorBreakStats(1, 2);
            case GOLDEN_AXE: return new ArmorBreakStats(1, 2);
            case IRON_AXE: return new ArmorBreakStats(3, 8);
            case DIAMOND_AXE: case NETHERITE_AXE: return new ArmorBreakStats(4, 12);

            case STONE_SHOVEL:
            case GOLDEN_SHOVEL:
            case IRON_SHOVEL:
            case DIAMOND_SHOVEL:
            case NETHERITE_SHOVEL: return new ArmorBreakStats(1, 1).setSkillBonus(SkillType.BUILDER, 1, 3);

            case STONE_HOE:
            case GOLDEN_HOE:
            case IRON_HOE: return new ArmorBreakStats(1, 1).setSkillBonus(SkillType.FARMER, 1, 3);
            case DIAMOND_HOE: return new ArmorBreakStats(1, 1).setSkillBonus(SkillType.FARMER, 1, 4);
            case NETHERITE_HOE: return new ArmorBreakStats(1, 1).setSkillBonus(SkillType.FARMER, 2, 4);

            case STONE_PICKAXE: return new ArmorBreakStats(2, 2).setSkillBonus(SkillType.MINER, 1, 2);
            case GOLDEN_PICKAXE: return new ArmorBreakStats(3, 3).setSkillBonus(SkillType.MINER, 1, 3);
            case IRON_PICKAXE: return new ArmorBreakStats(4, 4).setSkillBonus(SkillType.MINER, 1, 6);
            case DIAMOND_PICKAXE: return new ArmorBreakStats(5,5).setSkillBonus(SkillType.MINER, 2, 8);
            case NETHERITE_PICKAXE: return new ArmorBreakStats(6, 6).setSkillBonus(SkillType.MINER, 2, 8);
        }
        return new ArmorBreakStats(0,0);
    }



}

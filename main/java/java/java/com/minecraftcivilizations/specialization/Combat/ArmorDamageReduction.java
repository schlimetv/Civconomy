package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.Config.ConfigFile;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;

/**
 * A custom implementation of armor damage reduction, loosely based on Vanilla
 * Reworks Armor Toughness to be subtractive as well as scalar
 * @author Alectriciti ⚡
 */
public class ArmorDamageReduction {

    CombatManager combatManager;
    boolean enabled;

    public ArmorDamageReduction(CombatManager combatManager) {
        this.combatManager = combatManager;

        enabled = SpecializationConfig.getArmorDamageReductionConfig().get("ENABLED", Boolean.class);
    }

    /**
     * Reduce Damage taken by Mobs
     * TARGET: 16 hits with iron, 32 hits with diamond
     */
    public void applyArmorReduction(LivingEntity victim, EntityDamageByEntityEvent event) {
//        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        double original_total_damage = CombatManager.calculateTotalDamage(event);
        double original_armor = event.getDamage(ARMOR); //only for debug comparison

        /**
         * Formula Variables
         */
        EntityEquipment equipment = victim.getEquipment();
        ArmorStats stats = ArmorStats.getArmorStats(equipment);
        double base_damage = event.getDamage(BASE);

        double armor = stats.getArmor();
        double toughness = stats.getToughness();

        /**
         * Damage Reduction Formula
         */
        double armor_ceiling = 24;
        double armor_redux_factor = 2;
        double toughness_redux_factor = 8;
        double TOTAL_REDUCTION;


        double lvl;
        if (event.getEntity() instanceof Player player_victim) {
            CustomPlayer customPlayer = CustomPlayer.getCustomPlayer(player_victim);
            lvl = customPlayer.getSkillLevel(SkillType.GUARDSMAN);
        } else {
            lvl = 0;
        }

        double original = event.getDamage(BASE);
        // tunable

        boolean pvp = (event.getDamager() instanceof Player);


        double ARMOR_SCALE = 0.08; //(pvp?0.075:0.08);// : 0.08); //0.075;
        double TOUGHNESS_SCALE = (pvp ? 0.12 : 0.125) + (pvp?0:(lvl * 0.005)); //+ (lvl*0.01); //0.2 - PVP gets no lvl benefit
        double TOUGHNESS_CUTOFF = 0.15 + (pvp?0:(lvl * 0.01)); //0.25 -  PVP gets no lvl benefit
        double TOUGHNESS_HIGH_HIT_SCALE = (pvp?0:0.125+(lvl * 0.01)); //integrates vanilla's high hit negation - PVP gets no lvl benefit //(pvp?0:0.125) +

        double cutoff_final = (toughness * TOUGHNESS_CUTOFF);

        event.setDamage(BASE, Math.max(0.25, event.getDamage(BASE) - cutoff_final));
        double new_base = event.getDamage(BASE);

        //Vanilla implementation
        double high_hit_negation = Math.max(0, new_base - (new_base * (2.0 / (2.0 + toughness / 4.0))));

//            original = - toughness * TOUGHNESS_SCALE;
//            double ARMOR_SCALE = 0.285;
//            double TOUGHNESS_SCALE = 0.0;

        // how much more "survivable" the player becomes
        double hitMultiplier = 1.0
                + (armor * ARMOR_SCALE)
                + (toughness * TOUGHNESS_SCALE);

        // PURE DYNAMIC DAMAGE REDUCTION:
        // no fixed target, no forced HP assumptions
        double reductionPercent = 1.0 - (1.0 / hitMultiplier);

        if (reductionPercent < 0) reductionPercent = 0;
        if (reductionPercent > 1) reductionPercent = 1;

        double MAGIC_REDUCTION = 0;
        String magic_msg = "";
        if(event.isApplicable(MAGIC)) {
            //we apply magic (protection enchantment) to armor reduction
            double original_magic =  - event.getDamage(MAGIC); //inverted
            MAGIC_REDUCTION = event.getDamage(MAGIC)*0.25;
            event.setDamage(MAGIC, MAGIC_REDUCTION);
            magic_msg = "<light_purple>Magic: "+Debug.formatDecimal(MAGIC_REDUCTION);
        }

        TOTAL_REDUCTION = (new_base * reductionPercent); // (TOUGHNESS_SCALE * toughness)double HIGH_HIT_SCALE = 0.1;
        TOTAL_REDUCTION += high_hit_negation * TOUGHNESS_HIGH_HIT_SCALE;

        if (event.getEntity() instanceof Player player_victim) {
            Debug.message(player_victim, "armor",
                    "<dark_red>👾 In: <red>" + Debug.formatDecimal(original) + "</red> " +
                            "<aqua>Cut: " + Debug.formatDecimal(cutoff_final) + "</aqua> " +
                            ""+magic_msg+
                            "<dark_red>To: <red>" + Debug.formatDecimal(new_base) +
                            "<green> Hihit: " + Debug.formatDecimal(high_hit_negation) + "</green>" +
                            " <yellow>Scale: " + Debug.formatDecimal(reductionPercent * 100) + "%</yellow> "
            );
        }
//            if(toughness!=0) {
//                Debug.message(player_victim, "armor",
//                        "<light_purple>🛡 Toughness: -" + Debug.formatDecimal((TOUGHNESS_SCALE * toughness)));
//            }
        event.setDamage(ARMOR, -TOTAL_REDUCTION);
//            return;
//        }

//
//    }else if(!(event.getDamager() instanceof Player)){
////            Debug.broadcast("armor", "this message should be impossible");
//            return; //mob is attacking mob
//        }
//
//        // ARMOR (SCALING) REDUCTION FOR PVP
//        double ARMOR_REDUCTION = (armor / armor_ceiling) / armor_redux_factor;
//        // TOUGHNESS (LINEAR) REDUCTION
//        double TOUGHNESS_REDUCTION = Math.max (0, (toughness / toughness_redux_factor)); // Absolute damage reduction
//
//        TOTAL_REDUCTION = Math.min(base_damage, (base_damage * ARMOR_REDUCTION) + TOUGHNESS_REDUCTION);
//
//
//
//        //inverse finally
//        event.setDamage(ARMOR, -TOTAL_REDUCTION);
//
//
//        //Blocking
//        //scaled armor reduction effectiveness according to guardsman level
//
//        if(Debug.isAnyoneListening("armor", true)) {
//            Player p=null;
//            if(event.getDamager() instanceof Player px) {
//            p = px;
//            }else if(event.getEntity() instanceof Player pz){
//                p = pz;
//            }
//            if(p!=null) {
//                String modifiers = "";
//
//                for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
//                    if (event.getDamage(m) != 0)
//                        modifiers += "\n<gray>" + m.name() + "</gray>: " + Debug.formatDecimal(event.getDamage(m));
//                }
//
//                Debug.message(p,
//                        "armor",
//                        //WHITE+victim.getName()+" "+*
//                        "<dark_red>Armor: </dark_red><red>" + Debug.formatDecimal(original_total_damage) +
//                            (WHITE+" ["+BLUE+"🅱:"+Debug.formatDecimal(original_armor)+"]")+
//                                " <blue>[👕: " + Debug.formatDecimal(ARMOR_REDUCTION) + "x]</blue>" +
//                                ((stats.getToughness() > 0) ? (" <gray>[🪨: -" + Debug.formatDecimal(TOUGHNESS_REDUCTION) + "]</gray>") : "") +
//                                (" <green>[🚫: " + Debug.formatDecimal(TOTAL_REDUCTION) + "]</green>") +
//                            (event.isCritical()? GREEN+" (CRIT!)":"")+
//                                " [❤ " + Debug.formatDecimal(CombatManager.calculateTotalDamage(event)) + "]</red>"
//                        ,
//                        "<gray>Original Armor Reduction: <dark_blue>" + Debug.formatDecimal(-original_armor) + "</dark_blue>\n"
//                                + "<gray>New Armor Reduction: <blue>" + Debug.formatDecimal(TOTAL_REDUCTION) + "</blue>\n"
//                                + "Vanilla Damage would have been <dark_red>" + Debug.formatDecimal(original_total_damage) + "</dark_red>\n"
//                                + "Specialization Custom Damage is <red>" + Debug.formatDecimal(CombatManager.calculateTotalDamage(event)) + "</red>\n"
//                                + "<blue>ARMOR REDUCTION:</blue> " + Debug.formatDecimal(ARMOR_REDUCTION) + "\n"
//                                + "<light_purple>TOUGHNESS REDUCTION:</light_purple> " + Debug.formatDecimal(TOUGHNESS_REDUCTION) + "\n"
//                                + "<yellow>MAGIC REDUCTION:</yellow> " + Debug.formatDecimal(MAGIC_REDUCTION) + "\n"
//                                + "\nOriginal damage: " + Debug.formatDecimal(event.getDamage())
//                                + modifiers
//                );
//                if(ARMOR_REDUCTION!=0)
//                Debug.message(p, "armor", "<blue>ARMOR:      </blue> "+Debug.formatDecimal(ARMOR_REDUCTION)+"x to <red>dmg");
//                if(TOUGHNESS_REDUCTION!=0)
//                Debug.message(p, "armor", "<light_purple>TOUGHNESS:</light_purple> "+((TOUGHNESS_REDUCTION!=0)?"-"+Debug.formatDecimal(TOUGHNESS_REDUCTION)+" to <red>dmg":"<gray>not applicable"));
//            }
//        }

    }

    /**
     * Calculate damage reduction for a specific armor piece based on material and slot
     */
    private double calculateArmorReduction(ItemStack armor, String slotReductionKey) {
        double baseReduction = SpecializationConfig.getArmorDamageReductionConfig().get(slotReductionKey, Double.class);
        double materialMultiplier = getMaterialMultiplier(armor);

        return baseReduction * materialMultiplier;
    }
    
    /**
     * Get the material multiplier for an armor piece
     */
    private double getMaterialMultiplier(ItemStack armor) {
        ConfigFile cfg = SpecializationConfig.getArmorDamageReductionConfig();
        switch (armor.getType()) {
            case DIAMOND_BOOTS:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_HELMET:
                return cfg("DIAMOND_MULTIPLIER");
            case IRON_BOOTS:
            case IRON_CHESTPLATE:
            case IRON_LEGGINGS:
            case IRON_HELMET:
                return cfg("IRON_MULTIPLIER");
            case LEATHER_BOOTS:
            case LEATHER_CHESTPLATE:
            case LEATHER_LEGGINGS:
            case LEATHER_HELMET:
                return cfg("LEATHER_MULTIPLIER");
            case CHAINMAIL_BOOTS:
            case CHAINMAIL_CHESTPLATE:
            case CHAINMAIL_LEGGINGS:
            case CHAINMAIL_HELMET:
                return cfg("CHAINMAIL_MULTIPLIER");
            case GOLDEN_BOOTS:
            case GOLDEN_CHESTPLATE:
            case GOLDEN_LEGGINGS:
            case GOLDEN_HELMET:
                return cfg("GOLDEN_MULTIPLIER");
            case NETHERITE_BOOTS:
            case NETHERITE_CHESTPLATE:
            case NETHERITE_LEGGINGS:
            case NETHERITE_HELMET:
                return cfg("NETHERITE_MULTIPLIER");
            default:
                // Default to iron multiplier for unknown materials
                return cfg("IRON_MULTIPLIER");
        }
    }

    //Grabs cfg
    private double cfg(String key){
        return SpecializationConfig.getArmorDamageReductionConfig().get(key, Double.class);
    }
    
    /**
     * Check if an item is armor (helmet, chestplate, leggings, or boots)
     */
    private boolean isArmor(ItemStack item) {
        if (item == null) {
            return false;
        }

        switch (item.getType()) {
            case DIAMOND_BOOTS:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_HELMET:
            case IRON_BOOTS:
            case IRON_CHESTPLATE:
            case IRON_LEGGINGS:
            case IRON_HELMET:
            case LEATHER_BOOTS:
            case LEATHER_CHESTPLATE:
            case LEATHER_LEGGINGS:
            case LEATHER_HELMET:
            case CHAINMAIL_BOOTS:
            case CHAINMAIL_CHESTPLATE:
            case CHAINMAIL_LEGGINGS:
            case CHAINMAIL_HELMET:
            case GOLDEN_BOOTS:
            case GOLDEN_CHESTPLATE:
            case GOLDEN_LEGGINGS:
            case GOLDEN_HELMET:
            case NETHERITE_BOOTS:
            case NETHERITE_CHESTPLATE:
            case NETHERITE_LEGGINGS:
            case NETHERITE_HELMET:
                return true;
            default:
                return false;
        }
    }
}

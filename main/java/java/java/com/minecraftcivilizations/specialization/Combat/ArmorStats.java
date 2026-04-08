package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds pairs of armor/toughness for later calculation
 * Also has a few constants which we use in DynamicArmor
 * @author Alectriciti ⚡
 */
public final class ArmorStats {

    public static final ArmorStats FULL_LEATHER = new ArmorStats(7,0);
    public static final ArmorStats FULL_GOLD = new ArmorStats(11,0);
    public static final ArmorStats FULL_CHAINMAIL = new ArmorStats(12,0);
    public static final ArmorStats FULL_IRON = new ArmorStats(15,0);
    public static final ArmorStats FULL_TURTLE = new ArmorStats(15,0); //effectively iron
    public static final ArmorStats FULL_DIAMOND = new ArmorStats(20,8);
    public static final ArmorStats FULL_NETHERITE = new ArmorStats(20,12);

    public static ArmorStats getFullSetStats(Material armor) {
        if (armor == null) return null;

        switch (armor) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS:
                return FULL_LEATHER;

            case GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS:
                return FULL_GOLD;

            case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS:
                return FULL_CHAINMAIL;

            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS:
                return FULL_IRON;

            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS:
                return FULL_DIAMOND;

            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS:
                return FULL_NETHERITE;

            case TURTLE_HELMET:
                return FULL_TURTLE;
            default:
                return null;
        }
    }

    @Getter
    private final double armor;
    @Getter
    private final double toughness;
    @Getter
    private final double knockback_resist;

    private double water_movement = 0;


    public double getWaterMovement() {
        return water_movement;
    }

    public ArmorStats(double armor, double toughness) {
        this.armor = armor;
        this.toughness = toughness;
        this.knockback_resist = 0;
    }

    public ArmorStats(double armor, double toughness, double knockback_resist, double water_movement) {
        this.armor = armor;
        this.toughness = toughness;
        this.knockback_resist = knockback_resist;
        this.water_movement = water_movement;
    }

    public ArmorStats(double armor, double toughness, double knockback_resist) {
        this.armor = armor;
        this.toughness = toughness;
        this.knockback_resist = knockback_resist;
    }

    public ArmorStats setWaterMovement(double watermovement){
        this.water_movement = watermovement;
        return this;
    }

    public static double getArmorHeight(EquipmentSlot slot) {
        switch (slot) {
            case FEET: return 0.1;
            case LEGS: return 0.8;
            case CHEST: return 1.2;
            case HEAD: return 1.6;
            case HAND:
            case OFF_HAND: return 1.1;
            default: return 1.0;
        }
    }

    public static Sound getArmorSound(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return null;
        List<ItemStack> valid = new ArrayList<>();

        for (ItemStack armor : living.getEquipment().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                valid.add(armor);
            }
        }

        if (valid.isEmpty()) return null;
        ItemStack armor_to_check = valid.get(ThreadLocalRandom.current().nextInt(valid.size()));


        Material mat = armor_to_check.getType();
        Material base = getMaterialType(mat);
        if(base==null)return null;
        switch(base){
            case LEATHER: return Sound.ITEM_WOLF_ARMOR_DAMAGE;
            case CHAIN: return Sound.BLOCK_CHAIN_HIT;
            case GOLD_INGOT: return Sound.BLOCK_HEAVY_CORE_HIT;
            case IRON_INGOT: return Sound.BLOCK_HEAVY_CORE_PLACE;
            case DIAMOND: return Sound.BLOCK_HEAVY_CORE_PLACE;
            case NETHERITE_INGOT: return Sound.BLOCK_HEAVY_CORE_PLACE;
        }
        return null;
    }

    @Override
    public String toString() {
        return "ArmorStats{armor=" + armor + ", toughness=" + toughness + ", knockback="+knockback_resist+"}";
    }

    /**
     * Returns specific Vanilla Stats for individual pieces of armor
     */
    public static ArmorStats getVanillaStats(Material armor) {
        if (armor == null) return new ArmorStats(0, 0);

        switch (armor) {
            case LEATHER_HELMET:
            case LEATHER_BOOTS:
                return new ArmorStats(1, 0);
            case LEATHER_LEGGINGS:
                return new ArmorStats(2, 0);
            case LEATHER_CHESTPLATE:
                return new ArmorStats(3, 0);

            case GOLDEN_HELMET:
            case GOLDEN_BOOTS:
                return new ArmorStats(2, 0,0,1);
            case GOLDEN_LEGGINGS:
                return new ArmorStats(3, 0,0,1);
            case GOLDEN_CHESTPLATE:
                return new ArmorStats(5, 0,0,1);

            case CHAINMAIL_HELMET:
                return new ArmorStats(2, 0,0,1);
            case CHAINMAIL_BOOTS:
                return new ArmorStats(2, 0,0,1);
            case CHAINMAIL_LEGGINGS:
                return new ArmorStats(5, 0,0,1);
            case CHAINMAIL_CHESTPLATE:
                return new ArmorStats(6, 0,0,1);


            case IRON_HELMET:
                return new ArmorStats(2, 1, 0, 1);
            case IRON_BOOTS:
                return new ArmorStats(2, 1,0, 1);
            case IRON_LEGGINGS:
                return new ArmorStats(5, 1,0, 1);
            case IRON_CHESTPLATE:
                return new ArmorStats(6, 1,0, 1);

            case DIAMOND_HELMET:
            case DIAMOND_BOOTS:
                return new ArmorStats(3, 2,0, 2);
            case DIAMOND_LEGGINGS:
                return new ArmorStats(6, 2,0, 2);
            case DIAMOND_CHESTPLATE:
                return new ArmorStats(8, 2,0, 2);

            case NETHERITE_HELMET:
            case NETHERITE_BOOTS:
                return new ArmorStats(3, 3,0, 2);
            case NETHERITE_LEGGINGS:
                return new ArmorStats(6, 3, 0, 2);
            case NETHERITE_CHESTPLATE:
                return new ArmorStats(8, 3, 0, 2);

            case TURTLE_HELMET:
                return new ArmorStats(2, 0);

            default:
                return new ArmorStats(0, 0);
        }
    }

    /**
     * Returns a block that matches the material
     */
    public static Material getMaterialBlockType(Material armor){
        switch (armor) {
            // Leather
            case LEATHER_HELMET:
            case LEATHER_CHESTPLATE:
            case LEATHER_LEGGINGS:
            case LEATHER_BOOTS:
                return Material.SOUL_SOIL;

            // Chainmail
            case CHAINMAIL_HELMET:
            case CHAINMAIL_CHESTPLATE:
            case CHAINMAIL_LEGGINGS:
            case CHAINMAIL_BOOTS:
                return Material.CHAIN;

            // Iron
            case IRON_HELMET:
            case IRON_CHESTPLATE:
            case IRON_LEGGINGS:
            case IRON_BOOTS:
                return Material.IRON_BLOCK;

            // Gold
            case GOLDEN_HELMET:
            case GOLDEN_CHESTPLATE:
            case GOLDEN_LEGGINGS:
            case GOLDEN_BOOTS:
                return Material.GOLD_BLOCK;

            // Diamond
            case DIAMOND_HELMET:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_BOOTS:
                return Material.DIAMOND_BLOCK;

            // Netherite
            case NETHERITE_HELMET:
            case NETHERITE_CHESTPLATE:
            case NETHERITE_LEGGINGS:
            case NETHERITE_BOOTS:
                return Material.NETHERITE_BLOCK;

            // Turtle
            case TURTLE_HELMET:
                return Material.EMERALD_BLOCK;

            default:
                return Material.AIR;
        }
    }

    /**
     * Returns a material that is used in the creation of the armor piece
     */
    public static Material getMaterialType(Material armor) {
        switch (armor) {
            // Leather
            case LEATHER_HELMET:
            case LEATHER_CHESTPLATE:
            case LEATHER_LEGGINGS:
            case LEATHER_BOOTS:
                return Material.LEATHER;

            // Chainmail
            case CHAINMAIL_HELMET:
            case CHAINMAIL_CHESTPLATE:
            case CHAINMAIL_LEGGINGS:
            case CHAINMAIL_BOOTS:
                return Material.CHAIN;

            // Iron
            case IRON_HELMET:
            case IRON_CHESTPLATE:
            case IRON_LEGGINGS:
            case IRON_BOOTS:
                return Material.IRON_INGOT;

            // Gold
            case GOLDEN_HELMET:
            case GOLDEN_CHESTPLATE:
            case GOLDEN_LEGGINGS:
            case GOLDEN_BOOTS:
                return Material.GOLD_INGOT;

            // Diamond
            case DIAMOND_HELMET:
            case DIAMOND_CHESTPLATE:
            case DIAMOND_LEGGINGS:
            case DIAMOND_BOOTS:
                return Material.DIAMOND;

            // Netherite
            case NETHERITE_HELMET:
            case NETHERITE_CHESTPLATE:
            case NETHERITE_LEGGINGS:
            case NETHERITE_BOOTS:
                return Material.NETHERITE_INGOT;

            // Turtle
            case TURTLE_HELMET:
                return Material.TURTLE_SCUTE;

            default:
                return null;
        }
    }

    public static EquipmentSlot getSlot(Material mat) {
        switch (mat) {
            // Head
            case LEATHER_HELMET:
            case IRON_HELMET:
            case CHAINMAIL_HELMET:
            case GOLDEN_HELMET:
            case DIAMOND_HELMET:
            case NETHERITE_HELMET:
            case TURTLE_HELMET:
                return EquipmentSlot.HEAD;

            // Chest
            case LEATHER_CHESTPLATE:
            case IRON_CHESTPLATE:
            case CHAINMAIL_CHESTPLATE:
            case GOLDEN_CHESTPLATE:
            case DIAMOND_CHESTPLATE:
            case NETHERITE_CHESTPLATE:
                return EquipmentSlot.CHEST;

            // Legs
            case LEATHER_LEGGINGS:
            case IRON_LEGGINGS:
            case CHAINMAIL_LEGGINGS:
            case GOLDEN_LEGGINGS:
            case DIAMOND_LEGGINGS:
            case NETHERITE_LEGGINGS:
                return EquipmentSlot.LEGS;

            // Feet
            case LEATHER_BOOTS:
            case IRON_BOOTS:
            case CHAINMAIL_BOOTS:
            case GOLDEN_BOOTS:
            case DIAMOND_BOOTS:
            case NETHERITE_BOOTS:
                return EquipmentSlot.FEET;

            default:
                return null;
        }
    }

    public static ArmorStats getArmorStats(EntityEquipment equipment) {
        if (equipment == null) {
            Debug.broadcast("armorstats", "returning null!");
            return new ArmorStats(0, 0);
        }

        double total_armor = 0;
        double total_toughness = 0;

        ItemStack[] armor_items = {
                equipment.getHelmet(),
                equipment.getChestplate(),
                equipment.getLeggings(),
                equipment.getBoots()
        };

        for (ItemStack item : armor_items) {
            if (item == null){
                continue;
            }
            ItemMeta meta = item.getItemMeta();

            if((meta != null) && meta.hasAttributeModifiers()) {
                for (AttributeModifier mod : Objects.requireNonNull(meta.getAttributeModifiers(Attribute.ARMOR))) {
                    total_armor += mod.getAmount();
                }
                for (AttributeModifier mod : Objects.requireNonNull(meta.getAttributeModifiers(Attribute.ARMOR_TOUGHNESS))) {
                    total_toughness += mod.getAmount();
                }
            }else{
                ArmorStats stats = getVanillaStats(item.getType());
                total_armor += stats.getArmor();
                total_toughness += stats.getToughness();
            }
        }
        return new ArmorStats(total_armor, total_toughness);
    }
}
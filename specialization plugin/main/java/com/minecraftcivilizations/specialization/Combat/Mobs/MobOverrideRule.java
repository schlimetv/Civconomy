package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * a light-weight random selector for overriding vanilla mobs to sprinkle in some variety
 * @author Alectriciti
 */
public class MobOverrideRule {

    //this is the UNIVERSAL chance for all MobOverrideRules.
    private static int defaultBaseChance = 100;

    @Getter
    private final int chance; //This should ONLY communicate to MobOverrideRuleSet, do not use internally. Only assign here in this class.

    @Getter
    private final EnumSet<EntityType> replaceTypes;

    //Map to chance
    private final Map<MobVariation, Integer> variation_mapping = new HashMap<>();

    private int total_roll = -1;

    /**
     * Establishes a base chance of no override taking place
     * Build upon this with override(entity_type, chance)
     * @param rule_chance this is the chance to roll this rule. this is rolled using MobOverrideRule.globalBaseChance
     */
    public MobOverrideRule(int rule_chance, EntityType...replace_types){
        this.replaceTypes = EnumSet.noneOf(EntityType.class);
        Collections.addAll(this.replaceTypes, replace_types);
        this.chance = rule_chance;
        MobManager.getInstance().registerRule(this);
    }

    public MobOverrideRule addVariation(MobVariation variation, int variation_chance){
        variation_mapping.put(variation, variation_chance);
        return this;
    }

    /**
     * Adds a variation using the global base chance
     */
    public MobOverrideRule addVariation(MobVariation variation){
        variation_mapping.put(variation, defaultBaseChance);
        return this;
    }


    public MobVariation rollVariation() {
        calculateTotalRoll(); // now total_roll == sum(variations) only
        if (total_roll <= 0) {
//            Debug.broadcast("mobrule", "<blue>rollVariation</blue> total_roll <= 0 -> null");
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(total_roll); // 0..total_roll-1
        int accum = 0;

        for (Map.Entry<MobVariation, Integer> entry : variation_mapping.entrySet()) {
            accum += entry.getValue();
            if (roll < accum) {
//                Debug.broadcast("mobrule", "<blue>rollVariation</blue> returns " + entry.getKey().getId());
                return entry.getKey();
            }
        }

//        Debug.broadcast("mobrule", "<blue>rollVariation</blue> returns null as fallback");
        return null;
    }


    private void calculateTotalRoll() {
        int sum = 0;
        for (int i : variation_mapping.values()) sum += Math.max(0, i);
        total_roll = sum;
//        Debug.broadcast("mobrule", "<green>calculated total roll:</green> " + total_roll);

    }

    private boolean does_spawn_in_packs = false;

    public MobOverrideRule spawnInPacks(){
        this.does_spawn_in_packs = true;
        return this;
    }

    public boolean doesSpawnInPacks() {
        return does_spawn_in_packs;
    }
}

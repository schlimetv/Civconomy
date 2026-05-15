package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.EntityType;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Represents a set of rules based on an EntityType
 */
public class MobOverrideRuleSet {

    @Setter @Getter
    private int baseChance = 100; // this is the chance that the Vanilla entity will spawn for this EntityType

    private final Set<MobOverrideRule> rules = new HashSet<>();
    private int total_chance = 0;
    private EntityType type;

    public MobOverrideRuleSet(EntityType type){
        this.type = type;
    }

    public void add(MobOverrideRule rule) {
        if (rule == null) return;
        boolean added = rules.add(rule);
//        Debug.broadcast("mobrule", "<dark_green>adding rule chance for </dark_green>=" + rule.getChance() + " " + Debug.formatBoolean(added));
        if (added) total_chance += Math.max(0, rule.getChance());
    }

    private int sumOverrideChances() {
        int sum = 0;
        for (MobOverrideRule rule : rules) sum += Math.max(0, rule.getChance());
        return sum;
    }

    public MobOverrideRule rollRule() {
        int overrides_total = sumOverrideChances();
        int total = baseChance + overrides_total;
//        Debug.broadcast("mobrule", "base:" + baseChance + " overrides:" + overrides_total + " total:" + total);
        if (total <= 0) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        if (r < baseChance){
//            Debug.broadcast("mobrule", "<dark_gray> "+type.name()+" <gray>landed "+r+" out of "+total+ " <white>VANILLA");
            return null; //returns vanilla mob
        }
        r -= baseChance;
        int accum = 0;
        MobOverrideRule selected = null;
        for (MobOverrideRule rule : rules) {
            accum += Math.max(0, rule.getChance());
            if (r < accum){
//                Debug.broadcast("mobrule", "returning a valid RULE!");
                selected = rule;
                break;
            }
        }
//        Debug.broadcast("mobrule", "<dark_gray> "+type.name()+" <gray>landed "+r+" out of "+total+ " <green>MODIFIED");
        return selected;
    }

    public int getTotalChance() {
        return baseChance + total_chance;
    }
}

package com.minecraftcivilizations.specialization.Skill;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

@AllArgsConstructor
public class Skill {

    @Getter
    @Setter
    private SkillType skillType;
    @Getter
    private double xp;
    @Getter
    private long lastUpdate;

    static final int MAX_LEVEL = 5;
    public static double[] CACHED_LEVELS;

    /**
     * Optimized ⚡
     * Called at runtime. (No need to run this math 5 times per XP Action...)
     */
    public static void InitCacheXPLevelFormula() {
        CACHED_LEVELS = new double[MAX_LEVEL + 1];
        CACHED_LEVELS[0] = 0.0;
        //ASSIGN XP CURVE FORMULA
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            CACHED_LEVELS[lvl] = Math.floor(2 * (25 * Math.pow(lvl, 2) + (5 * lvl) + (200 * Math.pow(2.45, lvl))) - 400 );
        }
    }

    /**
     * Optimized AF ⚡
     * Uses a pre-cached lookup, you're welcome.
     */
    public static double getXPNeededForLevel(int level) {
//        Debug.broadcast("xp", "xp needed lookup");
        return CACHED_LEVELS[Math.max(0, Math.min(MAX_LEVEL, level))];
    }

    public static double mapValue(double x, double in_min, double in_max, double out_min, double out_max) {
        // Handle division by zero case when in_max equals in_min
        if (in_max == in_min) {
            return out_min; // Return minimum output value when input range is zero
        }
        return out_min + (x - in_min) * (out_max - out_min) / (in_max - in_min);
    }

    public void applyXp(Player player, double appliedXp, boolean allowNegative) {
        if(appliedXp!=0) {
            if (!allowNegative) {
                this.xp += Math.max(appliedXp, 0); //prevents unintentional negative xp gain
            } else {
                this.xp += appliedXp; //can potentially subtract xp
                if (this.xp < 0) {
                    this.xp = 0; //ensures xp does not get set below zero
                }
            }
        }

        boolean positive = appliedXp>0;
        TextComponent valuecomp;
        if(appliedXp>0){
            valuecomp = Component.text("+"+appliedXp+" ").color(NamedTextColor.GREEN);
        } else{
            valuecomp = Component.text("-"+appliedXp+" ").color(NamedTextColor.RED);
        }
        TextComponent comp = Component.text(" "+getSkillType().name()+" ").color(NamedTextColor.WHITE)
                .append(valuecomp)
                .append(Component.text("("+this.xp+")").color(NamedTextColor.GRAY));
        Debug.broadcast("xp_"+player.getName(), comp);
        this.lastUpdate = System.currentTimeMillis();
    }

}

package com.minecraftcivilizations.specialization.Skill;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;

public enum SkillType {

    FARMER,
    BUILDER,
    MINER,
    HEALER,
    LIBRARIAN,
    GUARDSMAN,
    BLACKSMITH;

    public String getSkillDescription() {
        return SpecializationConfig.getSkillsConfig().get(this + "_DESCRIPTION", String.class);
    }

    public Material getSkillWorkstation() {
        return SpecializationConfig.getSkillsConfig().get(this + "_WORKSTATION", Material.class);
    }

    /**
     * Optimized Level from XP ⚡
     */
    public static int getLevelFromXP(double xp) {
        if (xp <= 0) return 0;

        if (Skill.CACHED_LEVELS == null) Skill.InitCacheXPLevelFormula();
        double[] cached_levels = Skill.CACHED_LEVELS;
        int last_level = cached_levels.length - 1;

        for (int lvl = 0; lvl < last_level; lvl++) {
            if (xp < cached_levels[lvl + 1]) return lvl;
        }

        return last_level;
    }

    public static String getDisplayName(SkillType skillType) {
        return StringUtils.capitalize(skillType.name().toLowerCase());
    }


}

package com.minecraftcivilizations.specialization.Skill;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public enum SkillLevel {

    NOVICE(0),
    APPRENTICE(1),
    JOURNEYMAN(2),
    EXPERT(3),
    MASTER(4),
    GRANDMASTER(5);

    @Getter
    private final int level;

    private static final SkillLevel[] VALUES = values();

    //Optimized ⚡
    public static SkillLevel getSkillLevelFromInt(int skillLevel) {
        if (skillLevel < 0) return NOVICE;
        if (skillLevel >= VALUES.length) return GRANDMASTER;
        return VALUES[skillLevel];
    }

    public static String getDisplayName(SkillLevel skillLevel) {
        return skillLevel.name().substring(0, 1).toUpperCase() + skillLevel.name().toLowerCase().substring(1, skillLevel.name().length());
    }

    public static String getDisplayName(int skillLevel) {
        return getDisplayName(getSkillLevelFromInt(skillLevel));
    }
}

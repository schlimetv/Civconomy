package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.MathUtils;
import lombok.Getter;

public class ArmorBreakStats{

    int armor_break;
    int shield_break;


    @Getter
    SkillType restrictedSkillType = null;

    int low_lvl_bonus; // bonus at lvl 1
    int high_lvl_bonus; //bonus at lvl 5


    public ArmorBreakStats(int base, int shield){
        this.armor_break = base;
        this.shield_break = shield;
    }

    public ArmorBreakStats setSkillBonus(SkillType skillType, int low_lvl_bonus, int high_lvl_bonus) {
        this.restrictedSkillType = skillType;
        this.low_lvl_bonus = low_lvl_bonus;
        this.high_lvl_bonus = high_lvl_bonus;
        return this;
    }

    public int getSkillBonus(CustomPlayer customPlayer){
        int playerLevel = customPlayer.getSkillLevel(restrictedSkillType);
        if(playerLevel>0){
            return (int) MathUtils.lerp(low_lvl_bonus, high_lvl_bonus, ((double) playerLevel) / 5.0);
        }
        return 0;
    }

}
package com.minecraftcivilizations.specialization.Events;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player changes their skill level
 * Different skills can tap into this.
 */
public class SkillLevelChangeEvent extends Event{

    //Boilerplate
    private static final HandlerList HANDLERS = new HandlerList();
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
    public static HandlerList getHandlerList() { return HANDLERS; }

    @Getter
    private CustomPlayer customPlayer;
    @Getter
    private Player player;
    @Getter
    private SkillType skillType;
    @Getter
    private int oldLevel;
    @Getter
    private int newLevel;
    @Getter
    private double xp;



    /**
     * Called by CustomPlayer.java
     */
    public SkillLevelChangeEvent(CustomPlayer customPlayer, Player player, SkillType skillType, int old_level, int new_level, double xp) {
        this.customPlayer = customPlayer;
        this.player = player;
        this.skillType = skillType;
        this.oldLevel = old_level;
        this.newLevel = new_level;
        this.xp = xp;
    }


    public boolean isLevelUp(){
        return newLevel>oldLevel;
    }



}

package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.CraftItemEvent;

/**
 * Was going to use this for something, maybe not, yneverknow
 * @author Alectriciti
 */
public class SpecializationCraftItemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Getter
    private CraftItemEvent event;
    @Getter
    private Player player;
    @Getter
    private int craftedAmount;
    @Getter
    private int totalReduction;
    @Getter
    private SkillType skillType;
    @Getter
    private int skillLevel;


    private boolean cancel_xp = false;

    /**
     * Cancels Specialization's Crafting XP
     */
    public void setCancelXp(boolean cancel){
        this.cancel_xp = cancel;
    }

    public SpecializationCraftItemEvent(CraftItemEvent event, Player player, int craftedAmount, int totalReduction, SkillType skillType, int skillLevel) {
        this.event = event;
        this.player = player;
        this.craftedAmount = craftedAmount;
        this.totalReduction = totalReduction;
        this.skillType = skillType;
        this.skillLevel = skillLevel;
        Debug.broadcast("event", "CraftItemEvent created by "+player.getName()+" crafting "+event.getRecipe().getResult().getType().name());
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }


    /**
     * Is Specialization's Crafting XP cancelled
     */
    public boolean isXpCancelled() {
        return cancel_xp;
    }

}

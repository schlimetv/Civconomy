package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 *
 * @author ⚡ alectriciti ⚡
 */
public class CustomItemCreationEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final CustomItem custom_item_class;
    private final ItemStack item_stack;
    private final Player player;
    private boolean cancelled;

    public CustomItemCreationEvent(CustomItem custom_item_class, ItemStack item_stack, Player creator) {
        this.custom_item_class = custom_item_class;
        this.item_stack = item_stack;
        this.player = creator;
    }

    public CustomItem getCustomItemClass() {
        return custom_item_class;
    }

    public ItemStack getItemStack() {
        return item_stack;
    }

    public Player getPlayer(){ return player; }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}

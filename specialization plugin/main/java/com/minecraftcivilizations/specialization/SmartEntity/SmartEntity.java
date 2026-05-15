package com.minecraftcivilizations.specialization.SmartEntity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Custom
 * @Author Alectriciti
 */
public class SmartEntity {

    static SmartEntityManager manager;


    protected Entity owner;
    protected Location location;
    protected int tick;

    public SmartEntity(Entity owner, Location location){
        this.owner = owner;

        this.location = location;
//        if(owner instanceof Player player){
//            player.sendMessage("created entity at "+location.toString());
//        }
        manager.registerEntity(this);
    }

    /**
     * Called by the manager
     */
    void always_update(){
        tick++;
        update();
    }

    /**
     * Override this to implement custom functionality
     */
    public void update(){

    }

    public void destroy(){
        manager.unregisterEntity(this);
    }


}

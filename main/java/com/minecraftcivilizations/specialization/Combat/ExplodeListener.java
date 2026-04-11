package com.minecraftcivilizations.specialization.Combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Explosion protection for reinforced blocks is handled by BreakBlockListener
 * (probability-based blast resistance) — this listener is kept as a registered
 * placeholder so the registration in Specialization.onEnable() does not break.
 */
public class ExplodeListener implements Listener {
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        // Intentionally empty — blast resistance logic lives in BreakBlockListener
    }
}

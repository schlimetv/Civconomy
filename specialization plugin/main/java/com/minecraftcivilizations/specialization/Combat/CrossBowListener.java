package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import org.bukkit.enchantments.Enchantment;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class CrossBowListener implements Listener {

    @EventHandler
    public void onCrossBowShoot(EntityShootBowEvent bowEvent) {

        ItemStack bow = bowEvent.getBow();
        if (bow == null) {
            return;
        }

        if (bow.getType() != Material.CROSSBOW) {
            return;
        }

        if (!(bowEvent.getProjectile() instanceof Projectile)) {
            return;
        }
        
        Projectile projectile = (Projectile) bowEvent.getProjectile();
        if (!(projectile instanceof Arrow)) {
            return;
        }
//        Debug.broadcast("damage", "CrossbowListener for Enchantments");
        
        Arrow arrow = (Arrow) projectile;

        double baseVelocity = SpecializationConfig.getCombatConfig().get("CROSSBOW_BASE_VELOCITY", Double.class);
        arrow.setVelocity(arrow.getVelocity().multiply(baseVelocity));

        if (bow.containsEnchantment(Enchantment.MULTISHOT)) {
            double multishotVelocity = SpecializationConfig.getCombatConfig().get("CROSSBOW_BASE_MULTISHOT_VELOCITY", Double.class);
            arrow.setVelocity(arrow.getVelocity().multiply(multishotVelocity));
        } else if (bow.containsEnchantment(Enchantment.PIERCING)) {
            double piercingVelocity = SpecializationConfig.getCombatConfig().get("CROSSBOW_BASE_PIERCING_VELOCITY", Double.class);
            arrow.setVelocity(arrow.getVelocity().multiply(piercingVelocity));
        } else if (bow.containsEnchantment(Enchantment.QUICK_CHARGE)) {
            double quickChargeVelocity = SpecializationConfig.getCombatConfig().get("CROSSBOW_BASE_QUICKCHARGE_VELOCITY", Double.class);
            arrow.setVelocity(arrow.getVelocity().multiply(quickChargeVelocity));
        }
    }
}

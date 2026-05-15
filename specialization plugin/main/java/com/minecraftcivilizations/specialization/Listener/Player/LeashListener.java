package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;


/**
 * @author Jfrogy
 */

/**
 * This class handles the leashing of players using a proxy which is a sheep the player rides.
 * The sheep is somewhat arbitrary. The only important part is that the entity can move and be lead naturally.
 * This way the entity can be tied to things and handle normal persistent logout/login lead logic
 * <p>
 * Use player.getVehicle() instanceOf sheep and then
 * <p>
 * Relies on: PlayerDownedListener States & ReviveListener
 * <p>
 * Utilized by: Nothing
 */

public final class LeashListener implements Listener {

    private final Map<Player, Sheep> proxies = new HashMap<>();
    private final PlayerDownedListener downedListener;
    private final NamespacedKey leashKey = new NamespacedKey(Specialization.getInstance(), "leash_proxy");

    public LeashListener() {
        this.downedListener = new PlayerDownedListener(Specialization.getInstance());
    }

    @EventHandler
    public void onLeash(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Entity target = e.getRightClicked();
        Player leasher = e.getPlayer();

        // Check if the target is already leashed via a proxy
        if (target.getVehicle() instanceof Sheep proxy && proxy.getPersistentDataContainer().has(leashKey, PersistentDataType.BOOLEAN)) {
            if (target instanceof Player targetPlayer) {
                unleashPlayer(targetPlayer, proxy);
            }
            return;
        }

        // Must hold a lead
        if (leasher.getInventory().getItemInMainHand().getType() != Material.LEAD &&
                leasher.getInventory().getItemInOffHand().getType() != Material.LEAD) return;

        // Only leash downed players not already carried
        if (target instanceof Player targetPlayer) {
            // Check if they are already riding a sheep
            boolean ridingProxy = targetPlayer.getVehicle() instanceof Sheep;

            // Check if they are already a passenger of the leasher
            boolean carriedByLeasher = leasher.getPassengers().contains(targetPlayer);

            boolean isCarried = targetPlayer.getVehicle() instanceof Player;

            if (!ridingProxy && !isCarried) {
                leashPlayer(targetPlayer, leasher);
//                e.setCancelled(true);
            }
        }
    }

    private void leashPlayer(Player targetPlayer, Player leasher) {
        CustomPlayer h = CoreUtil.getPlayer(leasher);

        int lvl = h.getSkillLevel(SkillType.GUARDSMAN);

        if (lvl == SkillLevel.APPRENTICE.getLevel()) {
            Specialization.message(leasher, "You are not skilled enough for that");
        }else if (lvl < SkillLevel.APPRENTICE.getLevel()) {
            return; // too low level to leash players
        }

        if (!downedListener.isDowned(targetPlayer)) {
            PlayerUtil.message(leasher, "Only downed players may be leaded");

            return;
        }

        downedListener.clearMount(targetPlayer);

        Sheep proxy = spawnProxy(targetPlayer.getLocation(), leasher);
        proxy.addPassenger(targetPlayer);
        proxy.setLeashHolder(leasher);
        proxy.getPersistentDataContainer().set(leashKey, PersistentDataType.BOOLEAN, true);
        targetPlayer.getPersistentDataContainer().set(new NamespacedKey(Specialization.getInstance(), "is_leashed"), PersistentDataType.BOOLEAN, true);
        proxies.put(targetPlayer, proxy);

        leasher.getInventory().getItemInMainHand().subtract(1);
    }

    private void unleashPlayer(Player targetPlayer, Sheep proxy) {
        downedListener.clearMount(targetPlayer);
        downedListener.setSit(targetPlayer);

        // Determine where to drop the lead
        Location dropLocation = proxy.getLocation();
        Entity leashHolder = proxy.getLeashHolder();

        if (leashHolder instanceof org.bukkit.entity.LeashHitch hitch) {
            hitch.remove(); // clean up the hitch entity
        }

        proxy.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.LEAD, 1));

        if (proxy.isValid()) {
            for (Entity passenger : proxy.getPassengers()) passenger.leaveVehicle();
            proxy.remove();
        }
        targetPlayer.getPersistentDataContainer().remove(new NamespacedKey(Specialization.getInstance(), "is_leashed"));
        proxies.remove(targetPlayer);
    }


    // -------------------------
    // Proxy spawn utility
    // -------------------------
    private Sheep spawnProxy(Location loc, Player leasher) {
        Location spawnLoc = loc.clone().add(0, 0.5, 0);
        return spawnLoc.getWorld().spawn(spawnLoc, Sheep.class, sm -> {
            sm.setSilent(true);
            sm.setInvisible(true);
            sm.setAware(false);
            sm.setAI(true);
            sm.setPersistent(true);
            sm.setRemoveWhenFarAway(false);
            sm.setGravity(true);
            sm.setInvulnerable(true);
            sm.setCanPickupItems(false);
            sm.setCollidable(false);
            sm.setGlowing(false);
            sm.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.0);
            sm.getAttribute(Attribute.SCALE).setBaseValue(0.23);
            sm.getEquipment().clear();
        });
    }


    @EventHandler
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player p) {
            Sheep proxy = proxies.get(p);
            if (proxy != null && proxy.isValid()) {
                unleashPlayer(p, proxy);
            }
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        // Check if the player is riding a proxy sheep
        Entity vehicle = p.getVehicle();
        if (vehicle instanceof Sheep sheep) return;

        // cleanup if needed
//        p.getPersistentDataContainer().set(
//                new NamespacedKey(Specialization.getInstance(), "is_leashed"),
//                PersistentDataType.BOOLEAN,
//                false
//        );

    }


    //prevent sneaking/dismounting while marked as leashed.
    @EventHandler
    public void onSneakDismount(PlayerToggleSneakEvent e) {
        Player rider = e.getPlayer();

        Boolean isLeashed = rider.getPersistentDataContainer().get(
                new NamespacedKey(Specialization.getInstance(), "is_leashed"),
                PersistentDataType.BOOLEAN
        );

        if (isLeashed != null && isLeashed && e.isSneaking()) {
            e.setCancelled(true); // Prevent sneak-dismount while leashed
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (e.isCancelled()) return;
        if (!(e.getEntity() instanceof Player rider)) return;

        Boolean isLeashed = rider.getPersistentDataContainer().get(
                new NamespacedKey(Specialization.getInstance(), "is_leashed"),
                PersistentDataType.BOOLEAN
        );

//        if (isLeashed != null && isLeashed) {
//
//            e.setCancelled(true); // Prevent dismount while leashed
//        }
    }

    // -------------------------
    // Prevent snow layering
    // -------------------------
    @EventHandler
    public void onEntityBlockForm(EntityBlockFormEvent e) {
        if (!(e.getEntity() instanceof Sheep sheep)) return;
        if (!sheep.getPersistentDataContainer().has(leashKey, PersistentDataType.STRING)) return;

        if (e.getNewState().getType() == Material.SNOW) e.setCancelled(true);
    }
}

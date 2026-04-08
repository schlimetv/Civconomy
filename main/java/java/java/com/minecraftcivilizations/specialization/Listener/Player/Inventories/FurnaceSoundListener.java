package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plays custom furnace sounds (open, close, fire loop, fire start/stop)
 * broadcast to all nearby players via world.playSound().
 *
 * Sound keys (from assets/steel/sounds.json):
 *   steel.furnace.open    - furnace lid opens
 *   steel.furnace.close   - furnace lid closes
 *   steel.furnace.fire1/2/3 - looping crackle while burning
 *   steel.fire.start      - ignition click
 *   steel.fire.stop       - fire extinguishes
 */
public class FurnaceSoundListener implements Listener {

    private static final String FURNACE_OPEN   = "steel:steel.furnace.open";
    private static final String FURNACE_CLOSE  = "steel:steel.furnace.close";
    private static final String[] FIRE_LOOPS   = {
        "steel:steel.furnace.fire1", "steel:steel.furnace.fire2", "steel:steel.furnace.fire3"
    };
    private static final String FIRE_START     = "steel:steel.fire.start";
    private static final String FIRE_STOP      = "steel:steel.fire.stop";

    private static final float VOLUME          = 0.8f;
    private static final int   FIRE_LOOP_TICKS = 40; // 2 s per clip

    private final Plugin plugin;
    private final Random random = new Random();

    /** Active fire-loop tasks keyed by block Location (serialised as string). */
    private final Map<String, BukkitTask> activeFire = new ConcurrentHashMap<>();

    public FurnaceSoundListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // ──────────────────────────────────────────────────────────
    //  Open / Close
    // ──────────────────────────────────────────────────────────

    @EventHandler
    public void onFurnaceOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.FURNACE &&
            event.getInventory().getType() != InventoryType.BLAST_FURNACE) return;

        Location loc = event.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        String openSound = (event.getInventory().getType() == InventoryType.BLAST_FURNACE)
            ? "steel:steel.blast_furnace.open"
            : FURNACE_OPEN;

        loc.getWorld().playSound(loc.clone().add(0.5, 0.5, 0.5), openSound, SoundCategory.BLOCKS, VOLUME, 1.0f);
    }

    @EventHandler
    public void onFurnaceClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.FURNACE &&
            event.getInventory().getType() != InventoryType.BLAST_FURNACE) return;

        Location loc = event.getInventory().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        String closeSound = (event.getInventory().getType() == InventoryType.BLAST_FURNACE)
            ? "steel:steel.blast_furnace.close"
            : FURNACE_CLOSE;

        loc.getWorld().playSound(loc.clone().add(0.5, 0.5, 0.5), closeSound, SoundCategory.BLOCKS, VOLUME, 1.0f);
    }

    // ──────────────────────────────────────────────────────────
    //  Fire loop (ignition → loop → extinguish)
    // ──────────────────────────────────────────────────────────

    /**
     * Fires when the furnace consumes a new fuel item and begins burning.
     * Plays fire_start and kicks off the repeating fire loop if not already active.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBurn(FurnaceBurnEvent event) {
        Block block = event.getBlock();
        Location loc = block.getLocation().clone().add(0.5, 0.5, 0.5);
        if (loc.getWorld() == null) return;

        String key = locKey(block.getLocation());
        if (!activeFire.containsKey(key)) {
            // Furnace just ignited — play start sound and begin loop
            loc.getWorld().playSound(loc, FIRE_START, SoundCategory.BLOCKS, VOLUME, 1.0f);
            startFireLoop(block.getLocation());
        }
    }

    private void startFireLoop(Location blockLoc) {
        String key = locKey(blockLoc);
        stopFireLoop(key);

        Location soundLoc = blockLoc.clone().add(0.5, 0.5, 0.5);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (soundLoc.getWorld() == null) { cancel(); activeFire.remove(key); return; }

                Block b = blockLoc.getBlock();
                if (!(b.getState() instanceof Furnace furnace)) {
                    // Block is no longer a furnace
                    soundLoc.getWorld().playSound(soundLoc, FIRE_STOP, SoundCategory.BLOCKS, VOLUME, 1.0f);
                    cancel();
                    activeFire.remove(key);
                    return;
                }

                if (furnace.getBurnTime() <= 0) {
                    // Fuel exhausted — play stop sound and end loop
                    soundLoc.getWorld().playSound(soundLoc, FIRE_STOP, SoundCategory.BLOCKS, VOLUME, 1.0f);
                    cancel();
                    activeFire.remove(key);
                    return;
                }

                // Still burning — play random fire variant
                String clip = FIRE_LOOPS[random.nextInt(FIRE_LOOPS.length)];
                soundLoc.getWorld().playSound(soundLoc, clip, SoundCategory.BLOCKS, VOLUME, 1.0f);
            }
        }.runTaskTimer(plugin, FIRE_LOOP_TICKS, FIRE_LOOP_TICKS);

        activeFire.put(key, task);
    }

    private void stopFireLoop(String key) {
        BukkitTask t = activeFire.remove(key);
        if (t != null) t.cancel();
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}

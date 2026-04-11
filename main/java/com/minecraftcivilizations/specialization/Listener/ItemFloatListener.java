package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Causes dropped items in water to float back up to the surface.
 * Items may sink at most MAX_SINK_DEPTH blocks below the water surface
 * before an upward velocity is applied to return them to the top.
 *
 * Registration: new ItemFloatListener(this); in Specialization#onEnable
 */
public class ItemFloatListener {

    // How many blocks below the water surface an item is allowed to sink
    private static final int MAX_SINK_DEPTH = 1;

    // Upward velocity applied when an item exceeds MAX_SINK_DEPTH
    private static final double RISE_VELOCITY = 0.15;

    // How often (in ticks) to check items. 4 ticks = ~200ms, good balance of smoothness and cost.
    private static final long CHECK_INTERVAL_TICKS = 4L;

    // Only process items within this many blocks of at least one player
    private static final double PLAYER_PROXIMITY_RADIUS = 100.0;
    private static final double PLAYER_PROXIMITY_RADIUS_SQUARED = PLAYER_PROXIMITY_RADIUS * PLAYER_PROXIMITY_RADIUS;

    // Maximum blocks to scan upward when looking for the water surface
    private static final int MAX_SURFACE_SCAN_DEPTH = 32;

    public ItemFloatListener(Specialization plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    // getEntitiesByClass filters at the server level — avoids iterating mobs,
                    // players, and other non-item entities entirely.
                    // Cache the player list once per world so tickItem doesn't re-allocate
                    // it for every water item it processes.
                    var players = world.getPlayers();
                    // Skip worlds with no players — no point scanning items nobody can see
                    if (players.isEmpty()) continue;
                    // Snapshot player locations once per tick so the stream inside tickItem
                    // doesn't call getLocation() fresh for every water item it checks
                    List<Location> playerLocations = new ArrayList<>(players.size());
                    for (Player p : players) playerLocations.add(p.getLocation());
                    for (Item item : world.getEntitiesByClass(Item.class)) {
                        tickItem(item, world, playerLocations);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, CHECK_INTERVAL_TICKS);
    }

    private void tickItem(Item item, World world, List<Location> playerLocations) {
        Location loc = item.getLocation();
        Block block = loc.getBlock();

        // Only act on items that are inside a water block
        if (block.getType() != Material.WATER) return;

        // Skip items with no nearby players — avoids unnecessary processing in unloaded/empty areas
        boolean playerNearby = playerLocations.stream()
                .anyMatch(p -> p.distanceSquared(loc) <= PLAYER_PROXIMITY_RADIUS_SQUARED);
        if (!playerNearby) return;

        int surfaceY = findWaterSurface(loc, world);
        // Use block Y for an integer comparison — findWaterSurface always returns a whole number
        int depth = surfaceY - loc.getBlockY();

        // getVelocity() returns a copy, so mutating vel directly is safe
        Vector vel = item.getVelocity();

        if (depth > MAX_SINK_DEPTH) {
            // Item has sunk too deep — push it upward
            item.setVelocity(vel.setY(RISE_VELOCITY));
        } else {
            // Item is within the allowed depth window — cancel any downward drift
            // so it gently bobs at the surface rather than continuing to sink
            if (vel.getY() < 0) {
                item.setVelocity(vel.setY(0.0));
            }
        }
    }

    /**
     * Scans upward from the item's position to find the Y coordinate
     * of the first non-water block (i.e., the water surface).
     */
    private int findWaterSurface(Location loc, World world) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = loc.getBlockY();
        // Compute both upper bounds once — neither changes during the scan
        int maxY = Math.min(startY + MAX_SURFACE_SCAN_DEPTH, world.getMaxHeight());
        int y = startY;

        while (y < maxY && world.getBlockAt(x, y, z).getType() == Material.WATER) {
            y++;
        }

        // y is now the first non-water block, which is the surface level
        return y;
    }
}

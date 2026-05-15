package com.minecraftcivilizations.specialization.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.concurrent.ThreadLocalRandom;

public class WorldUtils {


    public static Location createRandomLocationInChunk(Location reference) {
        Chunk chunk = reference.getChunk();
        World world = reference.getWorld();

        // chunk.getX()/getZ() are chunk coordinates; convert to block coords
        int base_x = chunk.getX() << 4; // chunkX * 16
        int base_z = chunk.getZ() << 4; // chunkZ * 16

        int random_x = base_x + ThreadLocalRandom.current().nextInt(16);
        int random_z = base_z + ThreadLocalRandom.current().nextInt(16);

        // center in the block to avoid spawning exactly on block corners
        double dx = random_x + 0.5;
        double dz = random_z + 0.5;

        // Use a safe initial Y. Many worlds have different min/max heights; use reference Y as fallback.
        int start_y = Math.max(1, Math.min(world.getMaxHeight() - 1, reference.getBlockY()));

        return new Location(world, dx, start_y, dz);
    }

    public static Location getHighestNonsolidBlockLocation(Location l) {
        World world = l.getWorld();
        if (world == null) return l;

        int x = l.getBlockX();
        int z = l.getBlockZ();
        int y = world.getMaxHeight() - 1;

        while (y > world.getMinHeight()) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                return new Location(world, x + 0.5, y, z + 0.5); // 1 block above surface
            }
            y--;
        }
        return new Location(world, x + 0.5, world.getMinHeight(), z + 0.5);
    }

    /**
     *
     */
    public static Location getNextSafeVerticalPosition(Location l) {
        World world = l.getWorld();
        if (world == null) return l;

        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();

        int max_y = world.getMaxHeight() - 2; // need room for feet + head

        // climb upward until a safe 2-block space is found
        while (y <= max_y) {
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (isPassable(feet) && isPassable(head)) {
                // center in block
                return new Location(world, x + 0.5, y, z + 0.5);
            }

            y++;
        }

        // fallback: put at world max height if nothing works
        return new Location(world, x + 0.5, world.getMaxHeight() - 1, z + 0.5);
    }

    private static boolean isPassable(Block b) {
        // allow non-solid blocks: air, plants, water, etc.
        try {
            return !b.getType().isSolid();
        } catch (Throwable ex) {
            // fallback for any version weirdness
            return b.getType().name().equals("AIR");
        }
    }



}

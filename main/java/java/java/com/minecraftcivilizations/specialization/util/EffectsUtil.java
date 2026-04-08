package com.minecraftcivilizations.specialization.util;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class EffectsUtil {

    public static void playBlockBoundingBox(Player player, Block block, Particle particle, double step) {
        BoundingBox box;

        // --- Handle beds specially ---
        block.getType();
        if (block.getBlockData() instanceof Bed bedData) {
            Block otherPart = getOtherBedPart(block, bedData);
            if (otherPart != null) {
                box = block.getBoundingBox().union(otherPart.getBoundingBox());
            } else {
                box = block.getBoundingBox();
            }
        } else {
            box = block.getBoundingBox();
        }

        Vector min = box.getMin();
        Vector max = box.getMax();

        // Edge vertices
        Vector[] starts = {
                new Vector(min.getX(), min.getY(), min.getZ()),
                new Vector(min.getX(), min.getY(), max.getZ()),
                new Vector(max.getX(), min.getY(), min.getZ()),
                new Vector(max.getX(), min.getY(), max.getZ()),
                new Vector(min.getX(), max.getY(), min.getZ()),
                new Vector(min.getX(), max.getY(), max.getZ()),
                new Vector(max.getX(), max.getY(), min.getZ()),
                new Vector(max.getX(), max.getY(), max.getZ())
        };

        int[][] edges = {
                {0, 1}, {0, 2}, {1, 3}, {2, 3},
                {4, 5}, {4, 6}, {5, 7}, {6, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        for (int[] edge : edges) {
            Vector start = starts[edge[0]];
            Vector end = starts[edge[1]];
            Vector diff = end.clone().subtract(start);
            double length = diff.length();
            Vector stepVec = diff.clone().normalize().multiply(step);

            for (double d = 0; d <= length; d += step) {
                Vector point = start.clone().add(stepVec.clone().multiply(d / step));
                player.spawnParticle(particle, point.getX(), point.getY(), point.getZ(), 1, 0, 0, 0, 0);
            }
        }
    }

    private static Block getOtherBedPart(Block block, Bed bedData) {
        Block other = block.getRelative(bedData.getFacing(), bedData.getPart() == Bed.Part.HEAD ? -1 : 1);
        if (other.getType() == block.getType() && other.getBlockData() instanceof Bed) {
            return other;
        }
        return null;
    }
}

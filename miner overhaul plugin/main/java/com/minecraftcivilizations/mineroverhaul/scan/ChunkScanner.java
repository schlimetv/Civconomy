package com.minecraftcivilizations.mineroverhaul.scan;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Per-chunk flood-fill scanner. For each subclass material, identifies
 * connected components within the 16x16xY range of one chunk and hands them
 * off as {@link OpenComponent}s to the {@link OpenClusterTable} for
 * cross-chunk stitching. Component finalization (outline computation,
 * Cluster construction, persistence, optional debug armor stand) happens
 * later at seal time inside the table's registered handler.
 */
public class ChunkScanner {

    private final MinerOverhaul plugin;

    public ChunkScanner(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    public void scanChunk(ChunkSnapshot snap, int chunkX, int chunkZ, int minY, int maxY,
                          MinerSubclass family, OpenClusterTable openTable) {
        for (Material mat : family.oreMaterials()) {
            scanMaterial(snap, chunkX, chunkZ, minY, maxY, mat, family, openTable);
        }
    }

    private void scanMaterial(ChunkSnapshot snap, int chunkX, int chunkZ, int minY, int maxY,
                              Material targetMat, MinerSubclass family, OpenClusterTable openTable) {
        int yRange = maxY - minY + 1;
        byte[] grid = new byte[16 * 16 * yRange];

        // Mark ore voxels (state 1).
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Material m;
                    try {
                        m = snap.getBlockType(x, y, z);
                    } catch (Exception e) {
                        continue;
                    }
                    if (m == targetMat) grid[indexOf(x, y, z, minY, yRange)] = 1;
                }
            }
        }

        int chunkOriginX = chunkX * 16;
        int chunkOriginZ = chunkZ * 16;

        Deque<int[]> queue = new ArrayDeque<>();
        for (int sx = 0; sx < 16; sx++) {
            for (int sz = 0; sz < 16; sz++) {
                for (int sy = minY; sy <= maxY; sy++) {
                    int idx = indexOf(sx, sy, sz, minY, yRange);
                    if (grid[idx] != 1) continue;

                    List<int[]> component = new ArrayList<>();
                    queue.clear();
                    queue.add(new int[]{sx, sy, sz});
                    grid[idx] = 2;
                    while (!queue.isEmpty()) {
                        int[] v = queue.poll();
                        component.add(new int[]{chunkOriginX + v[0], v[1], chunkOriginZ + v[2]});
                        offer(grid, queue, v[0] + 1, v[1], v[2], minY, maxY, yRange);
                        offer(grid, queue, v[0] - 1, v[1], v[2], minY, maxY, yRange);
                        offer(grid, queue, v[0], v[1], v[2] + 1, minY, maxY, yRange);
                        offer(grid, queue, v[0], v[1], v[2] - 1, minY, maxY, yRange);
                        offer(grid, queue, v[0], v[1] + 1, v[2], minY, maxY, yRange);
                        offer(grid, queue, v[0], v[1] - 1, v[2], minY, maxY, yRange);
                    }

                    // Don't filter by min-cluster-size here — small per-chunk
                    // fragments may stitch into a large vein across chunks.
                    // The filter is applied at seal time.
                    openTable.addComponent(family, targetMat, chunkX, chunkZ, component);
                }
            }
        }
    }

    private static int indexOf(int x, int y, int z, int minY, int yRange) {
        return x * 16 * yRange + z * yRange + (y - minY);
    }

    private static void offer(byte[] grid, Deque<int[]> queue,
                              int x, int y, int z, int minY, int maxY, int yRange) {
        if (x < 0 || x > 15 || z < 0 || z > 15) return;
        if (y < minY || y > maxY) return;
        int idx = indexOf(x, y, z, minY, yRange);
        if (grid[idx] == 1) {
            grid[idx] = 2;
            queue.add(new int[]{x, y, z});
        }
    }
}

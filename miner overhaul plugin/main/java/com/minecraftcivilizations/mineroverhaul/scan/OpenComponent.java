package com.minecraftcivilizations.mineroverhaul.scan;

import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One per-chunk flood-fill result. Holds the component's voxels in world
 * coordinates plus an indexed subset of those voxels that lie on chunk
 * boundaries (used to quickly test for cross-chunk adjacency to a neighbor
 * chunk's components).
 */
public class OpenComponent {

    /**
     * Pack a world (x,y,z) into a single long. 20 bits per axis is more than
     * enough for ±524288 world coords; y is offset by 1024 so it stays
     * non-negative for values down to y=-64.
     */
    public static long packWorld(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40)
             | ((long)((y + 1024) & 0xFFFFF) << 20)
             | (long)(z & 0xFFFFF);
    }

    public final long id;
    public final Material material;
    public final MinerSubclass family;
    public final int chunkX;
    public final int chunkZ;
    public final List<int[]> voxels = new ArrayList<>();
    public final Set<Long> edgeVoxels = new HashSet<>();

    public OpenComponent(long id, Material material, MinerSubclass family, int chunkX, int chunkZ) {
        this.id = id;
        this.material = material;
        this.family = family;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public void addVoxel(int wx, int wy, int wz) {
        voxels.add(new int[]{wx, wy, wz});
        int lx = wx - chunkX * 16;
        int lz = wz - chunkZ * 16;
        if (lx == 0 || lx == 15 || lz == 0 || lz == 15) {
            edgeVoxels.add(packWorld(wx, wy, wz));
        }
    }
}

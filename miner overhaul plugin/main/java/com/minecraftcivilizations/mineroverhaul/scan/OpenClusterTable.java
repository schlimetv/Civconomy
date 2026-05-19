package com.minecraftcivilizations.mineroverhaul.scan;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-flight component table for cross-chunk stitching. Each
 * {@link OpenComponent} is kept alive until every chunk it touches has all
 * 8 chunk neighbors scanned (i.e. the cluster cannot grow further); at that
 * point the registered {@link ClusterSealHandler} is invoked with the full
 * aggregated voxel list.
 *
 * Thread-safety: a single intrinsic lock guards all mutations; callers may
 * invoke from any thread.
 */
public class OpenClusterTable {

    private final MinerOverhaul plugin;
    private final AtomicLong nextId = new AtomicLong();

    private final Map<Long, OpenComponent> componentsById = new HashMap<>();
    private final Map<Long, List<OpenComponent>> componentsByChunk = new HashMap<>();
    private final Map<Long, Long> ufParent = new HashMap<>();
    private final Map<Long, Set<Long>> ufRootMembers = new HashMap<>();
    private final Set<Long> scannedChunks = new HashSet<>();

    private final Object lock = new Object();
    private ClusterSealHandler sealHandler;

    public OpenClusterTable(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    public void setSealHandler(ClusterSealHandler handler) {
        this.sealHandler = handler;
    }

    public int openComponentCount() {
        synchronized (lock) {
            return componentsById.size();
        }
    }

    /**
     * Hand in a freshly flood-filled component for a chunk. Voxels are in
     * world coordinates. The component may merge with neighbor components
     * across chunk boundaries.
     */
    public void addComponent(MinerSubclass family, Material material,
                             int chunkX, int chunkZ, List<int[]> componentVoxels) {
        synchronized (lock) {
            OpenComponent comp = new OpenComponent(
                    nextId.getAndIncrement(), material, family, chunkX, chunkZ);
            for (int[] v : componentVoxels) {
                comp.addVoxel(v[0], v[1], v[2]);
            }
            componentsById.put(comp.id, comp);
            componentsByChunk
                    .computeIfAbsent(packChunk(chunkX, chunkZ), k -> new ArrayList<>())
                    .add(comp);
            ufAdd(comp.id);

            // Attempt merges with components in the 8 neighbor chunks of same family + material.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    long neighborKey = packChunk(chunkX + dx, chunkZ + dz);
                    List<OpenComponent> neighbors = componentsByChunk.get(neighborKey);
                    if (neighbors == null) continue;
                    for (OpenComponent nc : neighbors) {
                        if (nc.material != material || nc.family != family) continue;
                        if (touchesAcrossBoundary(comp, nc)) {
                            ufUnion(comp.id, nc.id);
                        }
                    }
                }
            }
        }
    }

    /**
     * Notify the table that a chunk's scan is complete. May trigger sealing
     * of any clusters whose full neighborhood has now been scanned.
     */
    public void markChunkScanned(int chunkX, int chunkZ) {
        synchronized (lock) {
            scannedChunks.add(packChunk(chunkX, chunkZ));

            // Candidates: clusters that touch this chunk or its 8 neighbors
            // are the only ones whose seal-readiness could have changed.
            Set<Long> rootsToCheck = new HashSet<>();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    List<OpenComponent> comps = componentsByChunk.get(packChunk(chunkX + dx, chunkZ + dz));
                    if (comps == null) continue;
                    for (OpenComponent c : comps) rootsToCheck.add(ufFind(c.id));
                }
            }
            for (long root : rootsToCheck) trySealRoot(root);
        }
    }

    /**
     * Force-seal every open cluster regardless of neighbor state. Use on
     * shutdown to avoid losing in-flight components.
     */
    public void flushAll() {
        synchronized (lock) {
            Set<Long> roots = new HashSet<>(ufRootMembers.keySet());
            for (long root : roots) sealRoot(root);
        }
    }

    private void trySealRoot(long root) {
        Set<Long> members = ufRootMembers.get(root);
        if (members == null || members.isEmpty()) return;

        // Collect every chunk touched by this cluster.
        Set<Long> chunksTouched = new HashSet<>();
        for (long memberId : members) {
            OpenComponent c = componentsById.get(memberId);
            if (c != null) chunksTouched.add(packChunk(c.chunkX, c.chunkZ));
        }

        // Cluster is sealable only when every touched chunk has all 8
        // neighbors scanned — otherwise it might still grow.
        for (long chunk : chunksTouched) {
            int cx = unpackChunkX(chunk);
            int cz = unpackChunkZ(chunk);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    if (!scannedChunks.contains(packChunk(cx + dx, cz + dz))) return;
                }
            }
        }
        sealRoot(root);
    }

    private void sealRoot(long root) {
        Set<Long> members = ufRootMembers.remove(root);
        if (members == null || members.isEmpty()) return;

        List<int[]> allVoxels = new ArrayList<>();
        Material primaryMaterial = null;
        MinerSubclass family = null;
        for (long id : members) {
            OpenComponent c = componentsById.remove(id);
            if (c == null) continue;
            if (primaryMaterial == null) primaryMaterial = c.material;
            if (family == null) family = c.family;
            allVoxels.addAll(c.voxels);
            long chunkKey = packChunk(c.chunkX, c.chunkZ);
            List<OpenComponent> list = componentsByChunk.get(chunkKey);
            if (list != null) {
                list.remove(c);
                if (list.isEmpty()) componentsByChunk.remove(chunkKey);
            }
            ufParent.remove(id);
        }

        if (sealHandler != null && primaryMaterial != null && family != null && !allVoxels.isEmpty()) {
            sealHandler.onSeal(family, primaryMaterial, allVoxels);
        }
    }

    private boolean touchesAcrossBoundary(OpenComponent a, OpenComponent b) {
        OpenComponent small = a.edgeVoxels.size() < b.edgeVoxels.size() ? a : b;
        OpenComponent large = (small == a) ? b : a;
        for (long packed : small.edgeVoxels) {
            int x = (int) ((packed >>> 40) & 0xFFFFF);
            if ((x & 0x80000) != 0) x |= 0xFFF00000;
            int y = (int) ((packed >>> 20) & 0xFFFFF) - 1024;
            int z = (int) (packed & 0xFFFFF);
            if ((z & 0x80000) != 0) z |= 0xFFF00000;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x + 1, y, z))) return true;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x - 1, y, z))) return true;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x, y, z + 1))) return true;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x, y, z - 1))) return true;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x, y + 1, z))) return true;
            if (large.edgeVoxels.contains(OpenComponent.packWorld(x, y - 1, z))) return true;
        }
        return false;
    }

    // ── Union-find with explicit member-set tracking ────────────────────

    private void ufAdd(long id) {
        ufParent.put(id, id);
        Set<Long> singleton = new HashSet<>();
        singleton.add(id);
        ufRootMembers.put(id, singleton);
    }

    private long ufFind(long id) {
        long cur = id;
        while (ufParent.get(cur) != cur) cur = ufParent.get(cur);
        // Path-compression pass.
        long root = cur;
        cur = id;
        while (ufParent.get(cur) != cur) {
            long next = ufParent.get(cur);
            ufParent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private void ufUnion(long a, long b) {
        long ra = ufFind(a);
        long rb = ufFind(b);
        if (ra == rb) return;
        Set<Long> sa = ufRootMembers.get(ra);
        Set<Long> sb = ufRootMembers.get(rb);
        if (sa.size() < sb.size()) {
            sb.addAll(sa);
            ufParent.put(ra, rb);
            ufRootMembers.remove(ra);
        } else {
            sa.addAll(sb);
            ufParent.put(rb, ra);
            ufRootMembers.remove(rb);
        }
    }

    private static long packChunk(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) { return (int) (packed >> 32); }
    private static int unpackChunkZ(long packed) { return (int) (packed & 0xFFFFFFFFL); }

    public interface ClusterSealHandler {
        void onSeal(MinerSubclass family, Material primaryMaterial, List<int[]> worldVoxels);
    }
}

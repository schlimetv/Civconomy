package com.minecraftcivilizations.mineroverhaul.scan;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.Material;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Disk-backed cluster store keyed by (regionX, regionZ, family). Region files at
 *   plugins/MinerOverhaul/clusters/r.<rx>.<rz>.<family>.bin
 *
 * Format (DataOutputStream big-endian):
 *   int  magic  = 'MINP' (0x4D494E50)
 *   byte version = 1
 *   short materialTableLen
 *   UTF[]  materialTable        (Material.name())
 *   int  clusterCount
 *   per cluster:
 *     short materialIdx
 *     int   totalSize
 *     int   outlineCount
 *     int[] outlinePacked
 */
public class OreClusterCache {

    private static final int MAGIC = 0x4D494E50;
    private static final byte VERSION = 1;

    private final MinerOverhaul plugin;
    private final Path clustersDir;
    private final int cacheCapacity;

    private final LinkedHashMap<RegionKey, RegionData> resident;
    private final Object lock = new Object();

    public OreClusterCache(MinerOverhaul plugin, int cacheCapacity) {
        this.plugin = plugin;
        this.cacheCapacity = cacheCapacity;
        // Per-world clusters subdir so a fresh world doesn't inherit stale
        // cluster data from a previous world that lived at the same coords.
        String worldName = org.bukkit.Bukkit.getWorlds().isEmpty()
                ? "default"
                : org.bukkit.Bukkit.getWorlds().get(0).getName();
        this.clustersDir = plugin.getDataFolder().toPath()
                .resolve("clusters").resolve(worldName);
        try {
            Files.createDirectories(clustersDir);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create clusters dir: " + e.getMessage());
        }
        this.resident = new LinkedHashMap<>(cacheCapacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<RegionKey, RegionData> eldest) {
                if (size() > cacheCapacity) {
                    if (eldest.getValue().dirty) {
                        try {
                            writeRegion(eldest.getKey(), eldest.getValue());
                        } catch (IOException ex) {
                            plugin.getLogger().warning("Failed to flush region on eviction: " + ex.getMessage());
                        }
                    }
                    return true;
                }
                return false;
            }
        };
    }

    /** Return all clusters for a given family whose xz bbox intersects the search square. */
    public List<Cluster> findCandidates(MinerSubclass family, double worldX, double worldZ, int radiusXz) {
        int qMinX = (int) Math.floor(worldX) - radiusXz;
        int qMaxX = (int) Math.floor(worldX) + radiusXz;
        int qMinZ = (int) Math.floor(worldZ) - radiusXz;
        int qMaxZ = (int) Math.floor(worldZ) + radiusXz;

        int rxLo = Math.floorDiv(qMinX, Cluster.REGION_SIZE);
        int rxHi = Math.floorDiv(qMaxX, Cluster.REGION_SIZE);
        int rzLo = Math.floorDiv(qMinZ, Cluster.REGION_SIZE);
        int rzHi = Math.floorDiv(qMaxZ, Cluster.REGION_SIZE);

        List<Cluster> out = new ArrayList<>();
        for (int rx = rxLo; rx <= rxHi; rx++) {
            for (int rz = rzLo; rz <= rzHi; rz++) {
                RegionData data = loadOrCreate(new RegionKey(rx, rz, family));
                for (Cluster c : data.clusters) {
                    if (c.bboxIntersectsXz(qMinX, qMinZ, qMaxX, qMaxZ)) {
                        out.add(c);
                    }
                }
            }
        }
        return out;
    }

    public void addCluster(MinerSubclass family, Cluster cluster) {
        RegionKey key = new RegionKey(cluster.regionX(), cluster.regionZ(), family);
        synchronized (lock) {
            RegionData data = loadOrCreate(key);
            data.clusters.add(cluster);
            data.dirty = true;
        }
    }

    public int regionsResident() {
        synchronized (lock) {
            return resident.size();
        }
    }

    public int totalClustersResident() {
        synchronized (lock) {
            int total = 0;
            for (RegionData rd : resident.values()) total += rd.clusters.size();
            return total;
        }
    }

    public void flushAll() {
        synchronized (lock) {
            for (Map.Entry<RegionKey, RegionData> e : resident.entrySet()) {
                if (e.getValue().dirty) {
                    try {
                        writeRegion(e.getKey(), e.getValue());
                        e.getValue().dirty = false;
                    } catch (IOException ex) {
                        plugin.getLogger().warning("Failed to flush region: " + ex.getMessage());
                    }
                }
            }
        }
    }

    public void clearAll() {
        synchronized (lock) {
            resident.clear();
            try {
                if (Files.exists(clustersDir)) {
                    try (var stream = Files.list(clustersDir)) {
                        stream.filter(p -> p.getFileName().toString().endsWith(".bin"))
                              .forEach(p -> {
                                  try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                              });
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to clear cluster files: " + e.getMessage());
            }
        }
    }

    private RegionData loadOrCreate(RegionKey key) {
        synchronized (lock) {
            RegionData existing = resident.get(key);
            if (existing != null) return existing;
            RegionData loaded;
            try {
                loaded = readRegion(key);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to read region " + key + ": " + e.getMessage());
                loaded = new RegionData();
            }
            resident.put(key, loaded);
            return loaded;
        }
    }

    private Path pathFor(RegionKey key) {
        return clustersDir.resolve("r." + key.rx + "." + key.rz + "." + key.family.lowerName() + ".bin");
    }

    private RegionData readRegion(RegionKey key) throws IOException {
        Path file = pathFor(key);
        if (!Files.exists(file)) return new RegionData();
        try (InputStream is = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(is)) {
            int magic = in.readInt();
            if (magic != MAGIC) throw new IOException("Bad magic in " + file);
            byte version = in.readByte();
            if (version != VERSION) throw new IOException("Unsupported version " + version + " in " + file);
            int tableLen = in.readShort() & 0xFFFF;
            String[] materials = new String[tableLen];
            for (int i = 0; i < tableLen; i++) materials[i] = in.readUTF();
            int clusterCount = in.readInt();
            RegionData data = new RegionData();
            for (int i = 0; i < clusterCount; i++) {
                int matIdx = in.readShort() & 0xFFFF;
                int totalSize = in.readInt();
                int outlineCount = in.readInt();
                int[] outline = new int[outlineCount];
                for (int j = 0; j < outlineCount; j++) outline[j] = in.readInt();
                Material mat = Material.matchMaterial(materials[matIdx]);
                if (mat == null) continue; // unknown material — skip
                data.clusters.add(new Cluster(mat, totalSize, outline, key.rx, key.rz));
            }
            return data;
        }
    }

    private void writeRegion(RegionKey key, RegionData data) throws IOException {
        Path file = pathFor(key);
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        // Build material table
        List<String> materials = new ArrayList<>();
        Map<Material, Integer> idx = new java.util.HashMap<>();
        for (Cluster c : data.clusters) {
            if (!idx.containsKey(c.primaryMaterial())) {
                idx.put(c.primaryMaterial(), materials.size());
                materials.add(c.primaryMaterial().name());
            }
        }
        try (OutputStream os = Files.newOutputStream(tmp);
             DataOutputStream out = new DataOutputStream(os)) {
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            out.writeShort(materials.size());
            for (String name : materials) out.writeUTF(name);
            out.writeInt(data.clusters.size());
            for (Cluster c : data.clusters) {
                out.writeShort(idx.get(c.primaryMaterial()));
                out.writeInt(c.totalSize());
                out.writeInt(c.outlinePacked().length);
                for (int p : c.outlinePacked()) out.writeInt(p);
            }
        }
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private static final class RegionKey {
        final int rx, rz;
        final MinerSubclass family;
        RegionKey(int rx, int rz, MinerSubclass family) { this.rx = rx; this.rz = rz; this.family = family; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof RegionKey k)) return false;
            return rx == k.rx && rz == k.rz && family == k.family;
        }
        @Override public int hashCode() { return rx * 31 + rz * 17 + family.hashCode(); }
        @Override public String toString() { return "r." + rx + "." + rz + "." + family.lowerName(); }
    }

    private static final class RegionData {
        final List<Cluster> clusters = Collections.synchronizedList(new ArrayList<>());
        volatile boolean dirty = false;
    }
}

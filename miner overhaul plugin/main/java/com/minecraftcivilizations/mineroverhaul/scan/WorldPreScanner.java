package com.minecraftcivilizations.mineroverhaul.scan;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import com.minecraftcivilizations.mineroverhaul.subclass.OreNaming;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WorldPreScanner {

    private final MinerOverhaul plugin;
    private final ChunkScanner chunkScanner;
    private final ScanProgress progress;
    private final OpenClusterTable openTable;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastCheckpoint = new AtomicLong(0);
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger clustersFound = new AtomicInteger(0);

    private BukkitTask tickerTask;
    private SpiralIterator spiral;
    private int radiusChunks;
    private World targetWorld;

    public WorldPreScanner(MinerOverhaul plugin, ChunkScanner chunkScanner,
                           ScanProgress progress, OpenClusterTable openTable) {
        this.plugin = plugin;
        this.chunkScanner = chunkScanner;
        this.progress = progress;
        this.openTable = openTable;
        this.openTable.setSealHandler(this::onClusterSealed);
    }

    public ScanProgress progress() { return progress; }
    public int clustersFound() { return clustersFound.get(); }
    public int openComponentCount() { return openTable.openComponentCount(); }
    public boolean isRunning() { return running.get(); }
    public int radiusChunks() { return radiusChunks; }

    public synchronized void start() {
        if (running.get()) return;
        targetWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (targetWorld == null) {
            plugin.getLogger().warning("No worlds available for scan start.");
            return;
        }
        radiusChunks = (int) Math.ceil(plugin.getMinerConfig().scanRadiusBlocks() / 16.0);
        spiral = new SpiralIterator(radiusChunks);
        running.set(true);
        plugin.getLogger().info("MinerOverhaul scan started: radius " + radiusChunks
                + " chunks, " + progress.scannedCount() + " chunks already scanned.");
        scheduleTicker();
    }

    public synchronized void stop() {
        if (!running.get()) return;
        running.set(false);
        if (tickerTask != null) { tickerTask.cancel(); tickerTask = null; }
        // Force-seal any in-flight clusters so they're not lost across restarts.
        openTable.flushAll();
        progress.save();
        plugin.getClusterCache().flushAll();
        plugin.getLogger().info("MinerOverhaul scan stopped. Chunks scanned: "
                + progress.scannedCount() + " Sealed clusters: " + clustersFound.get());
    }

    public synchronized void resetAndRestart() {
        stop();
        progress.clear();
        plugin.getClusterCache().clearAll();
        clustersFound.set(0);
        start();
    }

    private void scheduleTicker() {
        long periodTicks = Math.max(1, plugin.getMinerConfig().scanTickPeriodMs() / 50);
        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, periodTicks);
    }

    private void tick() {
        if (!running.get() || spiral == null) return;
        int budget = plugin.getMinerConfig().scanChunksPerTick();
        if (inFlight.get() > budget * 4) {
            checkpointMaybe();
            return;
        }

        int issued = 0;
        while (issued < budget && spiral.hasNext()) {
            int[] xz = spiral.next();
            int cx = xz[0], cz = xz[1];
            if (progress.isScanned(cx, cz)) continue;
            requestAndScan(cx, cz);
            issued++;
        }

        if (!spiral.hasNext() && inFlight.get() == 0) {
            plugin.getLogger().info("MinerOverhaul scan complete: " + progress.scannedCount()
                    + " chunks, " + clustersFound.get() + " sealed clusters.");
            stop();
            return;
        }
        checkpointMaybe();
    }

    private void checkpointMaybe() {
        long now = System.currentTimeMillis();
        if (now - lastCheckpoint.get() >= 30_000L) {
            lastCheckpoint.set(now);
            progress.save();
            plugin.getClusterCache().flushAll();
        }
    }

    private void requestAndScan(int cx, int cz) {
        if (targetWorld == null) return;
        CompletableFuture<Chunk> fut = targetWorld.getChunkAtAsync(cx, cz, false);
        inFlight.incrementAndGet();
        fut.whenComplete((chunk, err) -> {
            try {
                if (err != null || chunk == null) {
                    progress.markScanned(cx, cz);
                    openTable.markChunkScanned(cx, cz);
                    return;
                }
                ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
                int minY = targetWorld.getMinHeight();
                int maxY = targetWorld.getMaxHeight() - 1;
                Bukkit.getAsyncScheduler().runNow(plugin, task -> {
                    try {
                        for (MinerSubclass family : MinerSubclass.values()) {
                            chunkScanner.scanChunk(snap, cx, cz, minY, maxY, family, openTable);
                        }
                        progress.markScanned(cx, cz);
                        openTable.markChunkScanned(cx, cz);
                    } catch (Throwable t) {
                        plugin.getLogger().warning("Chunk scan failed (" + cx + "," + cz + "): " + t);
                    }
                });
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    public void scanFreshChunk(Chunk chunk) {
        if (chunk == null) return;
        int cx = chunk.getX(), cz = chunk.getZ();
        if (Math.abs(cx) > radiusChunks || Math.abs(cz) > radiusChunks) return;
        if (progress.isScanned(cx, cz)) return;
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight() - 1;
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                for (MinerSubclass family : MinerSubclass.values()) {
                    chunkScanner.scanChunk(snap, cx, cz, minY, maxY, family, openTable);
                }
                progress.markScanned(cx, cz);
                openTable.markChunkScanned(cx, cz);
            } catch (Throwable t) {
                plugin.getLogger().warning("Fresh chunk scan failed (" + cx + "," + cz + "): " + t);
            }
        });
    }

    /**
     * Called from {@link OpenClusterTable} when a cluster's full neighborhood
     * is scanned (or on shutdown via flushAll). Drops too-small clusters,
     * computes the outline across all stitched voxels, splits by region into
     * one {@link Cluster} each, persists, and optionally drops a debug
     * armor stand at the centroid.
     */
    private void onClusterSealed(MinerSubclass family, Material material, List<int[]> worldVoxels) {
        int totalSize = worldVoxels.size();
        int minClusterSize = plugin.getMinerConfig().scanMinClusterSize();
        if (totalSize < minClusterSize) return;

        // Outline = any voxel with at least one non-ore 6-neighbor.
        Set<Long> voxelSet = new HashSet<>();
        for (int[] v : worldVoxels) voxelSet.add(OpenComponent.packWorld(v[0], v[1], v[2]));

        // Group voxels by the region they belong to. One Cluster per region.
        Map<Long, List<int[]>> regionsToOutline = new HashMap<>();
        for (int[] v : worldVoxels) {
            int x = v[0], y = v[1], z = v[2];
            boolean isOutline =
                    !voxelSet.contains(OpenComponent.packWorld(x + 1, y, z)) ||
                    !voxelSet.contains(OpenComponent.packWorld(x - 1, y, z)) ||
                    !voxelSet.contains(OpenComponent.packWorld(x, y + 1, z)) ||
                    !voxelSet.contains(OpenComponent.packWorld(x, y - 1, z)) ||
                    !voxelSet.contains(OpenComponent.packWorld(x, y, z + 1)) ||
                    !voxelSet.contains(OpenComponent.packWorld(x, y, z - 1));
            if (!isOutline) continue;
            int rx = Math.floorDiv(x, Cluster.REGION_SIZE);
            int rz = Math.floorDiv(z, Cluster.REGION_SIZE);
            long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
            regionsToOutline.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
        }
        if (regionsToOutline.isEmpty()) {
            // Fully enclosed cluster (no detectable surface) — fall back to using all voxels grouped by region.
            for (int[] v : worldVoxels) {
                int rx = Math.floorDiv(v[0], Cluster.REGION_SIZE);
                int rz = Math.floorDiv(v[2], Cluster.REGION_SIZE);
                long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
                regionsToOutline.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
            }
        }

        OreClusterCache cache = plugin.getClusterCache();
        for (Map.Entry<Long, List<int[]>> e : regionsToOutline.entrySet()) {
            long rkey = e.getKey();
            int rx = (int) (rkey >> 32);
            int rz = (int) (rkey & 0xFFFFFFFFL);
            int rOriginX = rx * Cluster.REGION_SIZE;
            int rOriginZ = rz * Cluster.REGION_SIZE;
            List<int[]> outlineList = e.getValue();
            int[] packed = new int[outlineList.size()];
            for (int i = 0; i < outlineList.size(); i++) {
                int[] v = outlineList.get(i);
                packed[i] = Cluster.pack(v[0] - rOriginX, v[1], v[2] - rOriginZ);
            }
            Cluster cluster = new Cluster(material, totalSize, packed, rx, rz);
            cache.addCluster(family, cluster);
        }
        clustersFound.incrementAndGet();

        // Optional debug marker at centroid.
        if (plugin.getMinerConfig().debugSpawnArmorStands()) {
            long sx = 0, sy = 0, sz = 0;
            for (int[] v : worldVoxels) { sx += v[0]; sy += v[1]; sz += v[2]; }
            double n = worldVoxels.size();
            double wx = sx / n + 0.5, wy = sy / n + 0.5, wz = sz / n + 0.5;
            final World world = targetWorld;
            final Material mat = material;
            final int size = totalSize;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (world == null) return;
                Location loc = new Location(world, wx, wy, wz);
                world.spawn(loc, ArmorStand.class, stand -> {
                    stand.setMarker(true);
                    stand.setInvulnerable(true);
                    stand.setGlowing(true);
                    stand.setCustomNameVisible(true);
                    stand.customName(Component.text("[" + OreNaming.displayNameFor(mat) + "] x" + size,
                            colorForMaterial(mat)));
                    stand.setPersistent(true);
                    stand.addScoreboardTag("minerprospect_debug");
                });
            });
        }
    }

    private static NamedTextColor colorForMaterial(Material m) {
        if (m == null) return NamedTextColor.WHITE;
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> NamedTextColor.DARK_GRAY;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> NamedTextColor.GOLD;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> NamedTextColor.YELLOW;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> NamedTextColor.WHITE;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> NamedTextColor.RED;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> NamedTextColor.BLUE;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> NamedTextColor.GREEN;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> NamedTextColor.AQUA;
            default -> NamedTextColor.WHITE;
        };
    }

    /** Spiral iterator from (0,0) outward, bounded by radius chunks (inclusive). */
    static class SpiralIterator {
        private final int radius;
        private int x = 0, z = 0;
        private int dx = 0, dz = -1;
        private int stepsLeft = 1;
        private int stepSize = 1;
        private int turnsSinceGrow = 0;
        private boolean yieldedOrigin = false;
        private int yieldedCount = 0;
        private final int maxCount;

        SpiralIterator(int radius) {
            this.radius = radius;
            int side = radius * 2 + 1;
            this.maxCount = side * side;
        }

        boolean hasNext() { return yieldedCount < maxCount; }

        int[] next() {
            while (yieldedCount < maxCount) {
                if (!yieldedOrigin) {
                    yieldedOrigin = true;
                    yieldedCount++;
                    return new int[]{0, 0};
                }
                if (stepsLeft == 0) {
                    int tmp = dx; dx = -dz; dz = tmp;
                    turnsSinceGrow++;
                    if (turnsSinceGrow == 2) { stepSize++; turnsSinceGrow = 0; }
                    stepsLeft = stepSize;
                }
                x += dx; z += dz; stepsLeft--;
                if (Math.abs(x) <= radius && Math.abs(z) <= radius) {
                    yieldedCount++;
                    return new int[]{x, z};
                }
                yieldedCount++;
            }
            return null;
        }
    }
}

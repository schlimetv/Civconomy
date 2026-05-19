package com.minecraftcivilizations.mineroverhaul;

import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;

public class MinerConfig {

    private final MinerOverhaul plugin;

    private int prospectRadiusBlocks;
    private int prospectCooldownSeconds;
    private int distTraces;
    private int distSmallSample;
    private int distMediumSample;
    private int sizeTinyMax;
    private int sizeSmallMax;
    private int sizeAverageMax;
    private int sizeLargeMax;

    private int scanRadiusBlocks;
    private int scanChunksPerTick;
    private int scanTickPeriodMs;
    private int scanRegionCacheSize;
    private int scanMinClusterSize;
    private boolean scanRunOnEnable;

    private boolean debugSpawnArmorStands;

    private final Map<MinerSubclass, String> descriptions = new EnumMap<>(MinerSubclass.class);

    public MinerConfig(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        prospectRadiusBlocks    = c.getInt("prospect.radius-blocks", 100);
        prospectCooldownSeconds = c.getInt("prospect.cooldown-seconds", 5);
        distTraces              = c.getInt("prospect.distance-thresholds.traces", 75);
        distSmallSample         = c.getInt("prospect.distance-thresholds.small-sample", 40);
        distMediumSample        = c.getInt("prospect.distance-thresholds.medium-sample", 15);
        sizeTinyMax             = c.getInt("prospect.size-buckets.tiny-max", 600);
        sizeSmallMax            = c.getInt("prospect.size-buckets.small-max", 900);
        sizeAverageMax          = c.getInt("prospect.size-buckets.average-max", 1600);
        sizeLargeMax            = c.getInt("prospect.size-buckets.large-max", 2300);

        scanRadiusBlocks    = c.getInt("scan.radius-blocks", 5000);
        scanChunksPerTick   = c.getInt("scan.chunks-per-tick", 8);
        scanTickPeriodMs    = c.getInt("scan.tick-period-ms", 50);
        scanRegionCacheSize = c.getInt("scan.region-cache-size", 64);
        scanMinClusterSize  = c.getInt("scan.min-cluster-size", 20);
        scanRunOnEnable     = c.getBoolean("scan.run-on-enable", true);

        debugSpawnArmorStands = c.getBoolean("debug.spawn-armor-stands", false);

        descriptions.clear();
        for (MinerSubclass s : MinerSubclass.values()) {
            String desc = c.getString("descriptions." + s.lowerName(),
                    "Scan for " + s.displayName().toLowerCase() + " veins.");
            descriptions.put(s, desc);
        }
    }

    public int prospectRadiusBlocks()    { return prospectRadiusBlocks; }
    public int prospectCooldownSeconds() { return prospectCooldownSeconds; }
    public int distTraces()              { return distTraces; }
    public int distSmallSample()         { return distSmallSample; }
    public int distMediumSample()        { return distMediumSample; }
    public int sizeTinyMax()             { return sizeTinyMax; }
    public int sizeSmallMax()            { return sizeSmallMax; }
    public int sizeAverageMax()          { return sizeAverageMax; }
    public int sizeLargeMax()            { return sizeLargeMax; }

    public int scanRadiusBlocks()    { return scanRadiusBlocks; }
    public int scanChunksPerTick()   { return scanChunksPerTick; }
    public int scanTickPeriodMs()    { return scanTickPeriodMs; }
    public int scanRegionCacheSize() { return scanRegionCacheSize; }
    public int scanMinClusterSize()  { return scanMinClusterSize; }
    public boolean scanRunOnEnable() { return scanRunOnEnable; }

    public boolean debugSpawnArmorStands() { return debugSpawnArmorStands; }

    public String description(MinerSubclass s) {
        return descriptions.getOrDefault(s, "");
    }
}

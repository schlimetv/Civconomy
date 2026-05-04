package com.minecraftcivilizations.specialization.Distance;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class Town {

    // Serializable fields for Jackson
    @JsonProperty("centerX")
    private double centerX;
    @JsonProperty("centerY") 
    private double centerY;
    @JsonProperty("centerZ")
    private double centerZ;
    @JsonProperty("worldName")
    private String worldName;
    
    @JsonProperty("spawnLocations")
    private List<SpawnLocationData> spawnLocationData;
    
    @Getter
    @JsonProperty("discoveredTime")
    private long discoveredTime;

    // Helper class for serializable spawn location data
    public static class SpawnLocationData {
        @JsonProperty("x") public double x;
        @JsonProperty("y") public double y;
        @JsonProperty("z") public double z;
        @JsonProperty("world") public String world;
        
        @JsonCreator
        public SpawnLocationData(@JsonProperty("x") double x, 
                               @JsonProperty("y") double y, 
                               @JsonProperty("z") double z, 
                               @JsonProperty("world") String world) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }
        
        public Location toLocation() {
            return new Location(Bukkit.getWorld(world), x, y, z);
        }
        
        public static SpawnLocationData fromLocation(Location loc) {
            return new SpawnLocationData(loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
        }
    }

    @JsonCreator
    public Town(@JsonProperty("centerX") double centerX,
                @JsonProperty("centerY") double centerY,
                @JsonProperty("centerZ") double centerZ,
                @JsonProperty("worldName") String worldName,
                @JsonProperty("spawnLocations") List<SpawnLocationData> spawnLocationData,
                @JsonProperty("discoveredTime") Long discoveredTime) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.worldName = worldName;
        this.spawnLocationData = spawnLocationData != null ? spawnLocationData : new ArrayList<>();
        this.discoveredTime = discoveredTime != null ? discoveredTime : System.currentTimeMillis();
    }

    // Constructor for creating from Location objects (used by TownManager)
    public Town(Location center, List<Location> spawns) {
        this.centerX = center.getX();
        this.centerY = center.getY();
        this.centerZ = center.getZ();
        this.worldName = center.getWorld().getName();
        this.spawnLocationData = spawns.stream()
                .map(SpawnLocationData::fromLocation)
                .toList();
        this.discoveredTime = System.currentTimeMillis();
    }

    public Location getCenterLocation() {
        return new Location(Bukkit.getWorld(worldName), centerX, centerY, centerZ);
    }

    public List<Location> getSpawnLocations() {
        return spawnLocationData.stream()
                .map(SpawnLocationData::toLocation)
                .toList();
    }

    public int getSpawnCount() {
        return spawnLocationData.size();
    }

    // Legacy method for compatibility with existing analytics code
    @Deprecated
    public int getBedCount() {
        return spawnLocationData.size();
    }

    // Legacy method for compatibility with existing analytics code
    @Deprecated
    public List<Location> getBedLocations() {
        return getSpawnLocations();
    }

    public void updateSpawns(List<Location> newSpawns) {
        this.spawnLocationData = newSpawns.stream()
                .map(SpawnLocationData::fromLocation)
                .toList();
        // Recalculate center if needed
        if (!newSpawns.isEmpty()) {
            Location newCenter = calculateCenter(newSpawns);
            this.centerX = newCenter.getX();
            this.centerY = newCenter.getY();
            this.centerZ = newCenter.getZ();
            this.worldName = newCenter.getWorld().getName();
        }
    }

    // Legacy method for compatibility
    @Deprecated
    public void updateBeds(List<Location> newBeds) {
        updateSpawns(newBeds);
    }

    private Location calculateCenter(List<Location> locations) {
        if (locations.isEmpty()) return null;

        double totalX = 0, totalY = 0, totalZ = 0;
        org.bukkit.World world = locations.get(0).getWorld();

        for (Location loc : locations) {
            totalX += loc.getX();
            totalY += loc.getY();
            totalZ += loc.getZ();
        }

        return new Location(world,
                totalX / locations.size(),
                totalY / locations.size(),
                totalZ / locations.size());
    }

    @Override
    public String toString() {
        return String.format("Town{center=%.1f,%.1f,%.1f in %s, spawns=%d, discovered=%d}",
                centerX, centerY, centerZ, worldName, spawnLocationData.size(), discoveredTime);
    }
}
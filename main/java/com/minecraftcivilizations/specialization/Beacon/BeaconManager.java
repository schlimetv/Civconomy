package com.minecraftcivilizations.specialization.Beacon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages beacon ownership and whitelist data.
 * All data is stored in a single JSON file keyed by location string ("world:x:y:z").
 */
public class BeaconManager {

    private final File dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Location key -> beacon data
    private final Map<String, BeaconData> beacons = new HashMap<>();

    // Per-player saved whitelist templates (separate from golem templates)
    private final Map<UUID, List<String>> savedTemplates = new HashMap<>();
    private final File templateFile;

    public BeaconManager(Specialization plugin) {
        this.dataFile = new File(plugin.getDataFolder(), "beacon_whitelists.json");
        this.templateFile = new File(plugin.getDataFolder(), "beacon_saved_templates.json");
        load();
        loadTemplates();
    }

    // =========================================================================
    //  Location Key Helpers
    // =========================================================================

    private static String toKey(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private static String toKey(Block block) {
        return toKey(block.getLocation());
    }

    // =========================================================================
    //  Ownership & Whitelist
    // =========================================================================

    public void registerBeacon(Block block, UUID ownerUuid) {
        BeaconData data = new BeaconData();
        data.owner = ownerUuid.toString();
        data.whitelist = new ArrayList<>();
        // Owner is always on their own whitelist
        var ownerPlayer = Bukkit.getPlayer(ownerUuid);
        if (ownerPlayer != null) {
            data.whitelist.add(ownerPlayer.getName());
        }
        beacons.put(toKey(block), data);
        saveToDisk();
    }

    public void removeBeacon(Block block) {
        if (beacons.remove(toKey(block)) != null) {
            saveToDisk();
        }
    }

    public boolean isRegistered(Block block) {
        return beacons.containsKey(toKey(block));
    }

    public UUID getOwner(Block block) {
        BeaconData data = beacons.get(toKey(block));
        if (data != null && data.owner != null) {
            try { return UUID.fromString(data.owner); } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public boolean isOwner(Block block, UUID playerUuid) {
        UUID owner = getOwner(block);
        return owner != null && owner.equals(playerUuid);
    }

    public List<String> getWhitelist(Block block) {
        BeaconData data = beacons.get(toKey(block));
        return data != null && data.whitelist != null ? new ArrayList<>(data.whitelist) : new ArrayList<>();
    }

    public void setWhitelist(Block block, List<String> names) {
        BeaconData data = beacons.get(toKey(block));
        if (data == null) return;
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) cleaned.add(trimmed);
        }
        data.whitelist = cleaned;
        saveToDisk();
    }

    public boolean isWhitelisted(Block block, String playerName) {
        for (String name : getWhitelist(block)) {
            if (name.equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }

    /**
     * Checks if a player is whitelisted on ANY beacon whose radius covers the given location.
     * Returns true if whitelisted (or no beacon covers the location).
     * Returns false if at least one beacon covers the location and the player is NOT whitelisted on it.
     */
    public boolean canPlaceAt(Location placeLoc, String playerName) {
        for (Map.Entry<String, BeaconData> entry : beacons.entrySet()) {
            Location beaconLoc = parseLoc(entry.getKey());
            if (beaconLoc == null) continue;
            if (!beaconLoc.getWorld().equals(placeLoc.getWorld())) continue;

            Block beaconBlock = beaconLoc.getBlock();
            if (beaconBlock.getType() != Material.BEACON) continue;

            int radius = getBeaconRadius(beaconBlock);
            if (radius <= 0) continue;

            double dist = horizontalDistance(beaconLoc, placeLoc);
            if (dist <= radius) {
                // This beacon covers the placement location — check whitelist
                boolean whitelisted = false;
                if (entry.getValue().whitelist != null) {
                    for (String name : entry.getValue().whitelist) {
                        if (name.equalsIgnoreCase(playerName)) { whitelisted = true; break; }
                    }
                }
                if (!whitelisted) return false;
            }
        }
        return true;
    }

    // =========================================================================
    //  Saved Templates
    // =========================================================================

    public void saveTemplate(UUID playerUuid, List<String> whitelist) {
        savedTemplates.put(playerUuid, new ArrayList<>(whitelist));
        saveTemplatesToDisk();
    }

    public List<String> getSavedTemplate(UUID playerUuid) {
        List<String> t = savedTemplates.get(playerUuid);
        return t != null ? new ArrayList<>(t) : null;
    }

    public boolean hasSavedTemplate(UUID playerUuid) {
        return savedTemplates.containsKey(playerUuid);
    }

    // =========================================================================
    //  Beacon Radius
    // =========================================================================

    /** Returns the effective radius for a beacon based on its pyramid tier. */
    public static int getBeaconRadius(Block beaconBlock) {
        if (beaconBlock.getType() != Material.BEACON) return 0;
        if (beaconBlock.getState() instanceof org.bukkit.block.Beacon beacon) {
            return switch (beacon.getTier()) {
                case 1 -> 20;
                case 2 -> 30;
                case 3 -> 40;
                case 4 -> 50;
                default -> 0;
            };
        }
        return 0;
    }

    // =========================================================================
    //  Persistence
    // =========================================================================

    private void load() {
        if (!dataFile.exists()) return;
        try (Reader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<Map<String, BeaconData>>(){}.getType();
            Map<String, BeaconData> raw = gson.fromJson(reader, type);
            if (raw != null) beacons.putAll(raw);
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to load beacon whitelists", e);
        }
    }

    private void saveToDisk() {
        try {
            dataFile.getParentFile().mkdirs();
            try (Writer writer = new FileWriter(dataFile)) { gson.toJson(beacons, writer); }
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to save beacon whitelists", e);
        }
    }

    private void loadTemplates() {
        if (!templateFile.exists()) return;
        try (Reader reader = new FileReader(templateFile)) {
            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
            Map<String, List<String>> raw = gson.fromJson(reader, type);
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    try { savedTemplates.put(UUID.fromString(entry.getKey()), entry.getValue()); }
                    catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to load beacon saved templates", e);
        }
    }

    private void saveTemplatesToDisk() {
        try {
            templateFile.getParentFile().mkdirs();
            Map<String, List<String>> raw = new HashMap<>();
            for (var entry : savedTemplates.entrySet()) raw.put(entry.getKey().toString(), entry.getValue());
            try (Writer writer = new FileWriter(templateFile)) { gson.toJson(raw, writer); }
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to save beacon saved templates", e);
        }
    }

    public void shutdown() {
        saveToDisk();
        saveTemplatesToDisk();
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static double horizontalDistance(Location a, Location b) {
        double dx = a.getBlockX() - b.getBlockX();
        double dz = a.getBlockZ() - b.getBlockZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Location parseLoc(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) { return null; }
    }

    // =========================================================================
    //  Inner data class
    // =========================================================================

    private static class BeaconData {
        String owner;
        List<String> whitelist;
    }
}

package com.minecraftcivilizations.mineroverhaul.scan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ScanProgress {

    private final MinerOverhaul plugin;
    private final Path progressFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final Set<Long> scannedChunks = ConcurrentHashMap.newKeySet();
    private final Object saveLock = new Object();

    public ScanProgress(MinerOverhaul plugin) {
        this.plugin = plugin;
        // Per-world progress file so a fresh world starts with an empty scan.
        String worldName = org.bukkit.Bukkit.getWorlds().isEmpty()
                ? "default"
                : org.bukkit.Bukkit.getWorlds().get(0).getName();
        this.progressFile = plugin.getDataFolder().toPath()
                .resolve("progress").resolve(worldName + ".json");
        load();
    }

    public boolean isScanned(int cx, int cz) {
        return scannedChunks.contains(pack(cx, cz));
    }

    public void markScanned(int cx, int cz) {
        scannedChunks.add(pack(cx, cz));
    }

    public int scannedCount() {
        return scannedChunks.size();
    }

    public void clear() {
        scannedChunks.clear();
        try {
            Files.deleteIfExists(progressFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete progress file: " + e.getMessage());
        }
    }

    public void save() {
        synchronized (saveLock) {
            try {
                Files.createDirectories(progressFile.getParent());
                ProgressPayload payload = new ProgressPayload();
                payload.scannedChunks = new ArrayList<>();
                for (long packed : scannedChunks) {
                    payload.scannedChunks.add(new int[]{(int)(packed >> 32), (int)packed});
                }
                Collections.sort(payload.scannedChunks, (a, b) -> {
                    int c = Integer.compare(a[0], b[0]);
                    return c != 0 ? c : Integer.compare(a[1], b[1]);
                });
                payload.lastCheckpointAt = System.currentTimeMillis();
                String json = gson.toJson(payload);
                Files.writeString(progressFile, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to save scan progress: " + e.getMessage());
            }
        }
    }

    private void load() {
        if (!Files.exists(progressFile)) return;
        try {
            String json = Files.readString(progressFile, StandardCharsets.UTF_8);
            ProgressPayload payload = gson.fromJson(json, ProgressPayload.class);
            if (payload != null && payload.scannedChunks != null) {
                for (int[] coord : payload.scannedChunks) {
                    if (coord != null && coord.length >= 2) {
                        scannedChunks.add(pack(coord[0], coord[1]));
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load scan progress: " + e.getMessage());
        }
    }

    private static long pack(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    private static class ProgressPayload {
        List<int[]> scannedChunks = new ArrayList<>();
        long lastCheckpointAt;
    }
}

package com.minecraftcivilizations.mineroverhaul.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final MinerOverhaul plugin;
    private final ConcurrentHashMap<UUID, SubclassData> cache = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path playersDir;

    public PlayerDataManager(MinerOverhaul plugin) {
        this.plugin = plugin;
        this.playersDir = plugin.getDataFolder().toPath().resolve("players");
        try {
            Files.createDirectories(playersDir);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create players dir: " + e.getMessage());
        }
    }

    public SubclassData getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public SubclassData getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    public SubclassData getOrCreate(UUID uuid) {
        return cache.computeIfAbsent(uuid, u -> {
            SubclassData fromDisk = loadFromDisk(u);
            return fromDisk != null ? fromDisk : new SubclassData(u.toString());
        });
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        // load synchronously off-main using async scheduler is overkill; small JSON read is fine on main
        getOrCreate(uuid);
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        SubclassData data = cache.get(uuid);
        if (data != null) {
            saveAsync(uuid, data);
        }
    }

    public void save(UUID uuid) {
        SubclassData data = cache.get(uuid);
        if (data == null) return;
        saveAsync(uuid, data);
    }

    /** Clear any cached and persisted subclass data for the given player. */
    public void reset(UUID uuid) {
        cache.remove(uuid);
        Path file = fileFor(uuid);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to delete subclass data for " + uuid + ": " + e.getMessage());
        }
    }

    public void flushAll() {
        for (var e : cache.entrySet()) {
            saveSync(e.getKey(), e.getValue());
        }
    }

    private SubclassData loadFromDisk(UUID uuid) {
        Path file = fileFor(uuid);
        if (!Files.exists(file)) return new SubclassData(uuid.toString());
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            SubclassData data = gson.fromJson(json, SubclassData.class);
            if (data == null) data = new SubclassData(uuid.toString());
            if (data.getUuid() == null) data.setUuid(uuid.toString());
            return data;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load subclass data for " + uuid + ": " + e.getMessage());
            return new SubclassData(uuid.toString());
        }
    }

    private void saveAsync(UUID uuid, SubclassData data) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> saveSync(uuid, data));
    }

    private void saveSync(UUID uuid, SubclassData data) {
        Path file = fileFor(uuid);
        try {
            Files.createDirectories(file.getParent());
            String json = gson.toJson(data);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save subclass data for " + uuid + ": " + e.getMessage());
        }
    }

    private Path fileFor(UUID uuid) {
        return playersDir.resolve(uuid + ".json");
    }
}

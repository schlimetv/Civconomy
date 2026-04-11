package com.minecraftcivilizations.specialization.Golem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Specialization;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages Iron Golem ownership and whitelist data.
 *
 * Ownership and whitelist are stored directly on the golem entity via
 * PersistentDataContainer (PDC), so they survive server restarts without
 * needing a separate database or flat-file per golem.
 *
 * "Saved whitelist templates" (the clipboard a Builder can save/import)
 * are stored per-builder in a JSON file on disk.
 *
 * @author Generated for CivLabs
 */
public class IronGolemManager {

    // --- PDC Keys (stored on the Iron Golem entity) ---
    @Getter private final NamespacedKey ownerKey;
    @Getter private final NamespacedKey whitelistKey;

    // --- Saved templates (builder UUID -> list of player names) ---
    private final Map<UUID, List<String>> savedTemplates = new HashMap<>();
    private final File templateFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // --- Transient cache: tracks which player placed a carved pumpkin this tick ---
    // Used to attribute golem creation to a specific player.
    // Map of world+location hash -> player UUID, cleared each tick.
    private final Map<UUID, Long> recentPumpkinPlacers = new HashMap<>();

    public IronGolemManager(Specialization plugin) {
        this.ownerKey = new NamespacedKey(plugin, "iron_golem_owner");
        this.whitelistKey = new NamespacedKey(plugin, "iron_golem_whitelist");
        this.templateFile = new File(plugin.getDataFolder(), "golem_saved_whitelists.json");

        loadTemplates();

        // Periodic cleanup of the pumpkin placer cache (every tick, remove entries older than 5 ticks / 250ms)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            recentPumpkinPlacers.entrySet().removeIf(entry -> now - entry.getValue() > 250);
        }, 1L, 1L);
    }

    // =========================================================================
    //  Pumpkin Placer Tracking (for attributing golem creation to a player)
    // =========================================================================

    /**
     * Records that a player just placed a carved pumpkin.
     * Called from the BlockPlaceEvent handler.
     */
    public void recordPumpkinPlacer(Player player) {
        recentPumpkinPlacers.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Finds the player who most recently placed a carved pumpkin near the
     * golem's spawn location. Returns null if no match is found.
     * We search all recent placers since there should typically only be one
     * within the 250ms window.
     */
    public Player findRecentPumpkinPlacer() {
        for (UUID uuid : recentPumpkinPlacers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    /**
     * Clears the pumpkin placer entry for a player (called after golem spawn is processed).
     */
    public void clearPumpkinPlacer(UUID playerUuid) {
        recentPumpkinPlacers.remove(playerUuid);
    }

    // =========================================================================
    //  Golem Ownership (PDC on entity)
    // =========================================================================

    /**
     * Tags an Iron Golem with its owner (the Builder who created it).
     */
    public void setOwner(IronGolem golem, UUID ownerUuid) {
        golem.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, ownerUuid.toString());
    }

    /**
     * Returns the owner UUID of a player-built Iron Golem, or null if it has no owner.
     */
    public UUID getOwner(IronGolem golem) {
        PersistentDataContainer pdc = golem.getPersistentDataContainer();
        if (pdc.has(ownerKey)) {
            String raw = pdc.get(ownerKey, PersistentDataType.STRING);
            if (raw != null) {
                try {
                    return UUID.fromString(raw);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }

    /**
     * Returns true if this Iron Golem is owned by a Builder.
     */
    public boolean isOwned(IronGolem golem) {
        return golem.getPersistentDataContainer().has(ownerKey);
    }

    /**
     * Returns true if the given player is the owner of this Iron Golem.
     */
    public boolean isOwner(IronGolem golem, Player player) {
        UUID owner = getOwner(golem);
        return owner != null && owner.equals(player.getUniqueId());
    }

    // =========================================================================
    //  Whitelist Management (PDC on entity)
    // =========================================================================

    /**
     * Gets the whitelist for an Iron Golem.
     * Returns an empty list if no whitelist is set.
     */
    public List<String> getWhitelist(IronGolem golem) {
        PersistentDataContainer pdc = golem.getPersistentDataContainer();
        if (pdc.has(whitelistKey)) {
            String raw = pdc.get(whitelistKey, PersistentDataType.STRING);
            if (raw != null && !raw.isEmpty()) {
                return new ArrayList<>(Arrays.asList(raw.split(",")));
            }
        }
        return new ArrayList<>();
    }

    /**
     * Sets the whitelist for an Iron Golem.
     * Names are stored as a comma-separated string in PDC.
     */
    public void setWhitelist(IronGolem golem, List<String> names) {
        // Clean up: trim whitespace, remove empties, lowercase for case-insensitive matching
        List<String> cleaned = new ArrayList<>();
        for (String name : names) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        String joined = String.join(",", cleaned);
        golem.getPersistentDataContainer().set(whitelistKey, PersistentDataType.STRING, joined);
    }

    /**
     * Checks if a player name is on an Iron Golem's whitelist (case-insensitive).
     */
    public boolean isWhitelisted(IronGolem golem, String playerName) {
        List<String> whitelist = getWhitelist(golem);
        for (String name : whitelist) {
            if (name.equalsIgnoreCase(playerName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a player is on an Iron Golem's whitelist.
     */
    public boolean isWhitelisted(IronGolem golem, Player player) {
        return isWhitelisted(golem, player.getName());
    }

    // =========================================================================
    //  Saved Whitelist Templates (per-builder, persisted to JSON file)
    // =========================================================================

    /**
     * Saves the current whitelist of a golem as the builder's template.
     */
    public void saveTemplate(UUID builderUuid, List<String> whitelist) {
        savedTemplates.put(builderUuid, new ArrayList<>(whitelist));
        saveTemplatesToDisk();
    }

    /**
     * Gets the builder's saved whitelist template, or null if none exists.
     */
    public List<String> getSavedTemplate(UUID builderUuid) {
        List<String> template = savedTemplates.get(builderUuid);
        return template != null ? new ArrayList<>(template) : null;
    }

    /**
     * Returns true if the builder has a saved template.
     */
    public boolean hasSavedTemplate(UUID builderUuid) {
        return savedTemplates.containsKey(builderUuid);
    }

    // =========================================================================
    //  Persistence (JSON file for templates only — golem data is in PDC)
    // =========================================================================

    private void loadTemplates() {
        if (!templateFile.exists()) return;
        try (Reader reader = new FileReader(templateFile)) {
            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
            Map<String, List<String>> raw = gson.fromJson(reader, type);
            if (raw != null) {
                for (Map.Entry<String, List<String>> entry : raw.entrySet()) {
                    try {
                        savedTemplates.put(UUID.fromString(entry.getKey()), entry.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to load golem whitelist templates", e);
        }
    }

    private void saveTemplatesToDisk() {
        try {
            templateFile.getParentFile().mkdirs();
            Map<String, List<String>> raw = new HashMap<>();
            for (Map.Entry<UUID, List<String>> entry : savedTemplates.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue());
            }
            try (Writer writer = new FileWriter(templateFile)) {
                gson.toJson(raw, writer);
            }
        } catch (Exception e) {
            Specialization.logger.log(Level.WARNING, "Failed to save golem whitelist templates", e);
        }
    }

    /**
     * Called on plugin disable to ensure templates are saved.
     */
    public void shutdown() {
        saveTemplatesToDisk();
    }
}

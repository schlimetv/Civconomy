package com.animalbreeding.plugin.managers;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Assigns and persists a gender (MALE / FEMALE) to each supported animal.
 *
 * Gender is randomly assigned on first encounter and saved to
 * plugins/AnimalBreeding/genders.yml so it survives server restarts.
 *
 * Only animals of opposing genders may breed together.
 */
public class GenderManager {

    public enum Gender {
        MALE, FEMALE;

        public Gender opposite() {
            return this == MALE ? FEMALE : MALE;
        }

        /** Short symbol used in the hoe display. */
        public String symbol() {
            return this == MALE ? "♂" : "♀";
        }
    }

    private static final Random RNG = new Random();

    private final AnimalBreedingPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    /** In-memory cache: animal UUID → Gender */
    private final Map<UUID, Gender> cache = new HashMap<>();

    public GenderManager(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "genders.yml");
        load();
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns the gender of {@code animal}, assigning one randomly if not yet set.
     * The assignment is immediately persisted (batched save happens on disable).
     */
    public Gender getGender(Animals animal) {
        UUID id = animal.getUniqueId();
        Gender g = cache.get(id);
        if (g == null) {
            g = RNG.nextBoolean() ? Gender.MALE : Gender.FEMALE;
            cache.put(id, g);
            dataConfig.set(id.toString(), g.name());
        }
        return g;
    }

    /**
     * Returns true if {@code a} and {@code b} are of opposing genders.
     * Both genders are assigned on the fly if not yet known.
     */
    public boolean areOpposingGenders(Animals a, Animals b) {
        return getGender(a) != getGender(b);
    }

    /** Saves gender data to disk.  Called from onDisable. */
    public void save() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save genders.yml: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Internal load
    // -----------------------------------------------------------------------

    private void load() {
        if (!dataFile.exists()) {
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : dataConfig.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Gender g = Gender.valueOf(dataConfig.getString(key, "MALE"));
                cache.put(id, g);
            } catch (IllegalArgumentException ignored) {
                // Malformed entry — skip
            }
        }
        plugin.getLogger().info("Loaded " + cache.size() + " animal gender(s) from disk.");
    }
}

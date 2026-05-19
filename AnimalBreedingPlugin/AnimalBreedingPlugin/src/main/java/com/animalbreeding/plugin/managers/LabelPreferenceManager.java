package com.animalbreeding.plugin.managers;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player toggle for the floating animal labels. Default is ON; only
 * players who explicitly turned them OFF are stored, so first-time players
 * always see labels without any registration step.
 */
public class LabelPreferenceManager {

    private final AnimalBreedingPlugin plugin;
    private final File dataFile;
    private final Set<UUID> disabled = new HashSet<>();

    public LabelPreferenceManager(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "label_preferences.yml");
        load();
    }

    public boolean isEnabled(Player player) {
        return !disabled.contains(player.getUniqueId());
    }

    public void setEnabled(Player player, boolean on) {
        boolean changed = on ? disabled.remove(player.getUniqueId())
                             : disabled.add(player.getUniqueId());
        if (changed) save();
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("disabled", disabled.stream().map(UUID::toString).toList());
        try {
            cfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save label_preferences.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        List<String> list = cfg.getStringList("disabled");
        for (String s : list) {
            try { disabled.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
    }
}

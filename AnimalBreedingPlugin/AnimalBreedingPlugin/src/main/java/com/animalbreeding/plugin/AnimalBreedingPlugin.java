package com.animalbreeding.plugin;

import com.animalbreeding.plugin.commands.AnimalLabelCommand;
import com.animalbreeding.plugin.commands.MaxTraitsCommand;
import com.animalbreeding.plugin.commands.MutationCommand;
import com.animalbreeding.plugin.listeners.BreedingListener;
import com.animalbreeding.plugin.listeners.TraitListener;
import com.animalbreeding.plugin.managers.BreedingManager;
import com.animalbreeding.plugin.managers.GenderManager;
import com.animalbreeding.plugin.managers.GeneticsManager;
import com.animalbreeding.plugin.managers.LabelPreferenceManager;
import com.animalbreeding.plugin.tasks.HoeDisplayTask;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

public class AnimalBreedingPlugin extends JavaPlugin {

    private GenderManager           genderManager;
    private GeneticsManager         geneticsManager;
    private BreedingManager         breedingManager;
    private LabelPreferenceManager  labelPreferenceManager;
    private HoeDisplayTask          hoeDisplayTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        genderManager          = new GenderManager(this);
        geneticsManager        = new GeneticsManager(this);
        breedingManager        = new BreedingManager(this, genderManager, geneticsManager);
        labelPreferenceManager = new LabelPreferenceManager(this);

        getServer().getPluginManager().registerEvents(new BreedingListener(this), this);
        getServer().getPluginManager().registerEvents(new TraitListener(this), this);

        hoeDisplayTask = new HoeDisplayTask(this);
        hoeDisplayTask.runTaskTimer(this, 0L, 1L);

        // Register commands
        var mutCmd = getCommand("abmutation");
        if (mutCmd != null) mutCmd.setExecutor(new MutationCommand(this));

        var maxTraitsCmd = getCommand("abmaxtraits");
        if (maxTraitsCmd != null) {
            MaxTraitsCommand executor = new MaxTraitsCommand(this);
            maxTraitsCmd.setExecutor(executor);
            maxTraitsCmd.setTabCompleter(executor);
        }

        var labelCmd = getCommand("animallabel");
        if (labelCmd != null) {
            AnimalLabelCommand executor = new AnimalLabelCommand(this);
            labelCmd.setExecutor(executor);
            labelCmd.setTabCompleter(executor);
        }

        // After a reload all in-memory state is fresh.
        // Clear stale vanilla love mode from any animal that may have had it
        // set before the reload (prevents them trying to breed without our metadata).
        // Also re-apply genetic stat modifiers from the loaded genetics.yml.
        getServer().getScheduler().runTaskLater(this, () -> {
            for (var world : getServer().getWorlds()) {
                for (Entity e : world.getEntities()) {
                    if (!(e instanceof Animals a)) continue;
                    if (!breedingManager.isSupportedAnimal(a)) continue;
                    a.setLoveModeTicks(0);              // clear stale love mode
                    geneticsManager.assignDefaultBehavior(a); // ensure every animal has a behavior
                    geneticsManager.applyTraitStats(a); // restore attribute modifiers
                }
            }
        }, 5L);

        getLogger().info("AnimalBreeding enabled!");
    }

    @Override
    public void onDisable() {
        if (hoeDisplayTask  != null) hoeDisplayTask.cleanup();
        if (genderManager   != null) genderManager.save();
        if (geneticsManager != null) geneticsManager.save();
        getLogger().info("AnimalBreeding disabled.");
    }

    public BreedingManager         getBreedingManager()         { return breedingManager;         }
    public GenderManager           getGenderManager()           { return genderManager;           }
    public GeneticsManager         getGeneticsManager()         { return geneticsManager;         }
    public LabelPreferenceManager  getLabelPreferenceManager()  { return labelPreferenceManager;  }
    public HoeDisplayTask          getHoeDisplayTask()          { return hoeDisplayTask;          }
}

package com.animalbreeding.plugin.listeners;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import com.animalbreeding.plugin.managers.BreedingManager;
import com.animalbreeding.plugin.managers.GeneticsManager;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Set;

public class BreedingListener implements Listener {

    private static final String META_TRIGGERED = "ab_triggered";

    private final AnimalBreedingPlugin plugin;

    public BreedingListener(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Naturally-spawned animals roll for starting traits.
    // Bred offspring are skipped — onBreedSuccess assigns inherited traits.
    // ------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BREEDING) return;
        if (!(event.getEntity() instanceof Animals animal)) return;
        if (!plugin.getBreedingManager().isSupportedAnimal(animal)) return;

        GeneticsManager genetics = plugin.getGeneticsManager();
        boolean rolled = false;
        if (genetics.getTraits(animal).isEmpty()) {
            genetics.assignDefaultTraits(animal);
            rolled = true;
        }
        if (genetics.getBehavior(animal) == null) {
            genetics.assignDefaultBehavior(animal);
            rolled = true;
        }
        if (!rolled) return;

        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> genetics.applyTraitStats(animal), 1L);
    }

    // ------------------------------------------------------------------
    // Block ALL vanilla breeding for supported animals
    // When we cancel, also clear love mode so stale ticks from before a
    // reload cannot cause animals to circle each other without breeding.
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void blockVanillaBreeding(EntityBreedEvent event) {
        if (!(event.getMother() instanceof Animals mother)) return;
        if (!plugin.getBreedingManager().isSupportedAnimal(mother)) return;
        if (mother.hasMetadata(META_TRIGGERED)) return; // our own trigger — allow

        // Clear stale love mode so animals stop circling
        if (event.getMother() instanceof Animals m) m.setLoveModeTicks(0);
        if (event.getFather() instanceof Animals f) f.setLoveModeTicks(0);
        event.setCancelled(true);
    }

    // ------------------------------------------------------------------
    // Player manually feeds an animal (main hand, right-click)
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerFeedAnimal(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals animal)) return;

        Player player  = event.getPlayer();
        BreedingManager manager = plugin.getBreedingManager();

        if (!manager.isSupportedAnimal(animal)) return;

        Material food = manager.getBreedingFood(player, animal);
        if (food == null) return;

        event.setCancelled(true); // always take over vanilla interact

        if (!animal.isAdult()) return;
        if (manager.isOnCooldown(animal)) return;
        if (!manager.isInEnclosedPen(animal)) return;
        if (manager.countFoodInInventory(player, food) < 1) return;

        int penAnimals = manager.countPenAnimals(animal);
        int totalCost  = manager.calculateFoodCost(penAnimals);

        int accepted = manager.addFed(animal, 1, totalCost);
        if (accepted > 0) {
            manager.consumeFood(player, food, accepted);
            // Record feed time for Aggressive trait tracking
            plugin.getGeneticsManager().recordFeedTime(animal);
            // Check for a qualifying partner after the feed is recorded
            attemptBreed(animal, manager, totalCost);
        }
    }

    // ------------------------------------------------------------------
    // Animal eats grass → +1 food, 50/50 dirt or coarse dirt
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnimalEatGrass(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Animals animal)) return;
        if (event.getBlock().getType() != Material.GRASS_BLOCK) return;
        if (event.getTo() != Material.DIRT) return;

        BreedingManager manager = plugin.getBreedingManager();
        if (!manager.isBreedable(animal)) return;
        if (manager.isOnCooldown(animal)) return;
        if (!manager.isInEnclosedPen(animal)) return;

        event.setCancelled(true);
        event.getBlock().setType(Math.random() < 0.5 ? Material.COARSE_DIRT : Material.DIRT);

        int penAnimals = manager.countPenAnimals(animal);
        int totalCost  = manager.calculateFoodCost(penAnimals);

        int accepted = manager.addFed(animal, 1, totalCost);
        if (accepted > 0) {
            attemptBreed(animal, manager, totalCost);
        }
    }

    // ------------------------------------------------------------------
    // Trigger a breed between animal and its qualifying partner
    // ------------------------------------------------------------------

    private void attemptBreed(Animals animal, BreedingManager manager, int totalCost) {
        Animals partner = manager.findBreedingPartner(animal, totalCost);
        if (partner == null) return;

        FixedMetadataValue trigger = new FixedMetadataValue(plugin, true);
        animal.setMetadata(META_TRIGGERED, trigger);
        partner.setMetadata(META_TRIGGERED, trigger);

        // 200 ticks (10 s) gives animals plenty of time to pathfind to each other
        animal.setLoveModeTicks(200);
        partner.setLoveModeTicks(200);
    }

    // ------------------------------------------------------------------
    // Post-breed: cooldown, cleanup, genetics inheritance
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreedSuccess(EntityBreedEvent event) {
        if (!(event.getMother() instanceof Animals mother)) return;
        if (!(event.getFather() instanceof Animals father)) return;

        BreedingManager manager = plugin.getBreedingManager();
        if (!manager.isSupportedAnimal(mother)) return;

        manager.setCooldown(mother, father);

        for (Animals a : new Animals[]{mother, father}) {
            a.removeMetadata(META_TRIGGERED, plugin);
            manager.clearFedAmount(a);
        }

        // Genetics: inherit traits + behavior, and assign gender to offspring
        if (event.getEntity() instanceof Animals offspring) {
            GeneticsManager genetics = plugin.getGeneticsManager();
            Set<GeneticsManager.Trait> traits = genetics.inheritTraits(mother, father);
            genetics.setTraits(offspring, traits);
            genetics.setBehavior(offspring, genetics.inheritBehavior(mother, father));

            // Apply stat modifiers 1 tick later (entity fully initialised)
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> {
                    genetics.applyTraitStats(offspring);
                    // Eagerly assign gender so hoe display shows it immediately
                    plugin.getGenderManager().getGender(offspring);
                }, 1L);
        }
    }

    // ------------------------------------------------------------------
    // Static helpers used by HoeDisplayTask and TraitListener
    // ------------------------------------------------------------------

    public static String formatType(Animals animal) {
        String name = animal.getType().name();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase();
    }

    public static String formatMat(Material material) {
        return material.name().replace('_', ' ').toLowerCase();
    }
}

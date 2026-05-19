package com.animalbreeding.plugin.listeners;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import com.animalbreeding.plugin.managers.BreedingManager;
import com.animalbreeding.plugin.managers.GeneticsManager;
import com.animalbreeding.plugin.managers.GeneticsManager.Trait;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Applies passive and active effects for each genetic trait:
 *
 *  THICK_SKINNED  — +1 extra drop stack on death
 *  CHUNKY         — +2 to any meat drop stacks on death
 *  GIANT          — ×1.25 all drops on death (rounded up)
 *  DWARF          — ×0.75 all drops on death (min 1)
 *  RAPID_GROWTH   — baby grows 2× faster (task)
 *
 *  FERTILE  — handled in BreedingManager.setCooldown
 *  NIMBLE   — handled in GeneticsManager.applyTraitStats (speed attribute)
 *
 * Behavior (every animal has exactly one, tracked separately from traits):
 *  DOCILE         — no effect (vanilla behaviour)
 *  SKITTISH       — flees from any non-sneaking player within ~8 blocks.
 *                   Sneaking players can approach safely; no prior hit required.
 *  AGGRESSIVE     — attacks nearby unfed players & hostile mobs (task)
 */
public class TraitListener implements Listener {

    private static final Random RNG = new Random();

    private final AnimalBreedingPlugin plugin;

    public TraitListener(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
        startBehaviorTask();
    }

    // ------------------------------------------------------------------
    // Death drops
    // ------------------------------------------------------------------

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals animal)) return;
        if (!plugin.getBreedingManager().isSupportedAnimal(animal)) return;

        // Remove floating label when animal dies
        plugin.getHoeDisplayTask().removeDisplayForAnimal(animal.getUniqueId());

        GeneticsManager genetics = plugin.getGeneticsManager();
        Set<Trait> traits = genetics.getTraits(animal);
        List<ItemStack> drops = event.getDrops();

        if (traits.contains(Trait.THICK_SKINNED) && !drops.isEmpty()) {
            ItemStack extra = drops.get(0).clone();
            extra.setAmount(1);
            drops.add(extra);
        }

        if (traits.contains(Trait.CHUNKY)) {
            for (ItemStack drop : drops) {
                if (isMeat(drop.getType())) drop.setAmount(drop.getAmount() + 2);
            }
        }

        // GIANT and DWARF applied after CHUNKY so modifiers stack correctly
        if (traits.contains(Trait.GIANT)) {
            for (ItemStack drop : drops) {
                drop.setAmount((int) Math.ceil(drop.getAmount() * 1.25));
            }
        } else if (traits.contains(Trait.DWARF)) {
            for (ItemStack drop : drops) {
                drop.setAmount(Math.max(1, (int)(drop.getAmount() * 0.75)));
            }
        }
    }

    private boolean isMeat(Material m) {
        return switch (m) {
            case BEEF, COOKED_BEEF,
                 PORKCHOP, COOKED_PORKCHOP,
                 CHICKEN, COOKED_CHICKEN,
                 MUTTON, COOKED_MUTTON -> true;
            default -> false;
        };
    }

    // ------------------------------------------------------------------
    // Repeating behaviour task (every 20 ticks = 1 second)
    // ------------------------------------------------------------------

    private void startBehaviorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                GeneticsManager genetics = plugin.getGeneticsManager();
                BreedingManager breeding  = plugin.getBreedingManager();

                for (var world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (!(entity instanceof Animals animal)) continue;
                        if (!breeding.isSupportedAnimal(animal)) continue;

                        Set<Trait> traits = genetics.getTraits(animal);

                        // Rapid Growth — applies to babies only
                        if (!animal.isAdult() && traits.contains(Trait.RAPID_GROWTH)) {
                            int age = animal.getAge();
                            if (age < 0) animal.setAge(Math.min(0, age + 20));
                        }

                        if (!animal.isAdult()) continue; // behaviours below only affect adults

                        Trait b = genetics.getBehavior(animal);
                        if (b == Trait.AGGRESSIVE) {
                            handleAggressive(animal, genetics);
                        } else if (b == Trait.SKITTISH) {
                            handleSkittish(animal);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ------------------------------------------------------------------
    // Skittish — pathfinds away from any non-sneaking player within range.
    // Sneaking players are safe and can approach without triggering flee.
    // ------------------------------------------------------------------

    private static final double SKITTISH_RANGE  = 8.0;  // detect players within ~8 blocks
    private static final double FLEE_DISTANCE   = 10.0; // pathfind target offset
    private static final double FLEE_SPEED      = 1.4;  // speed multiplier (>1 = run)

    private void handleSkittish(Animals animal) {
        Player nearest = nearestNonSneakingPlayer(animal);
        if (nearest != null) fleeFrom(animal, nearest);
    }

    private Player nearestNonSneakingPlayer(Animals animal) {
        Player best = null;
        double bestSq = SKITTISH_RANGE * SKITTISH_RANGE;
        for (Entity e : animal.getNearbyEntities(SKITTISH_RANGE, SKITTISH_RANGE / 2, SKITTISH_RANGE)) {
            if (e instanceof Player p && !p.isSneaking()) {
                double d = p.getLocation().distanceSquared(animal.getLocation());
                if (d < bestSq) {
                    bestSq = d;
                    best = p;
                }
            }
        }
        return best;
    }

    private void fleeFrom(Animals animal, Player player) {
        Vector away = animal.getLocation().toVector()
            .subtract(player.getLocation().toVector())
            .setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5);
        }
        away.normalize().multiply(FLEE_DISTANCE);

        Location target = animal.getLocation().add(away);
        target.setY(animal.getWorld().getHighestBlockYAt(target) + 1.0);
        animal.getPathfinder().moveTo(target, FLEE_SPEED);
    }

    // ------------------------------------------------------------------
    // Aggressive — randomly attacks nearby players or hostile mobs that
    // have not recently fed this animal (within 30 minutes)
    // ------------------------------------------------------------------

    private void handleAggressive(Animals animal, GeneticsManager genetics) {
        if (genetics.isRecentlyFed(animal)) return;
        if (RNG.nextInt(20) != 0) return; // ~5 % chance per second

        List<LivingEntity> targets = new ArrayList<>();
        for (Entity e : animal.getNearbyEntities(2.5, 1.5, 2.5)) {
            if (e instanceof Player p && !p.isSneaking()) targets.add(p);
            else if (e instanceof Monster m)               targets.add(m);
        }

        if (!targets.isEmpty()) {
            LivingEntity target = targets.get(RNG.nextInt(targets.size()));
            target.damage(2.0, animal);
        }
    }
}

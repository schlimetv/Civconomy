package com.animalbreeding.plugin.managers;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

public class BreedingManager {

    public static final Set<EntityType> SUPPORTED_ANIMALS = Set.of(
        EntityType.COW, EntityType.PIG, EntityType.CHICKEN, EntityType.SHEEP
    );

    private static final int BASE_FOOD_COST          = 3;
    private static final int EXTRA_FOOD_PER_PEN_ANIMAL = 2;
    private static final int MAX_PEN_BLOCKS           = 512;

    private final AnimalBreedingPlugin plugin;
    private final GenderManager        genderManager;
    private final GeneticsManager      geneticsManager;

    /**
     * Cooldown stored as EXPIRY timestamp (ms) rather than start time.
     * This allows the Fertile trait to reduce the effective duration cleanly.
     */
    private final Map<UUID, Long> cooldownExpiry = new HashMap<>();

    /** Shared-pool feeding progress per animal. */
    private final Map<UUID, Integer> fedAmount = new HashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public BreedingManager(AnimalBreedingPlugin plugin,
                           GenderManager genderManager,
                           GeneticsManager geneticsManager) {
        this.plugin          = plugin;
        this.genderManager   = genderManager;
        this.geneticsManager = geneticsManager;
    }

    // -----------------------------------------------------------------------
    // Animal checks
    // -----------------------------------------------------------------------

    public boolean isSupportedAnimal(Animals animal) {
        return SUPPORTED_ANIMALS.contains(animal.getType());
    }

    public boolean isBreedable(Animals animal) {
        return isSupportedAnimal(animal) && animal.isAdult();
    }

    // -----------------------------------------------------------------------
    // Cooldown (expiry-based)
    // -----------------------------------------------------------------------

    public boolean isOnCooldown(Animals animal) {
        Long expiry = cooldownExpiry.get(animal.getUniqueId());
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public long getRemainingCooldown(Animals animal) {
        Long expiry = cooldownExpiry.get(animal.getUniqueId());
        if (expiry == null) return 0;
        return Math.max(0, (expiry - System.currentTimeMillis()) / 1000);
    }

    /**
     * Applies cooldown to both parents.
     * If either parent carries the FERTILE trait, cooldown is halved.
     */
    public void setCooldown(Animals mother, Animals father) {
        long durationMs = getBaseCooldownMs();

        boolean fertile = geneticsManager.hasTrait(mother, GeneticsManager.Trait.FERTILE)
                       || geneticsManager.hasTrait(father, GeneticsManager.Trait.FERTILE);
        if (fertile) durationMs /= 2;

        long expiry = System.currentTimeMillis() + durationMs;
        cooldownExpiry.put(mother.getUniqueId(), expiry);
        cooldownExpiry.put(father.getUniqueId(), expiry);
        purgeExpiredCooldowns();
    }

    public void clearCooldown(Animals animal) {
        cooldownExpiry.remove(animal.getUniqueId());
    }

    private long getBaseCooldownMs() {
        return plugin.getConfig().getLong("breeding.cooldown-seconds", 300) * 1000L;
    }

    private void purgeExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldownExpiry.entrySet().removeIf(e -> now >= e.getValue());
    }

    // -----------------------------------------------------------------------
    // Gender
    // -----------------------------------------------------------------------

    public GenderManager getGenderManager() { return genderManager; }

    // -----------------------------------------------------------------------
    // Feeding progress (shared pool, capped per-animal)
    // -----------------------------------------------------------------------

    public int getFedAmount(Animals animal) {
        return fedAmount.getOrDefault(animal.getUniqueId(), 0);
    }

    /**
     * Adds up to {@code amount} feed to this animal, capped at {@code totalCost-1}
     * so the partner must always contribute at least 1.
     *
     * @return how many were actually accepted (0 means already at cap)
     */
    public int addFed(Animals animal, int amount, int totalCost) {
        int current = getFedAmount(animal);
        int cap     = totalCost - 1;
        if (current >= cap) return 0;
        int accepted = Math.min(amount, cap - current);
        fedAmount.put(animal.getUniqueId(), current + accepted);
        return accepted;
    }

    public void clearFedAmount(Animals animal) {
        fedAmount.remove(animal.getUniqueId());
    }

    // -----------------------------------------------------------------------
    // Partner search (pen-aware, gender-aware, genetics-unaware)
    // -----------------------------------------------------------------------

    public Animals findBreedingPartner(Animals animal, int totalCost) {
        int myFed = getFedAmount(animal);
        Set<Block> pen = floodFillPen(animal.getLocation());

        Animals best = null;
        int bestFed  = 0;

        for (Entity e : animal.getWorld().getNearbyEntities(
                animal.getLocation(), MAX_PEN_BLOCKS, 10, MAX_PEN_BLOCKS)) {
            if (!(e instanceof Animals candidate)) continue;
            if (e.getUniqueId().equals(animal.getUniqueId())) continue;
            if (candidate.getType() != animal.getType()) continue;
            if (!candidate.isAdult()) continue;
            if (isOnCooldown(candidate)) continue;
            if (!genderManager.areOpposingGenders(animal, candidate)) continue;

            if (pen != null) {
                Block feet = candidate.getLocation().getBlock();
                if (!pen.contains(feet) && !pen.contains(feet.getRelative(0, -1, 0))) continue;
            }

            int partnerFed = getFedAmount(candidate);
            if (partnerFed < 1) continue;
            if (myFed + partnerFed < totalCost) continue;

            if (partnerFed > bestFed) { bestFed = partnerFed; best = candidate; }
        }
        return best;
    }

    // -----------------------------------------------------------------------
    // Pen detection
    // -----------------------------------------------------------------------

    public boolean isInEnclosedPen(Animals animal) {
        return floodFillPen(animal.getLocation()) != null;
    }

    public int countPenAnimals(Animals anchor, UUID... exclude) {
        Set<UUID> excl = new HashSet<>(Arrays.asList(exclude));
        excl.add(anchor.getUniqueId());

        Set<Block> pen = floodFillPen(anchor.getLocation());
        if (pen == null) return 0;

        int count = 0;
        for (Entity e : anchor.getWorld().getNearbyEntities(
                anchor.getLocation(), MAX_PEN_BLOCKS, 10, MAX_PEN_BLOCKS)) {
            if (e.getType() != anchor.getType()) continue;
            if (excl.contains(e.getUniqueId())) continue;
            if (!(e instanceof Animals a) || !a.isAdult()) continue;
            Block feet = e.getLocation().getBlock();
            if (pen.contains(feet) || pen.contains(feet.getRelative(0, -1, 0))) count++;
        }
        return count;
    }

    private Set<Block> floodFillPen(Location origin) {
        Block start = origin.getBlock();
        Set<Block> visited = new HashSet<>();
        Queue<Block> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_PEN_BLOCKS) return null;
            Block cur = queue.poll();
            for (int i = 0; i < 4; i++) {
                Block nb = cur.getRelative(dx[i], 0, dz[i]);
                if (visited.contains(nb)) continue;
                if (isPenBoundary(nb)) continue;
                visited.add(nb);
                queue.add(nb);
            }
        }
        return visited;
    }

    private boolean isPenBoundary(Block block) {
        Material m    = block.getType();
        String   name = m.name();
        if (name.contains("FENCE") || name.contains("WALL") || name.contains("GATE")) return true;
        if (!m.isSolid()) return false;
        return m.isOccluding();
    }

    // -----------------------------------------------------------------------
    // Food cost
    // -----------------------------------------------------------------------

    public int calculateFoodCost(int penAnimalCount) {
        return BASE_FOOD_COST + penAnimalCount * EXTRA_FOOD_PER_PEN_ANIMAL;
    }

    // -----------------------------------------------------------------------
    // Inventory helpers
    // -----------------------------------------------------------------------

    public int countFoodInInventory(Player player, Material food) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == food) total += item.getAmount();
        }
        return total;
    }

    public void consumeFood(Player player, Material food, int amount) {
        PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int slot = 0; slot < inv.getSize() && remaining > 0; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() != food) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                inv.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - remaining);
                inv.setItem(slot, item);
                remaining = 0;
            }
        }
        player.updateInventory();
    }

    public Material getBreedingFood(Player player, Animals animal) {
        Material held     = player.getInventory().getItemInMainHand().getType();
        Material expected = breedingFoodFor(animal.getType());
        return (expected != null && held == expected) ? held : null;
    }

    public static Material breedingFoodFor(EntityType type) {
        return switch (type) {
            case COW, SHEEP -> Material.WHEAT;
            case PIG        -> Material.CARROT;
            case CHICKEN    -> Material.WHEAT_SEEDS;
            default         -> null;
        };
    }
}

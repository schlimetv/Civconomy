package com.animalbreeding.plugin.managers;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages per-animal genetic traits: storage, inheritance, mutation, and stat application.
 *
 * Traits are saved to genetics.yml and survive server restarts.
 * Stat modifiers (speed, health, scale) are re-applied on enable via applyTraitStats().
 */
public class GeneticsManager {

    /** Fallback cap if config is missing. Configurable at runtime via /abmaxtraits. */
    public static final int DEFAULT_MAX_TRAITS = 3;
    public static final int HARD_MAX_TRAITS    = 10; // upper bound for sanity

    // -----------------------------------------------------------------------
    // Trait definitions
    // -----------------------------------------------------------------------

    public enum Trait {
        THICK_SKINNED ("Thick Skinned",  "§a",  "Higher health, extra drops"),
        CHUNKY        ("Chunky",         "§6",  "Higher meat yields"),
        RAPID_GROWTH  ("Rapid Growth",   "§b",  "Babies grow faster"),
        FERTILE       ("Fertile",        "§d",  "Reduced breeding cooldown"),
        GIANT         ("Giant",          "§c",  "More health & drops, slower"),
        DWARF         ("Dwarf",          "§e",  "Faster, smaller, less yield"),
        DOCILE        ("Docile",         "§7",  "Vanilla behaviour"),
        SKITTISH      ("Skittish",       "§f",  "Flees non-sneaking players"),
        AGGRESSIVE    ("Aggressive",     "§4",  "Attacks unfed nearby entities"),
        NIMBLE        ("Nimble",         "§3",  "Faster movement");

        public final String displayName;
        public final String color;
        public final String description;

        Trait(String d, String c, String desc) { displayName = d; color = c; description = desc; }
    }

    /**
     * Behaviors are stored separately from traits: every animal has exactly one
     * and it does not count toward the configurable max-traits cap.
     */
    public static final EnumSet<Trait> BEHAVIORS =
        EnumSet.of(Trait.DOCILE, Trait.SKITTISH, Trait.AGGRESSIVE);

    private static final Trait[] BEHAVIOR_VALUES = BEHAVIORS.toArray(new Trait[0]);
    private static final EnumSet<Trait> NON_BEHAVIOR_TRAITS =
        EnumSet.complementOf(BEHAVIORS);

    // Modifier key prefixes stored on the entity attributes
    private static final String[] MODIFIER_KEYS = {
        "ab_thick_skin_hp", "ab_giant_hp", "ab_giant_speed", "ab_giant_scale",
        "ab_dwarf_speed", "ab_dwarf_scale", "ab_nimble_speed"
    };

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    private final AnimalBreedingPlugin plugin;
    private final File dataFile;
    private YamlConfiguration dataConfig;

    /** animal UUID → set of non-behavior traits */
    private final Map<UUID, Set<Trait>> genetics = new HashMap<>();

    /** animal UUID → behavior (DOCILE / SKITTISH / AGGRESSIVE). Always exactly one once assigned. */
    private final Map<UUID, Trait> behaviors = new HashMap<>();

    /** animal UUID → last time it was fed by a player (ms). Used by Aggressive trait. */
    private final Map<UUID, Long> lastFedTime = new HashMap<>();

    private final Random rng = new Random();

    private double mutationChance;
    private int    maxTraits;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public GeneticsManager(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "genetics.yml");
        this.mutationChance = plugin.getConfig().getDouble("breeding.mutation-chance", 0.02);
        this.maxTraits      = clampMax(plugin.getConfig().getInt("breeding.max-traits", DEFAULT_MAX_TRAITS));
        load();
    }

    private static int clampMax(int v) {
        return Math.max(0, Math.min(HARD_MAX_TRAITS, v));
    }

    // -----------------------------------------------------------------------
    // Trait access
    // -----------------------------------------------------------------------

    public Set<Trait> getTraits(Animals animal) {
        return Collections.unmodifiableSet(
            genetics.getOrDefault(animal.getUniqueId(), Collections.emptySet()));
    }

    public void setTraits(Animals animal, Set<Trait> traits) {
        if (traits.isEmpty()) genetics.remove(animal.getUniqueId());
        else genetics.put(animal.getUniqueId(), new HashSet<>(traits));
    }

    public boolean hasTrait(Animals animal, Trait trait) {
        return genetics.getOrDefault(animal.getUniqueId(), Collections.emptySet()).contains(trait);
    }

    // -----------------------------------------------------------------------
    // Behavior access (DOCILE / SKITTISH / AGGRESSIVE)
    // -----------------------------------------------------------------------

    /** @return this animal's behavior, or {@code null} if none has been assigned yet. */
    public Trait getBehavior(Animals animal) {
        return behaviors.get(animal.getUniqueId());
    }

    public void setBehavior(Animals animal, Trait behavior) {
        if (behavior == null) {
            behaviors.remove(animal.getUniqueId());
        } else if (BEHAVIORS.contains(behavior)) {
            behaviors.put(animal.getUniqueId(), behavior);
        }
    }

    /** Picks a uniformly random behavior for an animal that has none yet. No-op otherwise. */
    public void assignDefaultBehavior(Animals animal) {
        if (behaviors.containsKey(animal.getUniqueId())) return;
        behaviors.put(animal.getUniqueId(), BEHAVIOR_VALUES[rng.nextInt(BEHAVIOR_VALUES.length)]);
    }

    /**
     * Inherits a behavior from one of the parents at random. Falls back to a
     * uniformly-random behavior if neither parent has one stored.
     */
    public Trait inheritBehavior(Animals parent1, Animals parent2) {
        Trait a = behaviors.get(parent1.getUniqueId());
        Trait b = behaviors.get(parent2.getUniqueId());
        if (a != null && b != null) return rng.nextBoolean() ? a : b;
        if (a != null) return a;
        if (b != null) return b;
        return BEHAVIOR_VALUES[rng.nextInt(BEHAVIOR_VALUES.length)];
    }

    /**
     * Gives a freshly-spawned animal a starting trait set when it has none.
     * Distribution: 30 % zero traits, 50 % one trait, 20 % two traits.
     * Behaviors are excluded — see {@link #assignDefaultBehavior}.
     * Conflicts (GIANT vs DWARF) are resolved randomly. No-op if the animal
     * already has traits stored.
     */
    public void assignDefaultTraits(Animals animal) {
        if (genetics.containsKey(animal.getUniqueId())) return;

        int roll = rng.nextInt(100);
        int count = roll < 30 ? 0 : roll < 80 ? 1 : 2;
        if (count == 0) return;

        List<Trait> pool = new ArrayList<>(NON_BEHAVIOR_TRAITS);
        Collections.shuffle(pool, rng);
        Set<Trait> picked = new HashSet<>(pool.subList(0, Math.min(count, pool.size())));
        resolveConflicts(picked, rng);
        setTraits(animal, picked);
    }

    // -----------------------------------------------------------------------
    // Inheritance & mutation
    // -----------------------------------------------------------------------

    /**
     * Builds an offspring trait set from two parents:
     *  - Each parent trait has 50 % chance to pass.
     *  - If both parents share a trait, chance is 75 % (1 − 0.5²).
     *  - After inheritance, one random mutation may occur with configurable probability.
     *  - Conflicting traits (GIANT/DWARF, personality group) are resolved randomly.
     */
    public Set<Trait> inheritTraits(Animals parent1, Animals parent2) {
        Set<Trait> a = genetics.getOrDefault(parent1.getUniqueId(), Collections.emptySet());
        Set<Trait> b = genetics.getOrDefault(parent2.getUniqueId(), Collections.emptySet());

        Set<Trait> pool = new HashSet<>(a);
        pool.addAll(b);
        pool.removeAll(BEHAVIORS);

        Set<Trait> offspring = new HashSet<>();
        for (Trait t : pool) {
            boolean fromA = a.contains(t) && rng.nextDouble() < 0.5;
            boolean fromB = b.contains(t) && rng.nextDouble() < 0.5;
            if (fromA || fromB) offspring.add(t);
        }

        // Mutation — picks from non-behavior traits only
        if (rng.nextDouble() < mutationChance) {
            Trait[] candidates = NON_BEHAVIOR_TRAITS.toArray(new Trait[0]);
            Trait mutated = candidates[rng.nextInt(candidates.length)];
            if (offspring.contains(mutated)) offspring.remove(mutated);
            else offspring.add(mutated);
        }

        resolveConflicts(offspring, rng);
        trimToMax(offspring);
        return offspring;
    }

    private void trimToMax(Set<Trait> traits) {
        if (traits.size() <= maxTraits) return;
        List<Trait> ordered = new ArrayList<>(traits);
        Collections.shuffle(ordered, rng);
        traits.retainAll(ordered.subList(0, maxTraits));
    }

    private void resolveConflicts(Set<Trait> traits, Random rng) {
        // Size: GIANT vs DWARF
        if (traits.contains(Trait.GIANT) && traits.contains(Trait.DWARF)) {
            traits.remove(rng.nextBoolean() ? Trait.GIANT : Trait.DWARF);
        }
    }

    // -----------------------------------------------------------------------
    // Stat application (health, speed, scale)
    // -----------------------------------------------------------------------

    /**
     * Applies attribute modifiers for all traits this animal carries.
     * Safe to call multiple times — removes previous modifiers first.
     */
    public void applyTraitStats(Animals animal) {
        removeAllModifiers(animal);

        Set<Trait> traits = getTraits(animal);

        if (traits.contains(Trait.THICK_SKINNED)) {
            addMod(animal, Attribute.MAX_HEALTH, "ab_thick_skin_hp",
                4.0, AttributeModifier.Operation.ADD_NUMBER);
        }
        if (traits.contains(Trait.GIANT)) {
            addMod(animal, Attribute.MAX_HEALTH,       "ab_giant_hp",
                6.0,  AttributeModifier.Operation.ADD_NUMBER);
            addMod(animal, Attribute.MOVEMENT_SPEED,   "ab_giant_speed",
                -0.2, AttributeModifier.Operation.ADD_SCALAR);
            addMod(animal, Attribute.SCALE,            "ab_giant_scale",
                0.15, AttributeModifier.Operation.ADD_SCALAR);
        }
        if (traits.contains(Trait.DWARF)) {
            addMod(animal, Attribute.MOVEMENT_SPEED,   "ab_dwarf_speed",
                0.2,  AttributeModifier.Operation.ADD_SCALAR);
            addMod(animal, Attribute.SCALE,            "ab_dwarf_scale",
                -0.1, AttributeModifier.Operation.ADD_SCALAR);
        }
        if (traits.contains(Trait.NIMBLE)) {
            addMod(animal, Attribute.MOVEMENT_SPEED,   "ab_nimble_speed",
                0.3,  AttributeModifier.Operation.ADD_SCALAR);
        }

        // Clamp health to new max
        AttributeInstance hp = animal.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && animal.getHealth() > hp.getValue()) {
            animal.setHealth(hp.getValue());
        }
    }

    private void addMod(Animals animal, Attribute attr, String keyStr,
                        double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = animal.getAttribute(attr);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, keyStr);
        // Only add if not already present
        boolean exists = inst.getModifiers().stream()
            .anyMatch(m -> m.getKey().equals(key));
        if (!exists) inst.addModifier(new AttributeModifier(key, amount, op));
    }

    private void removeAllModifiers(Animals animal) {
        for (Attribute attr : new Attribute[]{
                Attribute.MAX_HEALTH,
                Attribute.MOVEMENT_SPEED,
                Attribute.SCALE}) {
            AttributeInstance inst = animal.getAttribute(attr);
            if (inst == null) continue;
            for (String keyStr : MODIFIER_KEYS) {
                NamespacedKey key = new NamespacedKey(plugin, keyStr);
                inst.getModifiers().stream()
                    .filter(m -> m.getKey().equals(key))
                    .findFirst()
                    .ifPresent(inst::removeModifier);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Feed-time tracking (Aggressive trait)
    // -----------------------------------------------------------------------

    public void recordFeedTime(Animals animal) {
        lastFedTime.put(animal.getUniqueId(), System.currentTimeMillis());
    }

    /** True if the animal was fed within the last 30 minutes. */
    public boolean isRecentlyFed(Animals animal) {
        Long t = lastFedTime.get(animal.getUniqueId());
        return t != null && (System.currentTimeMillis() - t) < 30 * 60_000L;
    }

    // -----------------------------------------------------------------------
    // Mutation rate
    // -----------------------------------------------------------------------

    public double getMutationChance() { return mutationChance; }

    public void setMutationChance(double chance) {
        this.mutationChance = Math.max(0, Math.min(1, chance));
        plugin.getConfig().set("breeding.mutation-chance", this.mutationChance);
        plugin.saveConfig();
    }

    public int getMaxTraits() { return maxTraits; }

    /**
     * Sets the cap on traits per animal (behaviors excluded). Existing animals
     * keep their current trait sets; the new cap only affects future inheritance
     * and default rolls. The value is persisted to config.yml.
     */
    public void setMaxTraits(int newMax) {
        this.maxTraits = clampMax(newMax);
        plugin.getConfig().set("breeding.max-traits", this.maxTraits);
        plugin.saveConfig();
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    public void save() {
        // Reset the file so removed animals are dropped
        dataConfig = new YamlConfiguration();

        for (Map.Entry<UUID, Set<Trait>> entry : genetics.entrySet()) {
            List<String> names = new ArrayList<>();
            for (Trait t : entry.getValue()) names.add(t.name());
            dataConfig.set("traits." + entry.getKey(), names);
        }
        for (Map.Entry<UUID, Trait> entry : behaviors.entrySet()) {
            dataConfig.set("behaviors." + entry.getKey(), entry.getValue().name());
        }
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save genetics.yml: " + e.getMessage());
        }
    }

    private void load() {
        dataConfig = dataFile.exists()
            ? YamlConfiguration.loadConfiguration(dataFile)
            : new YamlConfiguration();

        // New format: traits.<uuid> + behaviors.<uuid>
        var traitsSection    = dataConfig.getConfigurationSection("traits");
        var behaviorsSection = dataConfig.getConfigurationSection("behaviors");

        if (traitsSection != null) {
            for (String key : traitsSection.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id == null) continue;
                Set<Trait> traits = new HashSet<>();
                for (String name : traitsSection.getStringList(key)) {
                    try {
                        Trait t = Trait.valueOf(name);
                        if (!BEHAVIORS.contains(t)) traits.add(t);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (!traits.isEmpty()) genetics.put(id, traits);
            }
        }
        if (behaviorsSection != null) {
            for (String key : behaviorsSection.getKeys(false)) {
                UUID id = parseUuid(key);
                if (id == null) continue;
                String name = behaviorsSection.getString(key);
                if (name == null) continue;
                try {
                    Trait t = Trait.valueOf(name);
                    if (BEHAVIORS.contains(t)) behaviors.put(id, t);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // Legacy format: top-level <uuid>: [TRAITS...] including any behavior trait.
        // Migrate by splitting them into the two maps.
        for (String key : dataConfig.getKeys(false)) {
            if (key.equals("traits") || key.equals("behaviors")) continue;
            UUID id = parseUuid(key);
            if (id == null) continue;
            Set<Trait> traits = new HashSet<>();
            for (String name : dataConfig.getStringList(key)) {
                try {
                    Trait t = Trait.valueOf(name);
                    if (BEHAVIORS.contains(t)) behaviors.putIfAbsent(id, t);
                    else traits.add(t);
                } catch (IllegalArgumentException ignored) {}
            }
            if (!traits.isEmpty()) genetics.merge(id, traits, (oldSet, newSet) -> {
                oldSet.addAll(newSet); return oldSet;
            });
        }

        plugin.getLogger().info("Loaded genetics for " + genetics.size()
            + " animal(s) and behaviors for " + behaviors.size() + ".");
    }

    private static UUID parseUuid(String s) {
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}

package com.minecraftcivilizations.specialization.Reinforcement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ReinforcementManager {

    public static final NamespacedKey namespacedKey = new NamespacedKey(Specialization.getInstance(), "reinforcedBlocks");

    /** Gson instance with backward-compatible Reinforcement adapter. */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Reinforcement.class, new ReinforcementTypeAdapter())
            .create();

    private static final Map<Vector, Long> lastTimeSpawnedParticle = new HashMap<>();
    private static final Map<Chunk, Set<Reinforcement>> cachedReinforcements = new HashMap<>();
    private static final Map<Chunk, List<Reinforcement>> cachedLists = new HashMap<>();
    private static final Map<Chunk, Long> cacheTime = new HashMap<>();
    private static final Map<Chunk, Integer> chunkIndices = new HashMap<>();

    private static final long cooldown = 1000L; // per-block particle cooldown
    private static final long CACHE_EXPIRE_MS = 2 * 60 * 1000L; // 2 minutes
    private static final int CHUNK_RADIUS = 3; // scan radius around player to show particles

    // Particle colors per tier
    private static final Particle.DustOptions DUST_WOODEN = new Particle.DustOptions(Color.fromRGB(139, 90, 43), 0.8f);   // brown
    private static final Particle.DustOptions DUST_LIGHT  = new Particle.DustOptions(Color.fromRGB(250, 150, 100), 1.0f);  // orange
    private static final Particle.DustOptions DUST_HEAVY  = new Particle.DustOptions(Color.fromRGB(150, 150, 150), 1.6f);  // gray

    /**
     * Optimized by Jfrogy and redesigned by Jfrogy.
     * Extended with wooden tier, decay, and tier-aware particles.
     */

    // --------------------- PARTICLE STREAMING ---------------------
    public static void startReinforcement() {

        // --- Player scan every 3 seconds ---
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<Chunk, Long>> it = cacheTime.entrySet().iterator();

                // Clean old cache
                while (it.hasNext()) {
                    Map.Entry<Chunk, Long> e = it.next();
                    if (now - e.getValue() > CACHE_EXPIRE_MS) {
                        cachedReinforcements.remove(e.getKey());
                        cachedLists.remove(e.getKey());
                        chunkIndices.remove(e.getKey());
                        it.remove();
                    }
                }

                // Clean stale particle cooldowns (prevents unbounded growth)
                if (lastTimeSpawnedParticle.size() > 500) {
                    lastTimeSpawnedParticle.entrySet().removeIf(
                            e -> now - e.getValue() > cooldown * 2
                    );
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (getHeldReinforcementTier(player) == null) continue;

                    Chunk playerChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = playerChunk.getX() + dx;
                            int cz = playerChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            if (!cachedReinforcements.containsKey(chunk)) {
                                Set<Reinforcement> set = getReinforcedBlocks(chunk);
                                if (set != null && !set.isEmpty()) {
                                    cachedReinforcements.put(chunk, set);
                                    cachedLists.put(chunk, new ArrayList<>(set));
                                    cacheTime.put(chunk, now);
                                }
                            } else {
                                cacheTime.put(chunk, now); // refresh cache activity
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(Specialization.getInstance(), 0L, 60L); // every 3 seconds (60 ticks)

        // --- Particle update every 2 ticks ---
        // 25 updates/second is visually identical to 50 for dust particles,
        // since the client renders each particle for several ticks anyway.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ReinforcementTier heldTier = getHeldReinforcementTier(player);
                    if (heldTier == null) continue;

                    Chunk baseChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = baseChunk.getX() + dx;
                            int cz = baseChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            // Use the pre-built list instead of allocating
                            // a new ArrayList from the Set every tick
                            List<Reinforcement> list = cachedLists.get(chunk);
                            if (list == null || list.isEmpty()) continue;

                            int index = chunkIndices.getOrDefault(chunk, 0);

                            int batchSize = list.size() > 25 ? 3 : 1; // batch 3 if >25 reinforced blocks
                            for (int i = 0; i < batchSize; i++) {
                                index = (index + 1) % list.size();
                                Reinforcement r = list.get(index);
                                // Only show particles for the tier matching the held item
                                if (r.getTier() == heldTier) {
                                    spawnParticle(player, r);
                                }
                            }

                            chunkIndices.put(chunk, index);
                        }
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 2L); // every 2 ticks

        // --- Decay task every 5 minutes (6000 ticks) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                processDecay();
            }
        }.runTaskTimer(Specialization.getInstance(), 6000L, 6000L);
    }

    private static void spawnParticle(Player player, Reinforcement r) {
        long now = System.currentTimeMillis();
        synchronized (lastTimeSpawnedParticle) {
            Long last = lastTimeSpawnedParticle.get(r.getLocation());
            if (last != null && now - last < cooldown) return;
            lastTimeSpawnedParticle.put(r.getLocation(), now);
        }

        Block b = player.getWorld().getBlockAt(
                r.getLocation().getBlockX(),
                r.getLocation().getBlockY(),
                r.getLocation().getBlockZ());
        Location base = b.getLocation().add(0.5, 0.5, 0.5);

        double offset = 0.55;
        double random_a = Math.random() * 0.33;
        double random_b = Math.random() * 0.33;
        Vector[] dirs = {
                new Vector(offset, random_a, random_b),
                new Vector(-offset, random_b, random_a),
                new Vector(random_a, offset, random_b),
                new Vector(random_b, -offset, random_a),
                new Vector(random_a, random_b, offset),
                new Vector(random_b, random_a, -offset)
        };

        Particle.DustOptions dust = switch (r.getTier()) {
            case WOODEN -> DUST_WOODEN;
            case LIGHT  -> DUST_LIGHT;
            case HEAVY  -> DUST_HEAVY;
        };

        for (Vector v : dirs) {
            Location loc = base.clone().add(v);
            player.spawnParticle(Particle.DUST, loc, 1, 0, 0, 0, 0, dust, true);
        }
    }

    // --------------------- ITEM → TIER MAPPING ---------------------

    /**
     * Returns the reinforcement tier that the player's held item corresponds to
     * for both particle display and placement.  Returns null if the player is not
     * holding a reinforcement item.
     */
    public static ReinforcementTier getHeldReinforcementTier(Player player) {
        if (player == null) return null;
        Material type = player.getInventory().getItemInMainHand().getType();
        if (type == Material.STICK)        return ReinforcementTier.WOODEN;
        if (type == Material.COPPER_INGOT) return ReinforcementTier.LIGHT;
        if (type == Material.IRON_INGOT)   return ReinforcementTier.HEAVY;
        return null;
    }

    // --------------------- REINFORCEMENT METHODS ---------------------

    /**
     * Add a reinforcement to a block.
     * <p>
     * Tier upgrade rules:
     * <ul>
     *   <li>WOODEN – rejected if block already has any reinforcement</li>
     *   <li>LIGHT  – rejected if block is already LIGHT or HEAVY; replaces WOODEN</li>
     *   <li>HEAVY  – rejected if block is already HEAVY; replaces WOODEN or LIGHT</li>
     * </ul>
     *
     * @return true if the reinforcement was successfully applied
     */
    public static boolean addReinforcement(Player player, Block block, ReinforcementTier tier) {
        Reinforcement existing = getReinforcement(block);

        // Determine if placement is allowed
        if (existing != null) {
            if (tier == ReinforcementTier.WOODEN) return false;                     // can never overwrite
            if (existing.getTier() == tier) return false;                           // same tier
            if (existing.getTier().isHigherThan(tier)) return false;               // downgrade
            // Otherwise this is an upgrade – remove old before adding new
            removeReinforcementInternal(block);
        }

        Chunk chunk = block.getChunk();
        Set<Reinforcement> blocks = getReinforcedBlocks(chunk);
        if (blocks == null) blocks = new HashSet<>();

        long worldTime = block.getWorld().getFullTime();
        Reinforcement reinforcement = new Reinforcement(block.getLocation().toVector(), tier, worldTime);
        if (!blocks.add(reinforcement)) return false;

        saveChunk(chunk, blocks);

        // Award XP
        double xp = switch (tier) {
            case WOODEN -> SpecializationConfig.getReinforcementConfig().get("WOODEN_XP", Double.class);
            case LIGHT  -> SpecializationConfig.getReinforcementConfig().get("LIGHT_XP", Double.class);
            case HEAVY  -> SpecializationConfig.getReinforcementConfig().get("HEAVY_XP", Double.class);
        };
        Player target = player != null ? player : block.getWorld().getNearbyPlayers(block.getLocation(), 4.0)
                .stream().findFirst().orElse(null);
        if (target != null) {
            CustomPlayer cp = CoreUtil.getPlayer(target.getUniqueId());
            if (cp != null) cp.addSkillXp(SkillType.BUILDER, xp);
        }
        return true;
    }

    public static boolean addReinforcement(Block block, ReinforcementTier tier) {
        return addReinforcement(null, block, tier);
    }

    // ---- Legacy boolean overloads (for callers not yet migrated) ----

    public static boolean addReinforcement(Player player, Block block, boolean isHeavy) {
        return addReinforcement(player, block, isHeavy ? ReinforcementTier.HEAVY : ReinforcementTier.LIGHT);
    }

    public static boolean addReinforcement(Block block, boolean isHeavy) {
        return addReinforcement(null, block, isHeavy ? ReinforcementTier.HEAVY : ReinforcementTier.LIGHT);
    }

    /**
     * Add reinforcement without awarding XP.  Used for piston / falling-block transfers.
     */
    public static boolean addReinforcementSilent(Block b, ReinforcementTier tier, long preservedTick) {
        Reinforcement existing = getReinforcement(b);
        if (existing != null) {
            if (tier == ReinforcementTier.WOODEN) return false;
            if (existing.getTier() == tier) return false;
            if (existing.getTier().isHigherThan(tier)) return false;
            removeReinforcementInternal(b);
        }
        Chunk c = b.getChunk();
        Set<Reinforcement> r = getReinforcedBlocks(c);
        if (r == null) r = new HashSet<>();
        if (!r.add(new Reinforcement(b.getLocation().toVector(), tier, preservedTick))) return false;
        saveChunk(c, r);
        return true;
    }

    /** Legacy overload – uses current world time. */
    public static boolean addReinforcementSilent(Block b, ReinforcementTier tier) {
        return addReinforcementSilent(b, tier, b.getWorld().getFullTime());
    }

    /** Legacy boolean overload for piston/falling-block code. */
    public static boolean addReinforcementSilent(Block b, boolean h) {
        return addReinforcementSilent(b, h ? ReinforcementTier.HEAVY : ReinforcementTier.LIGHT);
    }

    public static void removeReinforcement(Block block) {
        removeReinforcementInternal(block);
    }

    private static void removeReinforcementInternal(Block block) {
        Chunk chunk = block.getChunk();
        Set<Reinforcement> reinforcedBlocks = getReinforcedBlocks(chunk);
        if (reinforcedBlocks == null) return;
        // equals() is location-only, so a single dummy instance with any tier matches
        reinforcedBlocks.remove(new Reinforcement(block.getLocation().toVector(), ReinforcementTier.WOODEN, 0));
        saveChunk(chunk, reinforcedBlocks);
    }

    // --------------------- QUERY METHODS ---------------------

    public static boolean isReinforced(Block block) {
        return getReinforcement(block) != null;
    }

    public static boolean isHeavilyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.getTier() == ReinforcementTier.HEAVY;
    }

    public static boolean isLightlyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.getTier() == ReinforcementTier.LIGHT;
    }

    public static boolean isWoodenReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.getTier() == ReinforcementTier.WOODEN;
    }

    /**
     * Returns the tier of the block's reinforcement, or null if not reinforced.
     */
    public static ReinforcementTier getReinforcementTier(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null ? r.getTier() : null;
    }

    /**
     * Returns the full Reinforcement object for a block, or null if not reinforced.
     * Performs a lazy decay check — if the reinforcement has expired, it is removed
     * on access and null is returned.
     */
    public static Reinforcement getReinforcement(Block block) {
        Set<Reinforcement> blocks = getReinforcedBlocks(block.getChunk());
        if (blocks == null) return null;
        for (Reinforcement r : blocks) {
            if (r.getLocation().getBlockX() == block.getX() &&
                    r.getLocation().getBlockY() == block.getY() &&
                    r.getLocation().getBlockZ() == block.getZ()) {

                // Lazy decay: check if this reinforcement has expired
                if (isExpired(r, block.getWorld().getFullTime())) {
                    blocks.remove(r);
                    saveChunk(block.getChunk(), blocks);
                    return null;
                }

                return r;
            }
        }
        return null;
    }

    /**
     * Returns true if the given reinforcement has exceeded its decay time.
     */
    private static boolean isExpired(Reinforcement r, long currentTick) {
        // Migrated data gets a fresh timestamp — not expired yet
        if (r.getPlacedAtTick() == ReinforcementTypeAdapter.MIGRATED_SENTINEL) return false;

        long decayTicks = switch (r.getTier()) {
            case WOODEN -> {
                try {
                    yield SpecializationConfig.getReinforcementConfig().get("WOODEN_DECAY_TICKS", Long.class);
                } catch (Exception e) {
                    yield 48000L; // fallback: 2 MC days
                }
            }
            case LIGHT -> {
                try {
                    yield SpecializationConfig.getReinforcementConfig().get("LIGHT_DECAY_TICKS", Long.class);
                } catch (Exception e) {
                    yield 336000L; // fallback: 14 MC days
                }
            }
            case HEAVY -> -1L; // never decays
        };

        return decayTicks > 0 && (currentTick - r.getPlacedAtTick()) >= decayTicks;
    }

    // --------------------- DECAY ---------------------

    /**
     * Scans all cached chunks and removes reinforcements whose decay time has elapsed.
     * Called periodically from the scheduled task.
     */
    private static void processDecay() {
        World overworld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (overworld == null) return;
        long currentTick = overworld.getFullTime();

        long woodenDecayTicks = SpecializationConfig.getReinforcementConfig().get("WOODEN_DECAY_TICKS", Long.class);
        long lightDecayTicks  = SpecializationConfig.getReinforcementConfig().get("LIGHT_DECAY_TICKS", Long.class);

        // Iterate over a snapshot of cached chunks
        Map<Chunk, Set<Reinforcement>> snapshot = new HashMap<>(cachedReinforcements);

        for (Map.Entry<Chunk, Set<Reinforcement>> entry : snapshot.entrySet()) {
            Chunk chunk = entry.getKey();
            Set<Reinforcement> set = entry.getValue();
            if (set == null || set.isEmpty()) continue;

            boolean modified = false;
            Iterator<Reinforcement> it = set.iterator();
            while (it.hasNext()) {
                Reinforcement r = it.next();

                // Migrate sentinel timestamps from old data
                if (r.getPlacedAtTick() == ReinforcementTypeAdapter.MIGRATED_SENTINEL) {
                    r.setPlacedAtTick(currentTick);
                    modified = true;
                }

                long decayTicks = switch (r.getTier()) {
                    case WOODEN -> woodenDecayTicks;
                    case LIGHT  -> lightDecayTicks;
                    case HEAVY  -> -1L; // never decays
                };

                if (decayTicks > 0 && (currentTick - r.getPlacedAtTick()) >= decayTicks) {
                    it.remove();
                    modified = true;
                }
            }

            if (modified) {
                saveChunk(chunk, set);
            }
        }
    }

    // --------------------- PERSISTENCE HELPERS ---------------------

    private static void saveChunk(Chunk chunk, Set<Reinforcement> set) {
        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, GSON.toJson(set));
        cachedReinforcements.put(chunk, set);
        cachedLists.put(chunk, new ArrayList<>(set));
        cacheTime.put(chunk, System.currentTimeMillis());
    }

    private static Set<Reinforcement> getReinforcedBlocks(Chunk chunk) {
        if (cachedReinforcements.containsKey(chunk)) {
            cacheTime.put(chunk, System.currentTimeMillis());
            return cachedReinforcements.get(chunk);
        }
        if (!chunk.getPersistentDataContainer().has(namespacedKey)) return null;
        String s = chunk.getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
        Set<Reinforcement> set = GSON.fromJson(s, new TypeToken<Set<Reinforcement>>() {}.getType());
        if (set != null && !set.isEmpty()) {
            cachedReinforcements.put(chunk, set);
            cacheTime.put(chunk, System.currentTimeMillis());
        }
        return set;
    }
}

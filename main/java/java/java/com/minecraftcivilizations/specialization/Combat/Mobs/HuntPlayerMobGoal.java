package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.minecraftcivilizations.specialization.Combat.Instinct;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.MathUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HuntPlayerMobGoal implements Goal<Mob> {
    public static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey(Specialization.getInstance(), "monster_hunt_player"));

    private static final Predicate<Player> VALID_GAMEMODE = p ->
            p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE;

    private static final Predicate<Entity> NO_ENTITIES = entity -> false;

    private final Mob mob;
    private final double follow_range;
    private final double follow_range_sq;
    private final boolean breaks_blocks;
    private final float mobBreakScalar;

    // Cached config values (read once at construction, not every tick)
    private final double blockBreakChancePercentage;
    private final List<Pattern> blockIgnorePatterns;

    private float breakAmount = 0f;
    private float cachedBlockModifier = 0f;
    private Block block;
    private Collection<Player> nearbyPlayers;

    private Entity last_target = null;
    private int tick = 0;
    private int reacquire_tick = 0;

    public HuntPlayerMobGoal(Mob mob, double follow_range, boolean breaks_blocks, double break_scalar) {
        this.mob = mob;
        this.follow_range = follow_range;
        this.follow_range_sq = follow_range * follow_range;
        this.breaks_blocks = breaks_blocks;
        this.mobBreakScalar = (float) break_scalar;

        // Cache config values once instead of reading from config every tick
        this.blockBreakChancePercentage = SpecializationConfig.getMobConfig()
                .get("BLOCK_BREAK_CHANCE_PERCENTAGE", Double.class);

        List<String> regexStrings = SpecializationConfig.getMobConfig().get(
                "BLOCK_BREAK_IGNORE_LIST_REGEX",
                new com.google.gson.reflect.TypeToken<List<String>>() {}
        );
        this.blockIgnorePatterns = (regexStrings == null) ? List.of() :
                regexStrings.stream()
                        .map(s -> Pattern.compile(s.trim()))
                        .toList();
    }

    /**
     * Returns true if this mob is in the overworld during daytime.
     * Used to disable enhanced AI so mobs behave like vanilla during the day.
     */
    private boolean isOverworldDaytime() {
        return mob.getWorld().getEnvironment() == World.Environment.NORMAL && mob.getWorld().isDayTime();
    }

    @Override
    public boolean shouldActivate() {
        // During overworld daytime, don't activate — let vanilla AI handle targeting
        if (isOverworldDaytime()) return false;
        boolean b = mob.getTarget() == null || (breaks_blocks && mob.getTarget() != null);
        Debug.broadcast("huntplayer", "should activate: "+(b?"<green>TRUE":"<red>FALSE"));
        return b;
    }

    @Override
    public boolean shouldStayActive() {
        // Deactivate when day arrives so vanilla AI takes over
        if (isOverworldDaytime()) return false;
        return true;
    }

    @Override
    public void start() {
        Debug.broadcast("huntplayer", "<green>starting hunt player for " + mob.getName());
        if (mob.getTarget() == null) {
            calculateNewTarget(true);
        } else {
            last_target = mob.getTarget();
            resetBreakState();
        }
        tick = 0;
        reacquire_tick = 0;
    }

    public void calculateNewTarget(boolean detect_guardsman_level) {
        double guardsman_zone_radius_base = 6;
        double guardsman_zone_radius_per_lvl = 2;

        // Cache mob location outside the lambda to avoid repeated allocation
        final Location mobLoc = mob.getLocation();

        mob.getLocation().getNearbyPlayers(follow_range).stream()
                .filter(VALID_GAMEMODE)
                .min((p1, p2) -> {
                    CustomPlayer player1 = CoreUtil.getPlayer(p1);
                    CustomPlayer player2 = CoreUtil.getPlayer(p2);

                    int lvl1 = player1.getSkillLevel(SkillType.GUARDSMAN);
                    int lvl2 = player2.getSkillLevel(SkillType.GUARDSMAN);

                    double zone1 = guardsman_zone_radius_base + (lvl1 * guardsman_zone_radius_per_lvl);
                    double zone2 = guardsman_zone_radius_base + (lvl2 * guardsman_zone_radius_per_lvl);

                    double d1sq = p1.getLocation().distanceSquared(mobLoc);
                    double d2sq = p2.getLocation().distanceSquared(mobLoc);

                    boolean p1_in_zone = d1sq <= (zone1 * zone1);
                    boolean p2_in_zone = d2sq <= (zone2 * zone2);

                    if (lvl1 != lvl2) {
                        if (lvl1 > lvl2) {
                            if (!p1_in_zone && p2_in_zone) return 1;
                            if (p1_in_zone && !p2_in_zone) return -1;
                            return Integer.compare(lvl2, lvl1);
                        } else {
                            if (!p2_in_zone && p1_in_zone) return -1;
                            if (p2_in_zone && !p1_in_zone) return 1;
                            return Integer.compare(lvl2, lvl1);
                        }
                    }

                    // Equal guardsman — fall back to distance
                    if (mob.getWorld().equals(p1.getWorld()) && mob.getWorld().equals(p2.getWorld())) {
                        return Double.compare(d1sq, d2sq);
                    }
                    return 0;
                })
                .ifPresent(player -> mob.setTarget(player));
    }


    @Override
    public void tick() {
        // Belt-and-suspenders: if day arrived between shouldStayActive checks, bail out
        if (isOverworldDaytime()) {
            resetBreakState();
            return;
        }

        tick++;
        if (tick % 40 == 0) {
            if (mob.getTarget() == null) {
                calculateNewTarget(false);
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                calculateNewTarget(true);
            }
            if (mob.getTarget() == null) {
                calculateNewTarget(false);
                return;
            }
            tick = 0;
        }

        // We have a target — detect target changes and reset state
        Entity current_target = mob.getTarget();
        if (current_target == null) {
            return;
        // FIX: was comparing target.getLocation() to itself (always 0) — never dropped targets
        } else if (mob.getLocation().distanceSquared(current_target.getLocation()) > follow_range_sq) {
            mob.setTarget(null);
        } else if (current_target != last_target) {
            last_target = current_target;
            resetBreakState();
            reacquire_tick = 0;
        }

        if (!breaks_blocks) return;

        // Do not attempt breaking during daytime
        if (mob.getWorld().isDayTime()) {
            resetBreakState();
            return;
        }

        double reach_distance = 3;

        // Occasional chance check before attempting to break anything.
        if (block == null) {
            if (ThreadLocalRandom.current().nextDouble() > blockBreakChancePercentage / 100d) {
                reacquire_tick++;
                if (reacquire_tick < 20) return;
                reacquire_tick = 0;
            }

            if (!mob.getWorld().equals(current_target.getWorld())) return;

            double spray = 0.35;

            Vector vectorToPlayer = current_target.getLocation().subtract(mob.getEyeLocation()).toVector().normalize().add(MathUtils.randomVectorCentered(spray)).normalize();
            RayTraceResult result = mob.getWorld().rayTrace(mob.getEyeLocation().add(MathUtils.randomVectorCentered(0.15)), vectorToPlayer.normalize(), reach_distance, FluidCollisionMode.NEVER, true, .15, NO_ENTITIES);
            if (result == null || result.getHitBlock() == null) {
                Location leglocation = mob.getLocation().add(0,0.5,0);
                vectorToPlayer = current_target.getLocation().subtract(leglocation).toVector();
                result = mob.getWorld().rayTrace(leglocation, vectorToPlayer.normalize(), reach_distance, FluidCollisionMode.NEVER, true, .15, NO_ENTITIES);
                if (result == null || result.getHitBlock() == null) {
                    return;
                }
            }

            Block hit_block = result.getHitBlock();
            if (hit_block == null) return;
            if (hit_block.getType() == Material.AIR) return;
            if (isBlockIgnored(hit_block)) return;
            if (ReinforcementManager.isReinforced(hit_block)) return;

            float blockModifier = getBlockModifier(hit_block);
            if (blockModifier == 0) return;

            if(hit_block.getLocation().getY() < mob.getLocation().getY()-0.25){
                if(current_target.getLocation().getY()>=hit_block.getLocation().getY()){
                    return;
                }
            }

            block = hit_block;
            cachedBlockModifier = blockModifier;
            breakAmount = 0f;
            nearbyPlayers = block.getLocation().getNearbyPlayers(16).stream()
                    .filter(player -> player.getGameMode().equals(GameMode.SURVIVAL))
                    .collect(Collectors.toSet());

            if(mob instanceof Monster mon) {
                Instinct.onMobStartBreakingBlock(mon);
            }
        }

        if (block != null) {
            if (block.getType() == Material.AIR || block.getLocation().distance(mob.getLocation()) > reach_distance) {
                if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
                    nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), 0));
                }
                resetBreakState();
                return;
            }

            float breakPercentagePerTick = 5f;
            if (ReinforcementManager.isReinforced(block)){
                // Safety net: if a block was reinforced after the mob started breaking it,
                // stop immediately. All reinforced blocks are immune to mob breaking.
                resetBreakState();
                return;
            }
            breakPercentagePerTick *= cachedBlockModifier;
            breakPercentagePerTick *= mobBreakScalar;
            breakAmount += breakPercentagePerTick / 100f;

            if (breakAmount >= 1.0f) {
                if (block.getBlockData().getMaterial().getHardness() > 0) {
                    block.breakNaturally(true, false);
                }
                resetBreakState();
                return;
            }

            if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
                nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), breakAmount));
            }
        }
    }

    private void resetBreakState() {
        block = null;
        breakAmount = 0f;
        cachedBlockModifier = 0f;
        nearbyPlayers = null;
    }

    /**
     * Returns true if the block matches any pre-compiled pattern from config.
     */
    private boolean isBlockIgnored(Block block) {
        if (blockIgnorePatterns.isEmpty()) return false;
        String material_name = block.getType().name();
        for (Pattern pattern : blockIgnorePatterns) {
            if (pattern.matcher(material_name).matches()) return true;
        }
        return false;
    }

    /**
     * Returns how quickly a block breaks. Higher values break faster.
     * Return 0 to make a block unbreakable (hard blacklist).
     */
    private float getBlockModifier(Block block) {
        Material type = block.getType();

        switch(type){
            case DIRT:
            case GRAVEL:
            case SAND:
                return 2.5f;
            case GRASS_BLOCK:
            case MUD:
            case MYCELIUM:
            case PODZOL:
                return 2.0f;
            case NETHERRACK:
            case CRIMSON_NYLIUM:
            case WARPED_NYLIUM:
            case SOUL_SOIL:
            case SOUL_SAND:
                return 1.75f;
            case CLAY:
            case FARMLAND:
            case COARSE_DIRT:
            case ROOTED_DIRT:
            case MAGMA_BLOCK:
            case MELON:
            case PUMPKIN:
            case CARVED_PUMPKIN:
            case JACK_O_LANTERN:
                return 1.55f;
            case CACTUS:
                return 1.25f;
            case COBBLESTONE:
            case COBBLED_DEEPSLATE:
            case DRIPSTONE_BLOCK:
            case TUFF:
                return 0.8f;
            case STONE:
            case DEEPSLATE:
            case BASALT:
            case POLISHED_BASALT:
            case SMOOTH_BASALT:
                return 0.6f;
            case ANDESITE:
            case DIORITE:
            case GRANITE:
                return 0.6f;
            case NETHERITE_BLOCK:
                return 0.05f;
            case CHEST:
                return 0.9f;
            case BARREL:
                return 0.6f;
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case SMITHING_TABLE:
            case STONECUTTER:
            case GRINDSTONE:
            case COBWEB:
                return 0.5f;
            case SLIME_BLOCK:
            case HONEY_BLOCK:
                return 0.4f;
            case TINTED_GLASS:
            case COPPER_BLOCK:
                return 0.25f;
            case IRON_BLOCK:
                return 0.125f;
            case DIAMOND_BLOCK:
                return 0.075f;
            case ANVIL:
                return 0.1f;
            case CHIPPED_ANVIL:
                return 0.2f;
            case DAMAGED_ANVIL:
                return 0.3f;
            case ENCHANTING_TABLE:
            case JUKEBOX:
            case RESPAWN_ANCHOR:
            case LODESTONE:
                return 0.125f;
            case CRYING_OBSIDIAN:
            case OBSIDIAN:
                return 0.025f;
            case ANCIENT_DEBRIS:
            case DEEPSLATE_DIAMOND_ORE:
            case DIAMOND_ORE:
            case BEDROCK:
            case SPAWNER:
                return 0;
        }
        String name = type.name();
        if(name.contains("BLACKSTONE")){
            return 0.6f;
        }
        if(name.contains("_LEAVES")){
            return 2.5f;
        }
        if(name.contains("_LOGS")){
            return 0.75f;
        }
        if(name.contains("GLASS")){
            return 1.5f;
        }
        if(name.contains("_ORE") || name.endsWith("_CONCRETE")){
            return 0.125f;
        }
        return 1.0f;
    }

    @Override
    public void stop() {
        Debug.broadcast("huntplayer", "<gray> stopping");
        last_target = null;
        resetBreakState();
        // Clear the enhanced target so vanilla AI can re-evaluate from scratch
        mob.setTarget(null);
        Goal.super.stop();
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.TARGET, GoalType.MOVE);
    }
}

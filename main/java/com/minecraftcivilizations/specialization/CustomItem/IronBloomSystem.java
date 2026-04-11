package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages the iron heating/working system.
 *
 * <h3>Iron Bloom Flow (crushed iron ore -> wrought iron ingot):</h3>
 * <pre>
 *   Furnace:  crushed_iron_ore   -> iron_bloom (1/25, White Hot)
 *   Anvil:    bloom(24) + bronze_hammer -> 4 strikes to ingot
 *             bloom(24) + iron_hammer   -> 2 strikes to ingot
 *   Reheat:   bloom in furnace   -> Yellow Hot
 * </pre>
 *
 * <h3>Iron Plate Flow (wrought iron ingot -> iron plateset):</h3>
 * <pre>
 *   Furnace:  wrought_iron_ingot     -> heated ingot (Yellow Hot)
 *   Anvil:    hot ingot + hammer     -> armor_plate_iron (cold)
 *   Furnace:  armor_plate_iron       -> heated plate (Yellow Hot)
 *   Anvil:    hot plate + hot plate  -> iron_armor_plateset (cold)
 * </pre>
 *
 * <h3>Heat degradation (any item with heat PDC):</h3>
 * <pre>
 *   White Hot -> Yellow Hot -> Orange Hot -> Red Hot -> Cold  (7s each)
 * </pre>
 */
public class IronBloomSystem implements Listener {

    // ─── PDC Keys ───
    public static final NamespacedKey HEAT_LEVEL_KEY =
        new NamespacedKey("specialization", "bloom_heat");
    public static final NamespacedKey HEAT_TIME_KEY =
        new NamespacedKey("specialization", "bloom_heat_time");
    /** Block PDC: marks that the next FurnaceExtractEvent on this furnace skips XP. */
    public static final NamespacedKey NO_XP_SMELT_KEY =
        new NamespacedKey("specialization", "no_xp_smelt");
    /**
     * Sentinel stored in HEAT_TIME_KEY while an iron item sits in an actively-
     * burning furnace. getHeatLevel() returns the stored level without decaying
     * when this sentinel is present.
     */
    private static final long HEAT_PAUSED = Long.MAX_VALUE;

    // ─── Heat Levels ───
    public static final int WHITE_HOT  = 5;
    public static final int YELLOW_HOT = 4;
    public static final int ORANGE_HOT = 3;
    public static final int RED_HOT    = 2;
    public static final int HOT        = 1;
    public static final int COLD       = 0;

    private static final long HEAT_DEGRADE_MS = 7_000L;
    private static final int REHEAT_TICKS_PER_LEVEL = 80; // 4 seconds per heat level

    // ─── Bloom Durability (bronze=4 strikes, iron=2 strikes) ───
    public static final int BLOOM_MAX_DAMAGE = 25;
    private static final int DAMAGE_STEP_1    = 24;  // initial bloom damage
    private static final int DAMAGE_STEP_3    = 4;   // threshold to yield ingot
    // Bronze hammer: 24→17→10→3→yield (4 strikes)
    private static final int BRONZE_HAMMER_DMG       = 7;
    // Iron hammer: 24→4→yield (2 strikes)
    private static final int IRON_HAMMER_DMG         = 20;
    // Steel hammer: 24→0→yield (1 strike)
    private static final int STEEL_HAMMER_BLOOM_DMG  = 24;

    // ─── Workable Iron Component (tool heads / armor pieces) ───
    // raw_id -> [finished_id, strikesNeeded]
    // strikes = 2 × plate sets used in the crafting recipe
    // raw_id -> [finished_id, strikesNeeded]
    private static final Map<String, Object[]> IRON_RAW_COMPONENTS = Map.ofEntries(
        Map.entry("iron_helm_raw",         new Object[]{"iron_helm",         10}),
        Map.entry("iron_breastplate_raw",  new Object[]{"iron_breastplate",  16}),
        Map.entry("iron_greaves_raw",      new Object[]{"iron_greaves",      14}),
        Map.entry("iron_sabaton_raw",      new Object[]{"iron_sabaton",       8}),
        Map.entry("iron_sword_head_raw",   new Object[]{"iron_sword_head",    4}),
        Map.entry("iron_axe_head_raw",     new Object[]{"iron_axe_head",      6}),
        Map.entry("iron_pickaxe_head_raw", new Object[]{"iron_pickaxe_head",  6}),
        Map.entry("iron_hoe_head_raw",     new Object[]{"iron_hoe_head",      4}),
        Map.entry("iron_shovel_head_raw",  new Object[]{"iron_shovel_head",   2}),
        Map.entry("iron_hammer_head_raw", new Object[]{"iron_hammer_head",   8})
    );
    // raw_id -> [finished_id, plateSets] — durability = 2 × plateSets × 6
    static final Map<String, Object[]> STEEL_RAW_COMPONENTS = Map.ofEntries(
        Map.entry("steel_helm_raw",         new Object[]{"steel_helm",          5}),
        Map.entry("steel_breastplate_raw",  new Object[]{"steel_breastplate",   8}),
        Map.entry("steel_greaves_raw",      new Object[]{"steel_greaves",       7}),
        Map.entry("steel_sabaton_raw",      new Object[]{"steel_sabaton",       4}),
        Map.entry("steel_sword_head_raw",   new Object[]{"steel_sword_head",    2}),
        Map.entry("steel_axe_head_raw",     new Object[]{"steel_axe_head",      3}),
        Map.entry("steel_pickaxe_head_raw", new Object[]{"steel_pickaxe_head",  3}),
        Map.entry("steel_hoe_head_raw",     new Object[]{"steel_hoe_head",      2}),
        Map.entry("steel_shovel_head_raw",  new Object[]{"steel_shovel_head",   1}),
        Map.entry("steel_hammer_head_raw", new Object[]{"steel_hammer_head",   4})
    );

    // ─── Steel Annealing / Quench / Temper PDC Keys ───
    public static final NamespacedKey ANNEALED_KEY =
        new NamespacedKey("specialization", "steel_annealed");
    public static final NamespacedKey BAD_QUENCH_KEY =
        new NamespacedKey("specialization", "bad_quench");
    /** Tracks whether the item has been re-annealed (second anneal lasts until 90%). */
    public static final NamespacedKey REANNEALED_KEY =
        new NamespacedKey("specialization", "steel_reannealed");
    public static final NamespacedKey HARDENED_KEY =
        new NamespacedKey("specialization", "steel_hardened");
    public static final NamespacedKey TEMPER_TIER_KEY =
        new NamespacedKey("specialization", "temper_tier");
    public static final NamespacedKey TEMPER_START_KEY =
        new NamespacedKey("specialization", "temper_start");

    // ─── Temper Tiers (0=none, 1-5) ───
    public static final int TEMPER_NONE         = 0;
    public static final int TEMPER_PALE_YELLOW  = 1;
    public static final int TEMPER_STRAW_YELLOW = 2;
    public static final int TEMPER_PURPLE       = 3;
    public static final int TEMPER_BLUE         = 4;
    public static final int TEMPER_GREY         = 5;
    private static final long TEMPER_INTERVAL_MS = 2_000L; // 2 seconds per tier

    // ─── Pig Iron Durability (iron=4 strikes, steel=2 strikes) ───
    public static final int PIG_IRON_MAX_DAMAGE   = 25;
    private static final int PIG_IRON_DAMAGE_STEP_1 = 24;  // initial pig iron damage
    private static final int PIG_IRON_DAMAGE_STEP_3 = 4;   // threshold to yield steel ingot
    // Iron hammer: 24→17→10→3→yield (4 strikes)
    private static final int PIG_IRON_IRON_HAMMER_DMG  = 7;
    // Steel hammer: 24→4→yield (2 strikes)
    private static final int PIG_IRON_STEEL_HAMMER_DMG = 20;

    // ─── Hammer IDs ───
    private static final Set<String> HAMMER_IDS = Set.of(
        "bronze_hammer", "iron_hammer", "steel_hammer"
    );
    private static final Set<String> PIG_IRON_HAMMER_IDS = Set.of(
        "iron_hammer", "steel_hammer"
    );

    // ─── Active forge-spark particle tasks (keyed by player UUID) ───
    private final Map<UUID, BukkitTask> activeParticleTasks = new HashMap<>();
    /** Location keys of furnaces whose input-slot iron items are currently heat-paused. */
    private final java.util.Set<String> pausedFurnaces = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    // ═════════════════════════════════════════════════════════════
    //  Init
    // ═════════════════════════════════════════════════════════════

    public IronBloomSystem() {
        startHeatDegradationTask();
        startTemperingMonitorTask();
    }

    // ═════════════════════════════════════════════════════════════
    //  Heat Utilities
    // ═════════════════════════════════════════════════════════════

    /**
     * Returns the effective heat level by computing degradation from the stored
     * timestamp so items cool correctly even when outside player inventory
     * (e.g. sitting in an anvil or chest slot).
     */
    public static int getHeatLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return COLD;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer stored = pdc.get(HEAT_LEVEL_KEY, PersistentDataType.INTEGER);
        if (stored == null || stored <= COLD) return COLD;
        Long timestamp = pdc.get(HEAT_TIME_KEY, PersistentDataType.LONG);
        if (timestamp == null) return stored;
        if (timestamp == HEAT_PAUSED) return stored; // item in active furnace — no decay
        long elapsed = System.currentTimeMillis() - timestamp;
        int degraded = (int) (elapsed / HEAT_DEGRADE_MS);
        return Math.max(COLD, stored - degraded);
    }

    /** Returns the heat level stored in PDC without applying time-based degradation. */
    public static int getStoredHeatLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return COLD;
        Integer val = item.getItemMeta().getPersistentDataContainer()
            .get(HEAT_LEVEL_KEY, PersistentDataType.INTEGER);
        return val != null ? val : COLD;
    }

    public static void setHeatLevel(ItemStack item, int level) {
        if (item == null) return;
        item.editMeta(meta -> {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            if (level <= COLD) {
                pdc.remove(HEAT_LEVEL_KEY);
                pdc.remove(HEAT_TIME_KEY);
            } else {
                pdc.set(HEAT_LEVEL_KEY, PersistentDataType.INTEGER, level);
                pdc.set(HEAT_TIME_KEY, PersistentDataType.LONG, System.currentTimeMillis());
            }
            updateHeatLore(meta, level);
        });
    }

    private static void updateHeatLore(ItemMeta meta, int level) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains("Hot"));
        String heatText = getHeatText(level);
        if (heatText != null) {
            lore.add(0, heatText);
        }
        meta.setLore(lore.isEmpty() ? null : lore);
    }

    public static String getHeatText(int level) {
        return switch (level) {
            case WHITE_HOT  -> "\u00A7f\u00A7lWhite Hot";
            case YELLOW_HOT -> "\u00A7e\u00A7lYellow Hot";
            case ORANGE_HOT -> "\u00A76\u00A7lOrange Hot";
            case RED_HOT    -> "\u00A7c\u00A7lRed Hot";
            case HOT        -> "\u00A74\u00A7lHot";
            default -> null;
        };
    }

    public static boolean isWorkable(int heatLevel) {
        return heatLevel >= ORANGE_HOT;
    }

    // ─── Item type checks ───

    /** Any item that currently has heat PDC data (bloom, sheet, heated ingot). */
    public static boolean hasHeat(ItemStack item) {
        return getHeatLevel(item) > COLD;
    }

    public static boolean isIronBloom(ItemStack item) {
        return hasCustomId(item, "iron_bloom");
    }

    public static boolean isIronPlate(ItemStack item) {
        return hasCustomId(item, "armor_plate_iron");
    }

    /** Hot iron plate: armor_plate_iron at Orange Hot or hotter (workable). */
    public static boolean isHotIronPlate(ItemStack item) {
        return isIronPlate(item) && isWorkable(getHeatLevel(item));
    }

    /** Heated iron ingot usable for plate-making: either a plain vanilla IRON_INGOT
     *  or a wrought_iron_ingot custom item, both requiring at least RED_HOT heat
     *  (workability is further checked in prepareIngotToPlateResult). */
    public static boolean isHeatedIronIngot(ItemStack item) {
        if (item == null || item.getType() != Material.IRON_INGOT) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        // Accept: no custom ID (vanilla ingot) OR wrought_iron_ingot specifically
        if (ci != null && !"wrought_iron_ingot".equals(ci.getId())) return false;
        return getHeatLevel(item) > COLD;
    }

    public static boolean isSteelPlate(ItemStack item) {
        return hasCustomId(item, "armor_plate_steel");
    }

    public static boolean isHotSteelPlate(ItemStack item) {
        return isSteelPlate(item) && isWorkable(getHeatLevel(item));
    }

    /** Heated steel_ingot custom item with at least some heat. */
    public static boolean isHeatedSteelIngot(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && "steel_ingot".equals(ci.getId()) && getHeatLevel(item) > COLD;
    }

    /** Returns true if item is an unworked iron component (_raw custom ID). */
    public static boolean isRawIronComponent(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && IRON_RAW_COMPONENTS.containsKey(ci.getId());
    }

    /** Returns true if item is an unworked steel component (_raw custom ID). */
    public static boolean isRawSteelComponent(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && STEEL_RAW_COMPONENTS.containsKey(ci.getId());
    }

    public static boolean isHammer(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && HAMMER_IDS.contains(ci.getId());
    }

    public static boolean isPigIron(ItemStack item) {
        return hasCustomId(item, "pig_iron");
    }

    public static boolean isPigIronHammer(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && PIG_IRON_HAMMER_IDS.contains(ci.getId());
    }

    /** Iron or steel hammer only — no bronze. For all steel processing. */
    public static boolean isSteelProcessHammer(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && PIG_IRON_HAMMER_IDS.contains(ci.getId());
    }

    private static boolean hasCustomId(ItemStack item, String id) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && id.equals(ci.getId());
    }

    // ═════════════════════════════════════════════════════════════
    //  Heat Degradation Task
    // ═════════════════════════════════════════════════════════════

    /**
     * Scans all player inventories every second and syncs the heat lore label
     * to the effective (time-computed) heat level.  The stored PDC level and
     * timestamp are never modified here — getHeatLevel() derives the current
     * level from elapsed time, so no periodic PDC write is needed.
     * When an item reaches COLD the heat PDC keys are removed entirely.
     */
    private void startHeatDegradationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item == null || !item.hasItemMeta()) continue;

                        // Unfreeze any HEAT_PAUSED items in player inventory
                        // (catches items taken from furnace output via cancelAndReplace)
                        Long ts = item.getItemMeta().getPersistentDataContainer()
                            .get(HEAT_TIME_KEY, PersistentDataType.LONG);
                        if (ts != null && ts == HEAT_PAUSED) {
                            unfreezeHeat(item);
                        }

                        Integer stored = item.getItemMeta().getPersistentDataContainer()
                            .get(HEAT_LEVEL_KEY, PersistentDataType.INTEGER);
                        if (stored == null || stored <= COLD) continue;
                        int effective = getHeatLevel(item); // derived from timestamp
                        if (effective == stored) continue;  // nothing to update yet
                        item.editMeta(meta -> {
                            if (effective <= COLD) {
                                meta.getPersistentDataContainer().remove(HEAT_LEVEL_KEY);
                                meta.getPersistentDataContainer().remove(HEAT_TIME_KEY);
                            }
                            updateHeatLore(meta, effective);
                        });

                        // Steel anneal restoration: when a steel component reaches COLD,
                        // grant Annealed if below 90% durability and not already annealed.
                        // Mark as re-annealed so the second anneal lasts until 90%.
                        if (effective <= COLD && isRawSteelComponent(item) && !isAnnealed(item)) {
                            double pct = getDurabilityPercent(item);
                            if (pct < 0.9) {
                                setAnnealed(item, true);
                                item.editMeta(m -> m.getPersistentDataContainer()
                                    .set(REANNEALED_KEY, PersistentDataType.BYTE, (byte) 1));
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 20L, 20L);
    }

    // ═════════════════════════════════════════════════════════════
    //  Furnace: creation + heating + reheating
    // ═════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        CustomItem custom = CustomItemManager.getInstance().getCustomItem(source);
        Block block = event.getBlock();

        // ─── crushed_iron_ore -> Iron Bloom (White Hot, 1/25) ───
        if (custom != null && "crushed_iron_ore".equals(custom.getId())) {
            cancelAndReplace(event, block, buildFreshBloom(DAMAGE_STEP_1, WHITE_HOT));
            return;
        }

        // ─── steel_blend → Pig Iron (blast furnace only, White Hot, 1/25) ───
        if (custom != null && "steel_blend".equals(custom.getId())) {
            if (block.getType() != Material.BLAST_FURNACE) {
                event.setCancelled(true);
                return;
            }
            cancelAndReplace(event, block, buildFreshPigIron(PIG_IRON_DAMAGE_STEP_1, WHITE_HOT));
            return;
        }

        // ─── iron_bloom reheat -> Yellow Hot ───
        if (custom != null && "iron_bloom".equals(custom.getId())) {
            tagNoXp(block);
            ItemStack reheated = source.clone();
            setHeatLevel(reheated, YELLOW_HOT);
            reheated.editMeta(m -> m.getPersistentDataContainer()
                .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
            cancelAndReplace(event, block, reheated);
            return;
        }

        // ─── pig_iron reheat → Yellow Hot ───
        if (custom != null && "pig_iron".equals(custom.getId())) {
            tagNoXp(block);
            ItemStack reheated = source.clone();
            setHeatLevel(reheated, YELLOW_HOT);
            reheated.editMeta(m -> m.getPersistentDataContainer()
                .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
            cancelAndReplace(event, block, reheated);
            return;
        }

        // ─── wrought_iron_ingot / steel_ingot → Yellow Hot (ingot → plate path) ───
        if (custom != null && ("wrought_iron_ingot".equals(custom.getId())
                || "steel_ingot".equals(custom.getId()))) {
            tagNoXp(block);
            ItemStack heated = source.clone();
            heated.setAmount(1);
            heated.editMeta(m -> m.setMaxStackSize(1));
            setHeatLevel(heated, YELLOW_HOT);
            heated.editMeta(m -> m.getPersistentDataContainer()
                .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
            cancelAndReplace(event, block, heated);
            return;
        }

        // ─── Over-tempered steel piece → reset to raw component at 90% durability ───
        if (custom != null && STEEL_FINISHED_IDS.contains(custom.getId())) {
            if (!isOverTemperedPiece(source)) {
                // Not over-tempered — block reheating
                event.setCancelled(true);
                return;
            }
            tagNoXp(block);
            String rawId = custom.getId() + "_raw";
            Object[] rawData = STEEL_RAW_COMPONENTS.get(rawId);
            if (rawData == null) { event.setCancelled(true); return; }
            int plateSets = (int) rawData[1];
            int maxDmg = 2 * plateSets * 6;
            int dmgAt90 = (int) (maxDmg * 0.1); // floor so durability is >= 90%

            // Inherit existing bad quench level (do not increment)
            Integer existingBQ = source.getItemMeta().getPersistentDataContainer()
                .get(BAD_QUENCH_KEY, PersistentDataType.INTEGER);
            int newBQ = existingBQ != null ? existingBQ : 0;

            // Build the raw component manually (avoids createItemStack PDC conflicts)
            ItemStack rawItem = new ItemStack(Material.IRON_INGOT);
            rawItem.editMeta(m -> {
                // Set custom item ID
                m.getPersistentDataContainer().set(
                    new NamespacedKey("specialization", "custom_item_id"),
                    PersistentDataType.STRING, rawId);
                // Display name + model match the finished piece
                m.setDisplayName(custom.getDisplayName());
                // customModelData matches finished piece for same visual
                var cmd = m.getCustomModelDataComponent();
                List<String> strings = new ArrayList<>();
                strings.add(custom.getId()); // e.g. "steel_helm"
                cmd.setStrings(strings);
                m.setCustomModelDataComponent(cmd);
                // Durability at 90%
                if (m instanceof Damageable d) {
                    d.setMaxDamage(maxDmg);
                    d.setDamage(dmgAt90);
                }
                m.setMaxStackSize(1);
                // Bad quench (inherited, not incremented)
                if (newBQ > 0) {
                    m.getPersistentDataContainer().set(BAD_QUENCH_KEY, PersistentDataType.INTEGER, newBQ);
                }
                List<String> lore = new ArrayList<>();
                if (newBQ > 0) {
                    lore.add(ChatColor.RED + "Bad Quench " + toRoman(newBQ));
                }
                // At 90%+ durability = ready for quenching
                int pctInt = (int) ((maxDmg - dmgAt90) * 100.0 / maxDmg);
                lore.add(ChatColor.AQUA + "" + pctInt + "% Complete");
                lore.add(ChatColor.YELLOW + "Ready for Quenching");
                int overPct = Math.max(0, pctInt - 90);
                lore.add(ChatColor.RED + "Overworked Penalty " + (overPct * 10) + "%");
                m.setLore(lore);
            });
            setHeatLevel(rawItem, YELLOW_HOT);
            cancelAndReplace(event, block, rawItem);
            return;
        }

        // ─── Vanilla IRON_INGOT: block — players must use the bloom system ───
        if (source.getType() == Material.IRON_INGOT && custom == null) {
            event.setCancelled(true);
            return;
        }

        // ─── armor_plate_iron / armor_plate_steel reheat -> Yellow Hot (unstackable) ───
        if (custom != null && ("armor_plate_iron".equals(custom.getId())
                || "armor_plate_steel".equals(custom.getId()))) {
            tagNoXp(block);
            ItemStack reheated = source.clone();
            reheated.setAmount(1);
            reheated.editMeta(m -> m.setMaxStackSize(1));
            setHeatLevel(reheated, YELLOW_HOT);
            reheated.editMeta(m -> m.getPersistentDataContainer()
                .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
            cancelAndReplace(event, block, reheated);
            return;
        }

        // ─── Raw iron/steel component reheat → Yellow Hot ───
        if (custom != null && (IRON_RAW_COMPONENTS.containsKey(custom.getId())
                || STEEL_RAW_COMPONENTS.containsKey(custom.getId()))) {
            tagNoXp(block);
            ItemStack heated = source.clone();
            heated.setAmount(1);
            heated.editMeta(m -> m.setMaxStackSize(1));
            setHeatLevel(heated, YELLOW_HOT);
            heated.editMeta(m -> m.getPersistentDataContainer()
                .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
            cancelAndReplace(event, block, heated);
            return;
        }

        // ─── Block other custom items on IRON_INGOT base ───
        if (source.getType() == Material.IRON_INGOT && custom != null) {
            event.setCancelled(true);
            return;
        }

        // ─── Block other CARROT_ON_A_STICK (whetstone, etc.) ───
        if (source.getType() == Material.CARROT_ON_A_STICK) {
            event.setCancelled(true);
            return;
        }

        // ─── Coal → Coal Coke (regular furnace only) ───
        if (source.getType() == Material.COAL) {
            if (custom != null && "coal_coke".equals(custom.getId())) {
                event.setCancelled(true); // Don't re-smelt coal_coke
                return;
            }
            if (custom == null) {
                if (block.getType() == Material.BLAST_FURNACE) {
                    event.setCancelled(true); // Coal can't be processed in blast furnace
                    return;
                }
                CustomItem cokeDef = CustomItemManager.getInstance().getCustomItem("coal_coke");
                ItemStack coke = cokeDef != null ? cokeDef.createItemStack() : new ItemStack(Material.COAL);
                cancelAndReplace(event, block, coke);
                return;
            }
            event.setCancelled(true); // Block other custom COAL items from smelting
        }
    }

    /**
     * Cancels the vanilla smelt, immediately resets cook time to prevent re-firing,
     * then schedules a task that consumes 1 input and places the custom result.
     * This avoids both the "no output" bug (Paper rejecting custom-component results
     * via setResult) and the duplication bug (vanilla result can't merge with existing
     * custom output, so input isn't consumed but runTask still replaces output).
     */
    private void cancelAndReplace(FurnaceSmeltEvent event, Block block, ItemStack result) {
        event.setCancelled(true);

        // Reset cook time immediately so the furnace doesn't re-fire the event
        // before our runTask executes on the next tick.
        if (block.getState() instanceof org.bukkit.block.Furnace furnace) {
            furnace.setCookTime((short) 0);
            furnace.update(true);
        }

        final ItemStack resultCopy = result.clone();
        final Location loc = block.getLocation();
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            Block b = loc.getBlock();
            if (!(b.getState() instanceof org.bukkit.block.Furnace furnace)) return;

            // Consume 1 from input slot
            ItemStack smelting = furnace.getInventory().getSmelting();
            if (smelting != null) {
                if (smelting.getAmount() > 1) {
                    smelting.setAmount(smelting.getAmount() - 1);
                    furnace.getInventory().setSmelting(smelting);
                } else {
                    furnace.getInventory().setSmelting(null);
                }
            }

            // Place custom result in output — stack with existing if compatible
            ItemStack existing = furnace.getInventory().getResult();
            if (existing != null && existing.getType() != Material.AIR
                    && existing.isSimilar(resultCopy)
                    && existing.getAmount() + resultCopy.getAmount() <= existing.getMaxStackSize()) {
                existing.setAmount(existing.getAmount() + resultCopy.getAmount());
                furnace.getInventory().setResult(existing);
            } else {
                furnace.getInventory().setResult(resultCopy);
            }
        });
    }

    /**
     * Builds an iron bloom from scratch with proper damage and heat.
     * Bypasses createItemStack() to avoid any issues with the furnace
     * system stripping custom components from the result.
     */
    private ItemStack buildFreshBloom(int damage, int heatLevel) {
        ItemStack bloom = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = bloom.getItemMeta();

        meta.setDisplayName("\u00A77Iron Bloom");
        meta.setItemModel(new NamespacedKey("steel", "iron_bloom"));
        meta.setMaxStackSize(1);

        // Set custom item ID PDC so CustomItemManager recognizes it
        meta.getPersistentDataContainer().set(
            new NamespacedKey("specialization", "custom_item_id"),
            PersistentDataType.STRING,
            "iron_bloom"
        );

        // Set durability
        if (meta instanceof Damageable d) {
            d.setMaxDamage(BLOOM_MAX_DAMAGE);
            d.setDamage(damage);
        }

        // Set heat PDC + lore directly on this meta (before setItemMeta)
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(HEAT_LEVEL_KEY, PersistentDataType.INTEGER, heatLevel);
        pdc.set(HEAT_TIME_KEY, PersistentDataType.LONG, System.currentTimeMillis());
        updateHeatLore(meta, heatLevel);

        bloom.setItemMeta(meta);
        return bloom;
    }

    private ItemStack buildWroughtIronIngot() {
        CustomItem wroughtDef = CustomItemManager.getInstance().getCustomItem("wrought_iron_ingot");
        if (wroughtDef != null) {
            return wroughtDef.createItemStack();
        }
        ItemStack fallback = new ItemStack(Material.IRON_INGOT);
        fallback.editMeta(meta -> meta.setDisplayName("\u00A77Wrought Iron Ingot"));
        return fallback;
    }

    /**
     * Adjusts cook time when furnace starts smelting heatable items.
     * <ul>
     *   <li>armor_plate_iron: 4s per level to Yellow Hot</li>
     *   <li>Iron bloom with heat: 4s per level to Yellow Hot</li>
     *   <li>Cold bloom: 8s default (2 levels × 4s)</li>
     *   <li>Non-bloom CARROT_ON_A_STICK: blocked</li>
     *   <li>Other custom items on IRON_INGOT base: blocked</li>
     * </ul>
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceStart(FurnaceStartSmeltEvent event) {
        ItemStack source = event.getSource();
        CustomItem custom = CustomItemManager.getInstance().getCustomItem(source);

        // ─── IRON_INGOT: handle wrought_iron_ingot and armor_plate_iron ───
        if (source.getType() == Material.IRON_INGOT) {
            if (custom != null && ("armor_plate_iron".equals(custom.getId())
                    || "armor_plate_steel".equals(custom.getId()))) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else if (custom != null && ("wrought_iron_ingot".equals(custom.getId())
                    || "steel_ingot".equals(custom.getId()))) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else if (custom != null && (IRON_RAW_COMPONENTS.containsKey(custom.getId())
                    || STEEL_RAW_COMPONENTS.containsKey(custom.getId()))) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else if (custom != null && STEEL_FINISHED_IDS.contains(custom.getId())) {
                // Reheat finished steel for requench after over-tempering
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else {
                // Vanilla iron ingots and all other custom IRON_INGOT items — block
                event.setTotalCookTime(Integer.MAX_VALUE);
            }
            return;
        }

        // ─── CARROT_ON_A_STICK: bloom / pig iron reheating ───
        if (source.getType() == Material.CARROT_ON_A_STICK) {
            if (custom != null && "iron_bloom".equals(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
                return;
            }
            if (custom != null && "pig_iron".equals(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
                return;
            }

            // Not a bloom or pig iron — block
            event.setTotalCookTime(Integer.MAX_VALUE);
            return;
        }

        // ─── COAL: coal → coal_coke (regular furnace, 40 seconds) ───
        if (source.getType() == Material.COAL) {
            if (custom != null && "coal_coke".equals(custom.getId())) {
                event.setTotalCookTime(Integer.MAX_VALUE); // Block re-smelting
                return;
            }
            if (custom == null) {
                if (event.getBlock().getType() == Material.BLAST_FURNACE) {
                    event.setTotalCookTime(Integer.MAX_VALUE);
                    return;
                }
                event.setTotalCookTime(800); // 40 seconds
                return;
            }
            event.setTotalCookTime(Integer.MAX_VALUE);
            return;
        }

        // ─── RAW_IRON: steel_blend → pig_iron (blast furnace only) ───
        if (source.getType() == Material.RAW_IRON) {
            if (custom != null && "steel_blend".equals(custom.getId())) {
                if (event.getBlock().getType() != Material.BLAST_FURNACE) {
                    event.setTotalCookTime(Integer.MAX_VALUE);
                    return;
                }
                event.setTotalCookTime(400); // 20 seconds in blast furnace
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Anvil: Bloom + Hammer, Heated Ingot + Hammer, Hot Plate + Hot Plate
    // ═════════════════════════════════════════════════════════════

    /**
     * Prepares anvil result for all custom forge recipes:
     * <ul>
     *   <li>bloom (slot 0) + hammer (slot 1)  -> worked bloom or Wrought Iron Ingot</li>
     *   <li>heated ingot (slot 0) + hammer (slot 1) -> armor_plate_iron</li>
     *   <li>hot plate (slot 0) + hot plate (slot 1) -> iron_armor_plateset</li>
     * </ul>
     * Also blocks the vanilla "repair" that fires when two CARROT_ON_A_STICK items
     * (bloom + hammer) are placed in the wrong order.
     */
    // Minimum Blacksmith level required for iron forge recipes (EXPERT = 3)
    private static final int MIN_BLACKSMITH_LEVEL = SkillLevel.EXPERT.ordinal();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        AnvilInventory inv = event.getInventory();
        ItemStack first  = inv.getItem(0);
        ItemStack second = inv.getItem(1);
        UUID uid = player.getUniqueId();

        // ─── Level gate: forge recipes require Blacksmith Expert ───
        boolean isIronRecipe = isIronBloom(first) || isHeatedIronIngot(first)
            || isRawIronComponent(first) || (isHotIronPlate(first) && isHotIronPlate(second))
            || (isPigIron(first) && isPigIronHammer(second))
            || (isHeatedSteelIngot(first) && isSteelProcessHammer(second))
            || (isHotSteelPlate(first) && isHotSteelPlate(second))
            || (isRawSteelComponent(first) && isSteelProcessHammer(second));
        if (isIronRecipe) {
            CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(uid);
            if (cp != null && cp.getSkillLevel(SkillType.BLACKSMITH) < MIN_BLACKSMITH_LEVEL) {
                event.setResult(null);
                stopParticleTask(uid);
                return;
            }
        }

        // ─── Block invalid CARROT_ON_A_STICK combos ───
        // Only fires when BOTH slots are CARROT_ON_A_STICK — e.g. hammer in slot 0
        // and bloom in slot 1 (wrong order), or two hammers, etc.
        // A hammer (COTAS) in slot 1 alongside an iron ingot in slot 0 is valid
        // and must NOT be caught here.
        boolean firstIsCotas  = first  != null && first.getType()  == Material.CARROT_ON_A_STICK;
        boolean secondIsCotas = second != null && second.getType() == Material.CARROT_ON_A_STICK;
        if (firstIsCotas && secondIsCotas) {
            boolean validCombo = (isIronBloom(first) && isHammer(second))
                || (isPigIron(first) && isPigIronHammer(second));
            if (!validCombo) {
                event.setResult(null);
                stopParticleTask(uid);
                return;
            }
        }
        // Block non-bloom/pig-iron CARROT_ON_A_STICK in slot 0 (whetstone, stray hammer, etc.)
        if (firstIsCotas && !isIronBloom(first) && !isPigIron(first)) {
            event.setResult(null);
            stopParticleTask(uid);
            return;
        }

        // ─── Hot steel plate + hot steel plate → steel_armor_plateset ───
        if (isHotSteelPlate(first) && isHotSteelPlate(second)) {
            prepareHotSteelPlatesToPlatesetResult(event, inv);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        // ─── Hot iron plate + hot iron plate → iron_armor_plateset ───
        if (isHotIronPlate(first) && isHotIronPlate(second)) {
            prepareHotPlatesToPlatesetResult(event, inv);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        // ─── Pig iron + hammer (iron/steel only) → worked pig iron or steel ingot ───
        if (isPigIron(first) && isPigIronHammer(second)) {
            preparePigIronResult(event, inv, first, second);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (second == null || !isHammer(second)) {
            stopParticleTask(uid);
            return;
        }

        if (isIronBloom(first)) {
            prepareBloomResult(event, inv, first, second);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (isHeatedSteelIngot(first) && isSteelProcessHammer(second)) {
            prepareSteelIngotToPlateResult(event, inv, first);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (isHeatedIronIngot(first)) {
            prepareIngotToPlateResult(event, inv, first);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (isRawSteelComponent(first) && isSteelProcessHammer(second)) {
            prepareSteelRawComponentResult(event, inv, first, second);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (isRawIronComponent(first)) {
            prepareRawComponentResult(event, inv, first, second);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        stopParticleTask(uid);
    }

    @SuppressWarnings("deprecation")
    private void prepareBloomResult(PrepareAnvilEvent event, AnvilInventory inv, ItemStack bloom, ItemStack hammer) {
        int heat = getHeatLevel(bloom);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }

        int currentDamage = DAMAGE_STEP_1;
        if (bloom.getItemMeta() instanceof Damageable d) {
            currentDamage = d.getDamage();
        }

        // Determine damage per strike based on hammer type
        int damagePerWork = BRONZE_HAMMER_DMG; // default bronze
        if (hammer != null) {
            CustomItem hammerCI = CustomItemManager.getInstance().getCustomItem(hammer);
            if (hammerCI != null) {
                if ("steel_hammer".equals(hammerCI.getId())) {
                    damagePerWork = STEEL_HAMMER_BLOOM_DMG; // 1 strike
                } else if ("iron_hammer".equals(hammerCI.getId())) {
                    damagePerWork = IRON_HAMMER_DMG; // 2 strikes
                }
            }
        }

        ItemStack result;

        if (currentDamage <= DAMAGE_STEP_3) {
            // Current damage already at threshold → yield the Wrought Iron Ingot
            result = buildWroughtIronIngot();
        } else {
            int newDamage = currentDamage - damagePerWork;
            if (newDamage <= 0) {
                // Hammer powerful enough to finish in one strike → yield directly
                result = buildWroughtIronIngot();
            } else {
                result = bloom.clone();
                int newHeat = Math.max(COLD, heat - 1);
                result.editMeta(meta -> {
                    if (meta instanceof Damageable d) {
                        d.setDamage(newDamage);
                    }
                });
                setHeatLevel(result, newHeat);
            }
        }

        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    /** Heated iron ingot (Orange+) + hammer -> armor_plate_iron (cold). */
    private void prepareIngotToPlateResult(PrepareAnvilEvent event, AnvilInventory inv, ItemStack ingot) {
        int heat = getHeatLevel(ingot);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }

        CustomItem plateDef = CustomItemManager.getInstance().getCustomItem("armor_plate_iron");
        if (plateDef == null) {
            event.setResult(null);
            return;
        }

        event.setResult(plateDef.createItemStack());
        event.getView().setRepairCost(0);
    }

    /**
     * Raw component (Orange+) + hammer → reduced damage; at damage ≤1 → finished component.
     * Bronze = 1 dmg/strike, Iron = 2 dmg/strike, Steel = 3 dmg/strike.
     */
    private void prepareRawComponentResult(PrepareAnvilEvent event, AnvilInventory inv,
                                           ItemStack raw, ItemStack hammer) {
        int heat = getHeatLevel(raw);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }

        CustomItem ci = CustomItemManager.getInstance().getCustomItem(raw);
        if (ci == null) { event.setResult(null); return; }

        int currentDamage = 0;
        if (raw.getItemMeta() instanceof Damageable d) currentDamage = d.getDamage();

        // Determine damage per strike based on hammer type
        int damagePerWork = 1; // bronze default
        if (hammer != null) {
            CustomItem hammerCI = CustomItemManager.getInstance().getCustomItem(hammer);
            if (hammerCI != null) {
                if ("steel_hammer".equals(hammerCI.getId())) damagePerWork = 3;
                else if ("iron_hammer".equals(hammerCI.getId())) damagePerWork = 2;
            }
        }

        ItemStack result;
        if (currentDamage <= 1) {
            // Already at threshold — yield finished component
            String finishedId = (String) IRON_RAW_COMPONENTS.get(ci.getId())[0];
            CustomItem finishedDef = CustomItemManager.getInstance().getCustomItem(finishedId);
            if (finishedDef == null) { event.setResult(null); return; }
            result = finishedDef.createItemStack();
        } else {
            int newDamage = currentDamage - damagePerWork;
            if (newDamage <= 1) {
                // Hammer powerful enough to finish — yield directly
                String finishedId = (String) IRON_RAW_COMPONENTS.get(ci.getId())[0];
                CustomItem finishedDef = CustomItemManager.getInstance().getCustomItem(finishedId);
                if (finishedDef == null) { event.setResult(null); return; }
                result = finishedDef.createItemStack();
            } else {
                result = raw.clone();
                int newHeat = Math.max(COLD, heat - 1);
                final int nd = newDamage;
                result.editMeta(meta -> { if (meta instanceof Damageable d) d.setDamage(nd); });
                setHeatLevel(result, newHeat);
            }
        }

        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    /** Heated steel ingot (Orange+) + hammer → armor_plate_steel (cold). */
    private void prepareSteelIngotToPlateResult(PrepareAnvilEvent event, AnvilInventory inv, ItemStack ingot) {
        int heat = getHeatLevel(ingot);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }
        CustomItem plateDef = CustomItemManager.getInstance().getCustomItem("armor_plate_steel");
        if (plateDef == null) { event.setResult(null); return; }
        event.setResult(plateDef.createItemStack());
        event.getView().setRepairCost(0);
    }

    /** Two hot steel plates (Orange+) → steel_armor_plateset (cold). No hammer needed. */
    private void prepareHotSteelPlatesToPlatesetResult(PrepareAnvilEvent event, AnvilInventory inv) {
        CustomItem platesetDef = CustomItemManager.getInstance().getCustomItem("steel_armor_plateset");
        if (platesetDef == null) { event.setResult(null); return; }
        event.setResult(platesetDef.createItemStack());
        event.getView().setRepairCost(0);
    }

    /** Two hot iron plates (Orange+) → iron_armor_plateset (cold). No hammer needed. */
    private void prepareHotPlatesToPlatesetResult(PrepareAnvilEvent event, AnvilInventory inv) {
        CustomItem platesetDef = CustomItemManager.getInstance().getCustomItem("iron_armor_plateset");
        if (platesetDef == null) {
            event.setResult(null);
            return;
        }
        event.setResult(platesetDef.createItemStack());
        event.getView().setRepairCost(0);
    }

    // ═════════════════════════════════════════════════════════════
    //  Steel Raw Component Smithing (heat-variable, anneal lifecycle)
    // ═════════════════════════════════════════════════════════════

    /** Steel damage-per-strike lookup: [hammerType][heatLevel] */
    private static int getSteelDamagePerStrike(String hammerId, int heatLevel) {
        boolean steel = "steel_hammer".equals(hammerId);
        return switch (heatLevel) {
            case YELLOW_HOT -> steel ? 10 : 6;
            case ORANGE_HOT -> steel ?  8 : 5;
            case RED_HOT    -> steel ?  6 : 4;
            case HOT        -> steel ?  4 : 2;
            default         -> steel ?  2 : 1; // COLD
        };
    }

    // ─── Anneal helpers ───

    public static boolean isAnnealed(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte val = item.getItemMeta().getPersistentDataContainer()
            .get(ANNEALED_KEY, PersistentDataType.BYTE);
        return val != null && val == 1;
    }

    public static void setAnnealed(ItemStack item, boolean annealed) {
        if (item == null) return;
        item.editMeta(m -> {
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.removeIf(line -> ChatColor.stripColor(line).equals("Annealed"));
            if (annealed) {
                pdc.set(ANNEALED_KEY, PersistentDataType.BYTE, (byte) 1);
                lore.add("\u00A7aAnnealed");
            } else {
                pdc.remove(ANNEALED_KEY);
            }
            m.setLore(lore.isEmpty() ? null : lore);
        });
    }

    public static double getDurabilityPercent(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        if (!(item.getItemMeta() instanceof Damageable d)) return 0;
        int max = d.hasMaxDamage() ? d.getMaxDamage() : 0;
        if (max <= 0) return 0;
        return (max - d.getDamage()) / (double) max;
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(num);
        };
    }

    /** Finished steel component IDs that can be tempered. */
    private static final Set<String> STEEL_FINISHED_IDS = Set.of(
        "steel_helm", "steel_breastplate", "steel_greaves", "steel_sabaton",
        "steel_sword_head", "steel_axe_head", "steel_pickaxe_head",
        "steel_hoe_head", "steel_shovel_head", "steel_hammer_head"
    );

    /** Over-tempered piece: steel finished component with BAD_QUENCH but no HARDENED (stripped by overtemper). */
    public static boolean isOverTemperedPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        if (ci == null || !STEEL_FINISHED_IDS.contains(ci.getId())) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return !pdc.has(HARDENED_KEY, PersistentDataType.BYTE)
            && pdc.has(BAD_QUENCH_KEY, PersistentDataType.INTEGER);
    }

    public static boolean isHardenedSteelPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        if (ci == null || !STEEL_FINISHED_IDS.contains(ci.getId())) return false;
        return item.getItemMeta().getPersistentDataContainer().has(HARDENED_KEY, PersistentDataType.BYTE);
    }

    public static int getTemperTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return TEMPER_NONE;
        Integer val = item.getItemMeta().getPersistentDataContainer()
            .get(TEMPER_TIER_KEY, PersistentDataType.INTEGER);
        return val != null ? val : TEMPER_NONE;
    }

    public static String getTemperText(int tier) {
        return switch (tier) {
            case TEMPER_PALE_YELLOW  -> "\u00A7eTempered";
            case TEMPER_STRAW_YELLOW -> "\u00A76Tempered";
            case TEMPER_PURPLE       -> "\u00A75Tempered";
            case TEMPER_BLUE         -> "\u00A79Tempered";
            case TEMPER_GREY         -> "\u00A78Tempered";
            default -> null;
        };
    }

    private static void updateTemperLore(ItemStack item, int tier) {
        item.editMeta(m -> {
            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Tempered"));
            String text = getTemperText(tier);
            if (text != null) lore.add(text);
            m.setLore(lore.isEmpty() ? null : lore);
            m.getPersistentDataContainer().set(TEMPER_TIER_KEY, PersistentDataType.INTEGER, tier);
        });
    }

    /**
     * Steel raw component + hammer → worked component (heat-variable damage).
     * Allows cold working. Checks anneal state for 50%/90% transitions.
     * Past 90% durability: no more anvil results — must quench.
     */
    private void prepareSteelRawComponentResult(PrepareAnvilEvent event, AnvilInventory inv,
                                                ItemStack raw, ItemStack hammer) {
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(raw);
        if (ci == null) { event.setResult(null); return; }

        int currentDamage = 0;
        int maxDamage = 0;
        if (raw.getItemMeta() instanceof Damageable d) {
            currentDamage = d.getDamage();
            maxDamage = d.hasMaxDamage() ? d.getMaxDamage() : 0;
        }
        if (maxDamage <= 0) { event.setResult(null); return; }

        // At full durability — must quench, no more anvil work
        if (currentDamage <= 0) {
            event.setResult(null);
            return;
        }

        int heat = getHeatLevel(raw);
        CustomItem hammerCI = CustomItemManager.getInstance().getCustomItem(hammer);
        String hammerId = hammerCI != null ? hammerCI.getId() : "iron_hammer";
        int damagePerWork = getSteelDamagePerStrike(hammerId, heat);

        int newDamage = Math.max(0, currentDamage - damagePerWork);

        ItemStack result = raw.clone();
        int newHeat = Math.max(COLD, heat - 1);
        final int nd = newDamage;
        final int md = maxDamage;
        result.editMeta(meta -> {
            if (meta instanceof Damageable d) d.setDamage(nd);
        });
        setHeatLevel(result, newHeat);

        // Anneal transitions:
        //   First anneal (from creation): lost at 50%
        //   Re-anneal (after cooling): lasts until 90%
        double newDurPct = (md - nd) / (double) md;
        if (isAnnealed(result)) {
            boolean isReannealed = false;
            if (result.hasItemMeta()) {
                Byte re = result.getItemMeta().getPersistentDataContainer()
                    .get(REANNEALED_KEY, PersistentDataType.BYTE);
                isReannealed = re != null && re == 1;
            }
            double loseAt = isReannealed ? 0.9 : 0.5;
            if (newDurPct >= loseAt) {
                setAnnealed(result, false);
            }
        }

        // Update progress/quench lore
        updateSteelProgressLore(result, newDurPct);

        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    /** Updates durability %, ready for quenching, and overwork penalty lore on steel raw components. */
    private void updateSteelProgressLore(ItemStack item, double durPct) {
        int pctInt = (int) (durPct * 100);
        item.editMeta(m -> {
            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            // Strip old progress/quench lines
            lore.removeIf(line -> {
                String s = ChatColor.stripColor(line);
                return s.endsWith("% Complete") || s.startsWith("Ready for Quenching")
                    || s.startsWith("Overworked Penalty");
            });

            if (pctInt >= 90) {
                lore.add(ChatColor.AQUA + "" + pctInt + "% Complete");
                lore.add(ChatColor.YELLOW + "Ready for Quenching");
                int overPct = pctInt - 90;
                int penalty = overPct * 10;
                lore.add(ChatColor.RED + "Overworked Penalty " + penalty + "%");
            } else if (pctInt >= 80) {
                lore.add(ChatColor.AQUA + "" + pctInt + "% Complete");
            }
            m.setLore(lore.isEmpty() ? null : lore);
        });
    }

    /**
     * Handles taking the result from the anvil for all custom forge recipes.
     * Consumes ingredients, damages the hammer (where applicable), and
     * emits a colored spark burst at the anvil block.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilResultTake(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory anvil)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType() == Material.AIR) return;

        ItemStack first  = anvil.getItem(0);
        ItemStack second = anvil.getItem(1);

        boolean isBloomOrIngotRecipe = (isIronBloom(first) || isHeatedIronIngot(first)) && isHammer(second);
        boolean isSteelIngotRecipe   = isHeatedSteelIngot(first) && isSteelProcessHammer(second);
        boolean isPlatesetRecipe     = (isHotIronPlate(first) && isHotIronPlate(second))
            || (isHotSteelPlate(first) && isHotSteelPlate(second));
        boolean isComponentRecipe    = isRawIronComponent(first) && isHammer(second);
        boolean isSteelComponentRecipe = isRawSteelComponent(first) && isSteelProcessHammer(second);
        boolean isPigIronRecipe      = isPigIron(first) && isPigIronHammer(second);

        if (!isBloomOrIngotRecipe && !isSteelIngotRecipe && !isPlatesetRecipe
                && !isComponentRecipe && !isSteelComponentRecipe && !isPigIronRecipe) return;

        // Level gate check (duplicate guard against client-side bypass)
        if (!(event.getWhoClicked() instanceof Player)) return;
        CustomPlayer cpCheck = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(event.getWhoClicked().getUniqueId());
        if (cpCheck != null && cpCheck.getSkillLevel(SkillType.BLACKSMITH) < MIN_BLACKSMITH_LEVEL) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = resultItem.clone();

        // ─── Steel anneal break check (BEFORE consuming inputs) ───
        if (isSteelComponentRecipe && !isAnnealed(first)) {
            if (new java.util.Random().nextDouble() < 0.25) {
                // Break: destroy workpiece, return all base platesets
                CustomItem ci = CustomItemManager.getInstance().getCustomItem(first);
                if (ci != null) {
                    Object[] data = STEEL_RAW_COMPONENTS.get(ci.getId());
                    if (data != null) {
                        int plateSets = (int) data[1];
                        int dropCount = plateSets;
                        anvil.setItem(0, null);
                        damageHammerInSlot(anvil, 1, player);
                        if (dropCount > 0) {
                            CustomItem platesetDef = CustomItemManager.getInstance()
                                .getCustomItem("steel_armor_plateset");
                            if (platesetDef != null) {
                                ItemStack drop = platesetDef.createItemStack(dropCount);
                                player.getWorld().dropItemNaturally(player.getLocation(), drop);
                            }
                        }
                        CustomPlayer cpBreak = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
                        if (cpBreak != null) cpBreak.addSkillXp(SkillType.BLACKSMITH, -50.0);
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
                        stopParticleTask(player.getUniqueId());
                        return;
                    }
                }
            }
        }

        // Capture before slots are cleared
        Location anvilLoc = anvil.getLocation();
        int heatLevel     = getHeatLevel(first);

        if (isPlatesetRecipe) {
            // Consume both hot plates; no hammer in this recipe
            anvil.setItem(0, null);
            anvil.setItem(1, null);
        } else {
            // Bloom / ingot / pig iron / component: consume workpiece, damage hammer
            anvil.setItem(0, null);
            damageHammerInSlot(anvil, 1, player);
        }

        // ─── Sparks + sound ───
        stopParticleTask(player.getUniqueId());
        emitForgeBurst(anvilLoc, heatLevel);
        Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
            if (player.isOnline()) player.stopSound(Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS);
        }, 1L);

        // ─── Give result + Blacksmith XP ───
        player.setItemOnCursor(result);
        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) {
            // Steel recipes give 2 XP only if RED_HOT or higher; HOT/COLD = 0 XP
            boolean isSteelRecipe = isPigIronRecipe || isSteelComponentRecipe
                || isSteelIngotRecipe
                || (isHotSteelPlate(first) && isHotSteelPlate(second));
            if (isSteelRecipe) {
                if (heatLevel >= RED_HOT) {
                    cp.addSkillXp(SkillType.BLACKSMITH, 2.0);
                }
            } else {
                cp.addSkillXp(SkillType.BLACKSMITH, 1.0);
            }
        }
    }

    /**
     * Stops forge-spark particles when the player closes the anvil
     * (e.g. walks away without taking the result).
     */
    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory() instanceof AnvilInventory) {
            stopParticleTask(player.getUniqueId());
        }
    }


    /**
     * When the player extracts a result from the furnace output slot,
     * unfreeze heat so normal degradation resumes from the current stored level.
     * This is the counterpart to the HEAT_PAUSED sentinel set in onFurnaceSmelt.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        // Unfreeze heat on extracted items
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            unfreezeHeat(player.getItemOnCursor());
            player.setItemOnCursor(player.getItemOnCursor());
            for (ItemStack item : player.getInventory().getContents()) {
                unfreezeHeat(item);
            }
        });
    }

    /**
     * Coal coke XP: FurnaceExtractEvent doesn't fire for items placed via
     * cancelAndReplace (vanilla furnace never tracked a completed smelt).
     * Detect coal_coke extraction via InventoryClickEvent on furnace result slot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceResultTake(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof org.bukkit.inventory.FurnaceInventory fi)) return;
        if (event.getRawSlot() != 2) return; // slot 2 = result slot
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() != Material.COAL) return;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(result);
        if (ci == null || !"coal_coke".equals(ci.getId())) return;

        // Only regular furnaces produce coal_coke
        Location loc = fi.getLocation();
        if (loc == null || loc.getBlock().getType() != Material.FURNACE) return;

        int amount = result.getAmount();
        // Grant 1 vanilla XP per coal_coke
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            player.giveExp(amount);
        });
        // Grant 3 blacksmith XP per coal_coke
        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) cp.addSkillXp(SkillType.BLACKSMITH, 3.0 * amount);
    }


    // ═════════════════════════════════════════════════════════════
    //  Heat preservation while iron items are in an active furnace
    // ═════════════════════════════════════════════════════════════

    /**
     * When a furnace begins consuming fuel, freeze heat on any hot iron item in
     * the input slot so it doesn't cool while the furnace is active.
     * A watchdog task monitors for when the fuel runs out and unpauses it then.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceBurnStart(FurnaceBurnEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Furnace furnace)) return;
        ItemStack input = furnace.getInventory().getSmelting();
        if (!isHeatableIronItem(input)) return;

        input.editMeta(m -> m.getPersistentDataContainer()
            .set(HEAT_TIME_KEY, PersistentDataType.LONG, HEAT_PAUSED));
        furnace.getInventory().setSmelting(input);

        String key = locKey(block.getLocation());
        if (pausedFurnaces.add(key)) {
            startFurnaceWatchdog(block.getLocation(), key);
        }
    }

    /**
     * Periodically checks whether the furnace is still burning.
     * Once fuel runs out, restores the heat clock so normal decay resumes.
     */
    private void startFurnaceWatchdog(Location blockLoc, String key) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!pausedFurnaces.contains(key)) { cancel(); return; }
                Block b = blockLoc.getBlock();
                if (!(b.getState() instanceof org.bukkit.block.Furnace furnace)) {
                    pausedFurnaces.remove(key); cancel(); return;
                }
                if (furnace.getBurnTime() <= 0) {
                    // Fuel exhausted — resume normal heat degradation
                    ItemStack input = furnace.getInventory().getSmelting();
                    unfreezeHeat(input);
                    if (input != null) furnace.getInventory().setSmelting(input);
                    pausedFurnaces.remove(key);
                    cancel();
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 20L, 20L);
    }

    /**
     * When a player extracts an item from the furnace input slot (slot 0),
     * restore the heat clock on whatever ends up on their cursor.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceSlotInteract(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof org.bukkit.inventory.FurnaceInventory)) return;
        if (event.getRawSlot() != 0) return;
        // Run next tick after the slot update resolves
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            if (event.getWhoClicked() instanceof Player p) {
                ItemStack cursor = p.getItemOnCursor();
                unfreezeHeat(cursor);
                p.setItemOnCursor(cursor);
            }
        });
    }

    /** Returns true for hot iron items that should have heat preserved in an active furnace. */
    private static boolean isHeatableIronItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        if (getStoredHeatLevel(item) <= COLD) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        if (ci == null) return false;
        String id = ci.getId();
        return "iron_bloom".equals(id) || "wrought_iron_ingot".equals(id)
            || "armor_plate_iron".equals(id) || "armor_plate_steel".equals(id)
            || IRON_RAW_COMPONENTS.containsKey(id)
            || STEEL_RAW_COMPONENTS.containsKey(id) || "pig_iron".equals(id)
            || STEEL_FINISHED_IDS.contains(id) || "steel_ingot".equals(id);
    }

    /** Restores the heat clock from HEAT_PAUSED to current time so the item degrades normally. */
    public static void unfreezeHeat(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        Long ts = item.getItemMeta().getPersistentDataContainer()
            .get(HEAT_TIME_KEY, PersistentDataType.LONG);
        if (ts == null || ts != HEAT_PAUSED) return;
        item.editMeta(m -> m.getPersistentDataContainer()
            .set(HEAT_TIME_KEY, PersistentDataType.LONG, System.currentTimeMillis()));
    }

    /** Tags a furnace block so the next FurnaceExtractEvent on it skips XP. */
    private static void tagNoXp(Block block) {
        var bs = block.getState();
        if (bs instanceof org.bukkit.block.TileState ts) {
            ts.getPersistentDataContainer().set(NO_XP_SMELT_KEY, PersistentDataType.BYTE, (byte) 1);
            ts.update();
        }
    }

    private static String locKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ═════════════════════════════════════════════════════════════
    //  Pig Iron: build + anvil recipe
    // ═════════════════════════════════════════════════════════════

    /**
     * Builds a fresh pig iron from scratch with proper damage and heat.
     * Mirrors buildFreshBloom for the steel processing chain.
     */
    private ItemStack buildFreshPigIron(int damage, int heatLevel) {
        ItemStack pig = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = pig.getItemMeta();

        meta.setDisplayName("\u00A77Pig Iron");
        meta.setItemModel(new NamespacedKey("steel", "pig_iron"));
        meta.setMaxStackSize(1);

        meta.getPersistentDataContainer().set(
            new NamespacedKey("specialization", "custom_item_id"),
            PersistentDataType.STRING,
            "pig_iron"
        );

        if (meta instanceof Damageable d) {
            d.setMaxDamage(PIG_IRON_MAX_DAMAGE);
            d.setDamage(damage);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(HEAT_LEVEL_KEY, PersistentDataType.INTEGER, heatLevel);
        pdc.set(HEAT_TIME_KEY, PersistentDataType.LONG, System.currentTimeMillis());
        updateHeatLore(meta, heatLevel);

        pig.setItemMeta(meta);
        return pig;
    }

    /**
     * Pig iron (Orange+) + iron/steel hammer → worked pig iron or steel ingot.
     * Iron hammer: 4 strikes (7 dmg/strike), Steel hammer: 2 strikes (20 dmg/strike).
     */
    @SuppressWarnings("deprecation")
    private void preparePigIronResult(PrepareAnvilEvent event, AnvilInventory inv,
                                      ItemStack pigIron, ItemStack hammer) {
        int heat = getHeatLevel(pigIron);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }

        int currentDamage = PIG_IRON_DAMAGE_STEP_1;
        if (pigIron.getItemMeta() instanceof Damageable d) {
            currentDamage = d.getDamage();
        }

        int damagePerWork = PIG_IRON_IRON_HAMMER_DMG; // default iron hammer (4 strikes)
        if (hammer != null) {
            CustomItem hammerCI = CustomItemManager.getInstance().getCustomItem(hammer);
            if (hammerCI != null && "steel_hammer".equals(hammerCI.getId())) {
                damagePerWork = PIG_IRON_STEEL_HAMMER_DMG; // 2 strikes
            }
        }

        ItemStack result;

        if (currentDamage <= PIG_IRON_DAMAGE_STEP_3) {
            // Current damage at threshold → yield Steel Ingot
            CustomItem steelDef = CustomItemManager.getInstance().getCustomItem("steel_ingot");
            if (steelDef != null) {
                result = steelDef.createItemStack();
            } else {
                result = new ItemStack(Material.IRON_INGOT);
                result.editMeta(meta -> meta.setDisplayName("\u00A7bSteel Ingot"));
            }
        } else {
            result = pigIron.clone();
            int newDamage = currentDamage - damagePerWork;
            int newHeat = Math.max(COLD, heat - 1);
            result.editMeta(meta -> {
                if (meta instanceof Damageable d) {
                    d.setDamage(newDamage);
                }
            });
            setHeatLevel(result, newHeat);
        }

        event.setResult(result);
        event.getView().setRepairCost(0);
    }

    // ═════════════════════════════════════════════════════════════
    //  Steel Quenching: Q-drop into water cauldron
    // ═════════════════════════════════════════════════════════════

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Item itemEntity = event.getItemDrop();
        ItemStack stack = itemEntity.getItemStack();

        // Two quench paths: raw steel at 90%+, or over-tempered hardened piece
        boolean isRawQuench = isRawSteelComponent(stack) && getDurabilityPercent(stack) >= 0.9;
        boolean isRequench = isOverTemperedPiece(stack);
        if (!isRawQuench && !isRequench) return;

        // Unfreeze heat if still paused from furnace
        unfreezeHeat(stack);
        itemEntity.setItemStack(stack);

        int heat = getHeatLevel(stack);
        if (heat < RED_HOT) return;

        Player player = event.getPlayer();
        itemEntity.setPickupDelay(40); // prevent pickup during quench check

        // Wait 20 ticks for item to fall near cauldron
        Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
            if (itemEntity.isDead() || !itemEntity.isValid()) return;

            // Re-read stack from entity (heat may have changed)
            ItemStack current = itemEntity.getItemStack();
            int currentHeat = getHeatLevel(current);
            if (currentHeat < RED_HOT) {
                itemEntity.setPickupDelay(0);
                return;
            }

            // Search for water cauldron near the item entity
            Block cauldronBlock = findWaterCauldron(itemEntity.getLocation());
            if (cauldronBlock == null) {
                itemEntity.setPickupDelay(0); // restore normal pickup
                return;
            }

            processQuench(itemEntity, current, cauldronBlock, player);
        }, 20L);
    }

    private Block findWaterCauldron(Location loc) {
        World world = loc.getWorld();
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        // Check 3×3×4 area: item position + neighbors, 2 blocks below to 1 above
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 1; dy++) {
                    Block b = world.getBlockAt(bx + dx, by + dy, bz + dz);
                    if (b.getType() == Material.WATER_CAULDRON) return b;
                }
            }
        }
        return null;
    }

    private void processQuench(Item itemEntity, ItemStack stack, Block cauldronBlock, Player player) {
        int heat = getHeatLevel(stack);
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(stack);
        if (ci == null) return;

        // Handle requench of over-tempered piece (not a raw component)
        if (isOverTemperedPiece(stack)) {
            processRequench(itemEntity, stack, cauldronBlock, player, heat);
            return;
        }

        int maxDmg = 0, currentDmg = 0;
        if (stack.getItemMeta() instanceof Damageable d) {
            maxDmg = d.hasMaxDamage() ? d.getMaxDamage() : 0;
            currentDmg = d.getDamage();
        }
        if (maxDmg <= 0) return;

        Object[] data = STEEL_RAW_COMPONENTS.get(ci.getId());
        if (data == null) return;
        String finishedId = (String) data[0];
        int plateSets = (int) data[1];

        // Read bad quench count
        Integer badQuenchCount = stack.getItemMeta().getPersistentDataContainer()
            .get(BAD_QUENCH_KEY, PersistentDataType.INTEGER);
        if (badQuenchCount == null) badQuenchCount = 0;

        // Overwork penalty: 10% chance per point over 90% to add an extra penalty level
        int threshold90dmg = (int) (maxDmg * 0.1); // floor so durability is >= 90%
        double overworkChance = 0.0;
        double durPct = (maxDmg - currentDmg) / (double) maxDmg;
        int pctAbove90 = Math.max(0, (int) ((durPct - 0.9) * 100));
        if (pctAbove90 > 0 && threshold90dmg > 2) {
            overworkChance = pctAbove90 * 0.10;
        }

        Location cauldronLoc = cauldronBlock.getLocation().add(0.5, 1.0, 0.5);
        java.util.Random rng = new java.util.Random();

        // Drain cauldron (1/3 = 1 level)
        if (cauldronBlock.getBlockData() instanceof Levelled lev) {
            int newLevel = lev.getLevel() - 1;
            if (newLevel <= 0) {
                cauldronBlock.setType(Material.CAULDRON);
            } else {
                lev.setLevel(newLevel);
                cauldronBlock.setBlockData(lev);
            }
        }

        // Steam particles + hiss sound for all quenches
        cauldronLoc.getWorld().spawnParticle(Particle.CLOUD, cauldronLoc, 20, 0.3, 0.4, 0.3, 0.05);
        player.playSound(cauldronLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);

        // Check overwork: adds extra bad_quench/poorly_hardened level
        boolean overworkPenalty = overworkChance > 0 && rng.nextDouble() < overworkChance;

        switch (heat) {
            case RED_HOT -> {
                // Too soft — bad quench, reset to 90%
                int newBQ = badQuenchCount + 1 + (overworkPenalty ? 1 : 0);
                stack.editMeta(m -> {
                    if (m instanceof Damageable d) d.setDamage(threshold90dmg);
                    m.getPersistentDataContainer().set(BAD_QUENCH_KEY, PersistentDataType.INTEGER, newBQ);
                    List<String> lore = m.hasLore() && m.getLore() != null
                        ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                    lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Bad Quench"));
                    lore.add(ChatColor.RED + "Bad Quench " + toRoman(newBQ));
                    m.setLore(lore);
                });
                setHeatLevel(stack, COLD);
                setAnnealed(stack, false);
                itemEntity.setItemStack(stack);
                itemEntity.setPickupDelay(0);
            }
            case YELLOW_HOT -> {
                // 50% chance of breaking
                if (rng.nextDouble() < 0.5) {
                    breakQuenchedItem(itemEntity, plateSets, player, cauldronLoc);
                } else {
                    int finalBQ = badQuenchCount + (overworkPenalty ? 1 : 0);
                    completeQuench(itemEntity, stack, finishedId, plateSets, finalBQ, player, cauldronLoc);
                }
            }
            case ORANGE_HOT -> {
                // Ideal — overwork only adds penalty level, doesn't break
                int finalBQ = badQuenchCount + (overworkPenalty ? 1 : 0);
                completeQuench(itemEntity, stack, finishedId, plateSets, finalBQ, player, cauldronLoc);
            }
            default -> {
                // WHITE_HOT or hotter — treat as yellow
                if (heat > YELLOW_HOT) {
                    if (rng.nextDouble() < 0.5) {
                        breakQuenchedItem(itemEntity, plateSets, player, cauldronLoc);
                    } else {
                        int finalBQ = badQuenchCount + (overworkPenalty ? 1 : 0);
                        completeQuench(itemEntity, stack, finishedId, plateSets, finalBQ, player, cauldronLoc);
                    }
                }
            }
        }
    }

    private void breakQuenchedItem(Item itemEntity, int plateSets, Player player, Location loc) {
        itemEntity.remove();
        int dropCount = plateSets;
        if (dropCount > 0) {
            CustomItem platesetDef = CustomItemManager.getInstance().getCustomItem("steel_armor_plateset");
            if (platesetDef != null) {
                ItemStack drop = platesetDef.createItemStack(dropCount);
                player.getWorld().dropItemNaturally(loc, drop);
            }
        }
        CustomPlayer cpBreak = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cpBreak != null) cpBreak.addSkillXp(SkillType.BLACKSMITH, -50.0);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
    }

    private void completeQuench(Item itemEntity, ItemStack raw, String finishedId,
                                int plateSets, int badQuenchCount, Player player, Location loc) {
        itemEntity.remove();

        CustomItem finishedDef = CustomItemManager.getInstance().getCustomItem(finishedId);
        if (finishedDef == null) return;
        ItemStack finished = finishedDef.createItemStack();

        // Add "Hardened" lore and PDC key
        finished.editMeta(m -> {
            m.getPersistentDataContainer().set(HARDENED_KEY, PersistentDataType.BYTE, (byte) 1);
            if (badQuenchCount > 0) {
                m.getPersistentDataContainer().set(BAD_QUENCH_KEY, PersistentDataType.INTEGER, badQuenchCount);
            }
            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();

            if (badQuenchCount > 0) {
                lore.add(ChatColor.RED + "Poorly Hardened " + toRoman(badQuenchCount));
                // Reduce max durability by 25% per bad quench level
                if (m instanceof Damageable d && d.hasMaxDamage()) {
                    double reduction = badQuenchCount * 0.25;
                    int newMax = (int) Math.max(1, d.getMaxDamage() * (1.0 - reduction));
                    d.setMaxDamage(newMax);
                }
            } else {
                lore.add(ChatColor.GREEN + "Hardened, brittle");
            }
            m.setLore(lore);
        });

        // Give to player or drop at cauldron
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(finished);
        } else {
            player.getWorld().dropItemNaturally(loc, finished);
        }

        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) cp.addSkillXp(SkillType.BLACKSMITH, 3.0);

        player.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.5f);
    }

    /**
     * Requench an over-tempered hardened piece: restore hardened status,
     * clear temper so it can be re-tempered. Keeps the accumulated poorly hardened level.
     */
    private void processRequench(Item itemEntity, ItemStack stack, Block cauldronBlock,
                                 Player player, int heat) {
        Location cauldronLoc = cauldronBlock.getLocation().add(0.5, 1.0, 0.5);

        // Drain cauldron
        if (cauldronBlock.getBlockData() instanceof Levelled lev) {
            int newLevel = lev.getLevel() - 1;
            if (newLevel <= 0) {
                cauldronBlock.setType(Material.CAULDRON);
            } else {
                lev.setLevel(newLevel);
                cauldronBlock.setBlockData(lev);
            }
        }

        // Steam
        cauldronLoc.getWorld().spawnParticle(Particle.CLOUD, cauldronLoc, 20, 0.3, 0.4, 0.3, 0.05);
        player.playSound(cauldronLoc, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.2f);

        if (heat < ORANGE_HOT) {
            // Not hot enough for a good requench — just reset heat
            setHeatLevel(stack, COLD);
            itemEntity.setItemStack(stack);
            itemEntity.setPickupDelay(0);
            return;
        }

        // Success: restore hardened, clear temper
        stack.editMeta(m -> {
            m.getPersistentDataContainer().set(HARDENED_KEY, PersistentDataType.BYTE, (byte) 1);
            m.getPersistentDataContainer().remove(TEMPER_TIER_KEY);
            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.removeIf(line -> {
                String s = ChatColor.stripColor(line);
                return s.startsWith("Tempered") || s.startsWith("Hardened");
            });
            lore.add(ChatColor.GREEN + "Hardened, brittle");
            m.setLore(lore);
        });
        setHeatLevel(stack, COLD);
        itemEntity.setItemStack(stack);
        itemEntity.setPickupDelay(0);
    }

    // ═════════════════════════════════════════════════════════════
    //  Steel Tempering: smoker monitoring
    // ═════════════════════════════════════════════════════════════

    /** Smoker locations currently tempering steel pieces. */
    private final Map<String, Long> temperingSmokers = new HashMap<>();

    private void startTemperingMonitorTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
            if (temperingSmokers.isEmpty()) return;
            var iter = temperingSmokers.entrySet().iterator();
            while (iter.hasNext()) {
                var entry = iter.next();
                String key = entry.getKey();
                long startTime = entry.getValue();

                // Parse location from key
                String[] parts = key.split(",");
                World world = Bukkit.getWorld(parts[0]);
                if (world == null) { iter.remove(); continue; }
                Block b = world.getBlockAt(Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (b.getType() != Material.SMOKER) { iter.remove(); continue; }
                if (!(b.getState() instanceof org.bukkit.block.Furnace smoker)) { iter.remove(); continue; }
                if (smoker.getBurnTime() <= 0) { iter.remove(); continue; }

                ItemStack input = smoker.getInventory().getSmelting();
                if (!isHardenedSteelPiece(input)) { iter.remove(); continue; }

                // Compute temper tier from elapsed time
                long elapsed = System.currentTimeMillis() - startTime;
                int tier = Math.min(TEMPER_GREY, (int)(elapsed / TEMPER_INTERVAL_MS) + 1);
                int currentTier = getTemperTier(input);
                if (tier != currentTier) {
                    updateTemperLore(input, tier);
                    smoker.getInventory().setSmelting(input); // write back

                    // Handle grey over-temper
                    if (tier == TEMPER_GREY) {
                        handleOverTemper(input, smoker);
                        iter.remove();
                    }
                }
            }
            }
        }.runTaskTimer(Specialization.getInstance(), 40L, 40L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmokerBurnForTemper(FurnaceBurnEvent event) {
        if (event.getBlock().getType() != Material.SMOKER) return;
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Furnace smoker)) return;
        ItemStack input = smoker.getInventory().getSmelting();
        if (!isHardenedSteelPiece(input)) return;

        // Only start if not already tracking (fuel re-consumption for same item)
        String key = locKey(event.getBlock().getLocation());
        temperingSmokers.putIfAbsent(key, System.currentTimeMillis());
    }

    /** When smelting completes for IRON_INGOT in a smoker, cancel it (tempering is lore-based). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmokerSmeltSteel(FurnaceSmeltEvent event) {
        if (event.getBlock().getType() != Material.SMOKER) return;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(event.getSource());
        if (ci != null && STEEL_FINISHED_IDS.contains(ci.getId())) {
            event.setCancelled(true);
        }
    }

    /**
     * Set very long cook time for steel in smoker, and start tempering tracker.
     * This fires when the smoker detects a new item to smelt — covers the case
     * where the player places the steel piece into an already-burning smoker.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSmokerStartSteel(FurnaceStartSmeltEvent event) {
        if (event.getBlock().getType() != Material.SMOKER) return;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(event.getSource());
        if (ci != null && STEEL_FINISHED_IDS.contains(ci.getId())) {
            event.setTotalCookTime(Integer.MAX_VALUE);
            // Always reset timer — this fires when a NEW item starts smelting
            String key = locKey(event.getBlock().getLocation());
            temperingSmokers.put(key, System.currentTimeMillis());
        }
    }

    private void handleOverTemper(ItemStack item, org.bukkit.block.Furnace smoker) {
        item.editMeta(m -> {
            // Add a level of Poorly Hardened
            Integer badQuench = m.getPersistentDataContainer()
                .get(BAD_QUENCH_KEY, PersistentDataType.INTEGER);
            int newLevel = (badQuench != null ? badQuench : 0) + 1;
            m.getPersistentDataContainer().set(BAD_QUENCH_KEY, PersistentDataType.INTEGER, newLevel);

            List<String> lore = m.hasLore() && m.getLore() != null
                ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.removeIf(line -> {
                String stripped = ChatColor.stripColor(line);
                return stripped.startsWith("Poorly Hardened") || stripped.startsWith("Hardened");
            });
            lore.add(ChatColor.RED + "Poorly Hardened " + toRoman(newLevel));

            // Remove hardened status — needs requench
            m.getPersistentDataContainer().remove(HARDENED_KEY);
            m.setLore(lore);
        });
        smoker.getInventory().setSmelting(item);
    }

    // ═════════════════════════════════════════════════════════════
    //  Blast Furnace: fuel restriction (coal coke only)
    // ═════════════════════════════════════════════════════════════

    /**
     * Blast furnaces may only burn coal coke as fuel.
     * All other fuels (vanilla coal, charcoal, lava buckets, etc.) are rejected.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlastFurnaceFuelCheck(FurnaceBurnEvent event) {
        if (event.getBlock().getType() != Material.BLAST_FURNACE) return;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(event.getFuel());
        if (ci == null || !"coal_coke".equals(ci.getId())) {
            event.setCancelled(true);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Shared: damage hammer in an inventory slot
    // ═════════════════════════════════════════════════════════════

    private void damageHammerInSlot(Inventory inv, int slot, Player player) {
        ItemStack hammer = inv.getItem(slot);
        if (hammer == null) return;

        boolean broken = false;
        if (hammer.getItemMeta() instanceof Damageable d) {
            int newDmg = d.getDamage() + 1;
            int maxDmg = d.hasMaxDamage() ? d.getMaxDamage() : 0;
            if (maxDmg > 0 && newDmg >= maxDmg) {
                broken = true;
            } else {
                ItemStack damagedHammer = hammer.clone();
                damagedHammer.editMeta(m -> {
                    if (m instanceof Damageable dm) {
                        dm.setDamage(newDmg);
                    }
                });
                inv.setItem(slot, damagedHammer);
            }
        }

        if (broken) {
            inv.setItem(slot, null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Forge Spark Particles
    // ═════════════════════════════════════════════════════════════

    /**
     * Returns the Bukkit Color for a given heat level, used for particle tinting.
     * Returns null for COLD (no particles).
     */
    private static Color getHeatColor(int heatLevel) {
        return switch (heatLevel) {
            case WHITE_HOT  -> Color.fromRGB(255, 255, 255);
            case YELLOW_HOT -> Color.fromRGB(255, 210, 0);
            case ORANGE_HOT -> Color.fromRGB(255, 110, 0);
            case RED_HOT    -> Color.fromRGB(210, 25, 0);
            case HOT        -> Color.fromRGB(150, 30, 0);
            default         -> null;
        };
    }

    /**
     * Starts a repeating particle task that emits heat-colored sparks in a
     * slowly-rotating ring at the top of the anvil block.
     * Only one task runs per player; if one is already active this is a no-op.
     *
     * @param player    the player using the anvil
     * @param anvilLoc  block location of the anvil (may be null for virtual anvils)
     * @param heatLevel current heat level of the workpiece (for color)
     */
    private void startParticleTask(Player player, Location anvilLoc, int heatLevel) {
        UUID uid = player.getUniqueId();
        if (activeParticleTasks.containsKey(uid)) return; // already running
        if (anvilLoc == null) return;

        Color color = getHeatColor(heatLevel);
        if (color == null) return;

        // Center of the anvil's top surface
        Location top = anvilLoc.clone().add(0.5, 1.0, 0.5);
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.8f);

        BukkitTask task = new BukkitRunnable() {
            private int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !activeParticleTasks.containsKey(uid)) {
                    cancel();
                    activeParticleTasks.remove(uid);
                    return;
                }
                // Concentric ring that slowly rotates and expands then resets
                double radius = 0.15 + (tick % 10) * 0.035;
                double rotOffset = tick * 0.4;
                int count = 7;
                for (int i = 0; i < count; i++) {
                    double angle = (2 * Math.PI * i) / count + rotOffset;
                    double dx = radius * Math.cos(angle);
                    double dz = radius * Math.sin(angle);
                    top.getWorld().spawnParticle(
                        Particle.DUST,
                        top.clone().add(dx, 0.04, dz),
                        1, 0.01, 0.06, 0.01, 0, dust);
                }
                tick++;
            }
        }.runTaskTimer(Specialization.getInstance(), 2L, 4L);

        activeParticleTasks.put(uid, task);
    }

    /** Cancels the active particle task for the given player, if any. */
    private void stopParticleTask(UUID uid) {
        BukkitTask task = activeParticleTasks.remove(uid);
        if (task != null) task.cancel();
    }

    /**
     * Emits a one-shot burst of heat-colored sparks at the anvil top
     * when the player takes the forged result.
     */
    private void emitForgeBurst(Location anvilLoc, int heatLevel) {
        if (anvilLoc == null) return;
        Color color = getHeatColor(heatLevel);
        if (color == null) return;

        Location top = anvilLoc.clone().add(0.5, 1.0, 0.5);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        int burst = 24;
        double maxRadius = 0.55;
        for (int i = 0; i < burst; i++) {
            double angle  = (2 * Math.PI * i) / burst;
            double spread = 0.4 + Math.random() * 0.6; // vary radius
            double dx = maxRadius * spread * Math.cos(angle);
            double dz = maxRadius * spread * Math.sin(angle);
            double dy = Math.random() * 0.35;
            top.getWorld().spawnParticle(
                Particle.DUST,
                top.clone().add(dx, dy, dz),
                1, 0, 0, 0, 0, dust);
        }
        // Extra central burst for brightness
        top.getWorld().spawnParticle(Particle.FIREWORK, top, 8, 0.2, 0.15, 0.2, 0.05);
    }
}

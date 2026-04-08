package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
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
    public static final int WHITE_HOT  = 4;
    public static final int YELLOW_HOT = 3;
    public static final int ORANGE_HOT = 2;
    public static final int RED_HOT    = 1;
    public static final int COLD       = 0;

    private static final long HEAT_DEGRADE_MS = 7_000L;
    private static final int REHEAT_TICKS_PER_LEVEL = 80; // 4 seconds per heat level

    // ─── Bloom Durability (bronze=4 strikes, iron=2 strikes) ───
    public static final int BLOOM_MAX_DAMAGE = 25;
    private static final int DAMAGE_STEP_1    = 24;  // initial bloom damage
    private static final int DAMAGE_STEP_3    = 4;   // threshold to yield ingot
    // Bronze hammer: 24→17→10→3→yield (4 strikes)
    private static final int BRONZE_HAMMER_DMG = 7;
    // Iron hammer: 24→4→yield (2 strikes)
    private static final int IRON_HAMMER_DMG   = 20;

    // ─── Workable Iron Component (tool heads / armor pieces) ───
    // raw_id -> [finished_id, strikesNeeded]
    // strikes = 2 × plate sets used in the crafting recipe
    private static final Map<String, Object[]> RAW_COMPONENTS = Map.ofEntries(
        Map.entry("iron_helm_raw",         new Object[]{"iron_helm",         10}),
        Map.entry("iron_breastplate_raw",  new Object[]{"iron_breastplate",  16}),
        Map.entry("iron_greaves_raw",      new Object[]{"iron_greaves",      14}),
        Map.entry("iron_sabaton_raw",      new Object[]{"iron_sabaton",       8}),
        Map.entry("iron_sword_head_raw",   new Object[]{"iron_sword_head",    4}),
        Map.entry("iron_axe_head_raw",     new Object[]{"iron_axe_head",      6}),
        Map.entry("iron_pickaxe_head_raw", new Object[]{"iron_pickaxe_head",  6}),
        Map.entry("iron_hoe_head_raw",     new Object[]{"iron_hoe_head",      4}),
        Map.entry("iron_shovel_head_raw",  new Object[]{"iron_shovel_head",   2})
    );

    // ─── Hammer IDs ───
    private static final Set<String> HAMMER_IDS = Set.of(
        "bronze_hammer", "iron_hammer"
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

    /** Returns true if item is an unworked iron component (_raw custom ID). */
    public static boolean isRawIronComponent(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && RAW_COMPONENTS.containsKey(ci.getId());
    }

    public static boolean isHammer(ItemStack item) {
        if (item == null) return false;
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(item);
        return ci != null && HAMMER_IDS.contains(ci.getId());
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

        // ─── wrought_iron_ingot → Yellow Hot (the proper ingot → plate path) ───
        if (custom != null && "wrought_iron_ingot".equals(custom.getId())) {
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

        // ─── Vanilla IRON_INGOT: block — players must use the bloom system ───
        if (source.getType() == Material.IRON_INGOT && custom == null) {
            event.setCancelled(true);
            return;
        }

        // ─── armor_plate_iron reheat -> Yellow Hot (unstackable) ───
        if (custom != null && "armor_plate_iron".equals(custom.getId())) {
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

        // ─── Raw iron component reheat → Yellow Hot ───
        if (custom != null && RAW_COMPONENTS.containsKey(custom.getId())) {
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

            // Place custom result in output (replaces whatever is there)
            furnace.getInventory().setResult(resultCopy);
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
            if (custom != null && "armor_plate_iron".equals(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else if (custom != null && "wrought_iron_ingot".equals(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else if (custom != null && RAW_COMPONENTS.containsKey(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
            } else {
                // Vanilla iron ingots and all other custom IRON_INGOT items — block
                event.setTotalCookTime(Integer.MAX_VALUE);
            }
            return;
        }

        // ─── CARROT_ON_A_STICK: bloom reheating ───
        if (source.getType() == Material.CARROT_ON_A_STICK) {
            if (custom != null && "iron_bloom".equals(custom.getId())) {
                int heat = getStoredHeatLevel(source);
                int levels = Math.max(1, YELLOW_HOT - heat);
                event.setTotalCookTime(levels * REHEAT_TICKS_PER_LEVEL);
                return;
            }

            // Not a bloom — block
            event.setTotalCookTime(Integer.MAX_VALUE);
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

        // ─── Level gate: iron forge recipes require Blacksmith Journeyman ───
        boolean isIronRecipe = isIronBloom(first) || isHeatedIronIngot(first)
            || isRawIronComponent(first) || (isHotIronPlate(first) && isHotIronPlate(second));
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
            if (!(isIronBloom(first) && isHammer(second))) {
                event.setResult(null);
                stopParticleTask(uid);
                return;
            }
        }
        // Block non-bloom CARROT_ON_A_STICK in slot 0 (whetstone, stray hammer, etc.)
        if (firstIsCotas && !isIronBloom(first)) {
            event.setResult(null);
            stopParticleTask(uid);
            return;
        }

        // ─── Hot plate + hot plate → iron_armor_plateset ───
        // The smithing table template slot rejects custom items client-side,
        // so this recipe is handled in the anvil instead.
        if (isHotIronPlate(first) && isHotIronPlate(second)) {
            prepareHotPlatesToPlatesetResult(event, inv);
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

        if (isHeatedIronIngot(first)) {
            prepareIngotToPlateResult(event, inv, first);
            startParticleTask(player, inv.getLocation(), getHeatLevel(first));
            return;
        }

        if (isRawIronComponent(first)) {
            prepareRawComponentResult(event, inv, first);
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
            if (hammerCI != null && "iron_hammer".equals(hammerCI.getId())) {
                damagePerWork = IRON_HAMMER_DMG;
            }
        }

        ItemStack result;

        if (currentDamage <= DAMAGE_STEP_3) {
            // Current damage already at threshold → yield the Wrought Iron Ingot
            CustomItem wroughtDef = CustomItemManager.getInstance().getCustomItem("wrought_iron_ingot");
            if (wroughtDef != null) {
                result = wroughtDef.createItemStack();
            } else {
                result = new ItemStack(Material.IRON_INGOT);
                result.editMeta(meta -> meta.setDisplayName("\u00A77Wrought Iron Ingot"));
            }
        } else {
            result = bloom.clone();
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

    /** Raw iron component (Orange+) + hammer → reduced damage; at damage 0 → finished component. */
    private void prepareRawComponentResult(PrepareAnvilEvent event, AnvilInventory inv, ItemStack raw) {
        int heat = getHeatLevel(raw);
        if (!isWorkable(heat)) {
            event.setResult(null);
            return;
        }

        CustomItem ci = CustomItemManager.getInstance().getCustomItem(raw);
        if (ci == null) { event.setResult(null); return; }

        int currentDamage = 0;
        if (raw.getItemMeta() instanceof Damageable d) currentDamage = d.getDamage();

        ItemStack result;
        if (currentDamage <= 1) {
            // Final strike — yield finished component
            String finishedId = (String) RAW_COMPONENTS.get(ci.getId())[0];
            CustomItem finishedDef = CustomItemManager.getInstance().getCustomItem(finishedId);
            if (finishedDef == null) { event.setResult(null); return; }
            result = finishedDef.createItemStack();
        } else {
            result = raw.clone();
            int newHeat = Math.max(COLD, heat - 1);
            final int nd = currentDamage - 1;
            result.editMeta(meta -> { if (meta instanceof Damageable d) d.setDamage(nd); });
            setHeatLevel(result, newHeat);
        }

        event.setResult(result);
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
        boolean isPlatesetRecipe     = isHotIronPlate(first) && isHotIronPlate(second);
        boolean isComponentRecipe    = isRawIronComponent(first) && isHammer(second);

        if (!isBloomOrIngotRecipe && !isPlatesetRecipe && !isComponentRecipe) return;

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

        // Capture before slots are cleared
        Location anvilLoc = anvil.getLocation();
        int heatLevel     = getHeatLevel(first);

        if (isPlatesetRecipe) {
            // Consume both hot plates; no hammer in this recipe
            anvil.setItem(0, null);
            anvil.setItem(1, null);
        } else {
            // Bloom / ingot: consume workpiece, damage hammer
            anvil.setItem(0, null);
            damageHammerInSlot(anvil, 1, player);
        }

        // ─── Sparks + sound ───
        stopParticleTask(player.getUniqueId());
        emitForgeBurst(anvilLoc, heatLevel);
        // Suppress the automatic vanilla anvil-use sound; ForgeSmithingSoundListener
        // already plays a custom craft-burst sound on this tick.
        Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
            if (player.isOnline()) player.stopSound(Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS);
        }, 1L);

        // ─── Give result + 1 Blacksmith XP ───
        player.setItemOnCursor(result);
        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
            .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) cp.addSkillXp(SkillType.BLACKSMITH, 1.0);
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
        // The extracted item is already removed from the furnace at this point.
        // We need to find it in the player's inventory or cursor and unfreeze it.
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            // Check cursor first (shift-click puts it in inventory, normal click leaves on cursor)
            unfreezeHeat(player.getItemOnCursor());
            player.setItemOnCursor(player.getItemOnCursor()); // force update
            for (ItemStack item : player.getInventory().getContents()) {
                unfreezeHeat(item);
            }
        });
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
            || "armor_plate_iron".equals(id) || RAW_COMPONENTS.containsKey(id);
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

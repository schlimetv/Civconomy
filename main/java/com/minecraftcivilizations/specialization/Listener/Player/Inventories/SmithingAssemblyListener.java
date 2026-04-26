package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.CustomItem.IronBloomSystem;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles all blueprint-required smithing recipes (iron & bronze tools, armor, hammers).
 *
 * <p>Paper 1.21.8's smithing table client rejects custom items in the template slot,
 * so the vanilla SmithingTransformRecipe matching never shows a result in the UI.
 * This listener manually matches the inputs and forces the result, bypassing the
 * client-side rejection.</p>
 *
 * <p>Also strips the inherited "Tool Handle" display name from iron tool results,
 * since SmithingTransformRecipe copies the base item's custom name to the result.</p>
 */
public class SmithingAssemblyListener implements Listener {

    // ─── Tool heads: headSuffix → [toolName, ironResultMaterial] ───
    private static final Map<String, String[]> TOOL_HEADS = Map.of(
        "sword_head",   new String[]{"sword",   "IRON_SWORD"},
        "axe_head",     new String[]{"axe",     "IRON_AXE"},
        "pickaxe_head", new String[]{"pickaxe", "IRON_PICKAXE"},
        "hoe_head",     new String[]{"hoe",     "IRON_HOE"},
        "shovel_head",  new String[]{"shovel",  "IRON_SHOVEL"}
    );

    // ─── Armor pieces: pieceSuffix → [slotName, leatherMaterial, ironResultMaterial] ───
    private static final Map<String, String[]> ARMOR_PIECES = Map.of(
        "helm",        new String[]{"helmet",     "LEATHER_HELMET",     "IRON_HELMET"},
        "breastplate", new String[]{"chestplate", "LEATHER_CHESTPLATE", "IRON_CHESTPLATE"},
        "greaves",     new String[]{"leggings",   "LEATHER_LEGGINGS",   "IRON_LEGGINGS"},
        "sabaton",     new String[]{"boots",      "LEATHER_BOOTS",      "IRON_BOOTS"}
    );

    private static final String[] METALS = {"iron", "bronze", "steel"};

    // ═════════════════════════════════════════════════════════════
    //  PrepareSmithingEvent — show the correct result
    // ═════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getItem(0);
        ItemStack base     = inv.getItem(1);
        ItemStack addition = inv.getItem(2);

        if (template == null || base == null || addition == null) return;

        CustomItem templateCI = CustomItemManager.getInstance().getCustomItem(template);
        if (templateCI == null) return;
        String templateId = templateCI.getId();
        if (!templateId.endsWith("_blueprint")) return;

        // Diamond smithing is distinct: base must be a toughened-steel vanilla iron item,
        // not a tool_handle or leather armor piece. Handle it entirely here and return.
        if (templateId.startsWith("diamond_")) {
            handleDiamondSmithing(event, base, addition, templateId);
            return;
        }

        CustomItem baseCI = CustomItemManager.getInstance().getCustomItem(base);
        CustomItem addCI  = CustomItemManager.getInstance().getCustomItem(addition);

        // ─── Try tool assembly (template=blueprint, base=tool_handle, addition=head) ───
        ItemStack result = tryToolAssembly(templateId, baseCI, addCI);

        // ─── Try armor assembly (template=blueprint, base=leather armor, addition=piece) ───
        if (result == null) {
            result = tryArmorAssembly(templateId, base, baseCI, addCI);
        }

        if (result != null) {
            // Steel pieces require purple or blue temper for smithing
            if (addCI != null && addCI.getId().startsWith("steel_")) {
                int temper = IronBloomSystem.getTemperTier(addition);
                if (temper != IronBloomSystem.TEMPER_PURPLE && temper != IronBloomSystem.TEMPER_BLUE) {
                    event.setResult(null);
                    return;
                }
                // Read poorly hardened level from the component
                Integer badQuench = addition.getItemMeta().getPersistentDataContainer()
                    .get(IronBloomSystem.BAD_QUENCH_KEY, PersistentDataType.INTEGER);
                int poorlyHardened = badQuench != null ? badQuench : 0;
                applySteelStats(result, temper, poorlyHardened);
            }
            event.setResult(result);
        }
    }

    /**
     * Matches tool and hammer assembly recipes.
     * Template: {metal}_{tool}_blueprint, Base: tool_handle, Addition: {metal}_{head}
     */
    private ItemStack tryToolAssembly(String templateId, CustomItem baseCI, CustomItem addCI) {
        if (baseCI == null || !"tool_handle".equals(baseCI.getId())) return null;
        if (addCI == null) return null;
        String addId = addCI.getId();

        for (String metal : METALS) {
            // ─── Hammer ───
            if (templateId.equals(metal + "_hammer_blueprint") && addId.equals(metal + "_hammer_head")) {
                CustomItem def = CustomItemManager.getInstance().getCustomItem(metal + "_hammer");
                return def != null ? def.createItemStack() : null;
            }

            // ─── Standard tools ───
            for (var entry : TOOL_HEADS.entrySet()) {
                String headSuffix = entry.getKey();
                String toolName   = entry.getValue()[0];
                String ironMat    = entry.getValue()[1];

                if (!templateId.equals(metal + "_" + toolName + "_blueprint")) continue;
                if (!addId.equals(metal + "_" + headSuffix)) continue;

                if (metal.equals("iron")) {
                    return new ItemStack(Material.valueOf(ironMat));
                } else {
                    CustomItem def = CustomItemManager.getInstance().getCustomItem(metal + "_" + toolName);
                    return def != null ? def.createItemStack() : null;
                }
            }
        }
        return null;
    }

    /**
     * Matches armor assembly recipes.
     * Template: {metal}_{slot}_blueprint, Base: vanilla leather armor, Addition: {metal}_{piece}
     */
    private ItemStack tryArmorAssembly(String templateId, ItemStack base, CustomItem baseCI, CustomItem addCI) {
        // Base must be a vanilla leather armor piece (no custom item)
        if (baseCI != null) return null;
        if (addCI == null) return null;
        String addId = addCI.getId();

        for (String metal : METALS) {
            for (var entry : ARMOR_PIECES.entrySet()) {
                String pieceSuffix = entry.getKey();
                String slotName    = entry.getValue()[0];
                String leatherMat  = entry.getValue()[1];
                String ironMat     = entry.getValue()[2];

                if (!templateId.equals(metal + "_" + slotName + "_blueprint")) continue;
                if (!addId.equals(metal + "_" + pieceSuffix)) continue;
                if (base.getType() != Material.valueOf(leatherMat)) continue;

                if (metal.equals("iron")) {
                    return new ItemStack(Material.valueOf(ironMat));
                } else {
                    CustomItem def = CustomItemManager.getInstance().getCustomItem(metal + "_" + slotName);
                    return def != null ? def.createItemStack() : null;
                }
            }
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════
    //  Leather-base durability transfer for copper/bronze/iron/steel armor
    // ═════════════════════════════════════════════════════════════

    /** Armor-piece custom item IDs whose leather base's durability % should carry onto the result. */
    private static final java.util.Set<String> LEATHER_DURABILITY_TRANSFER_TARGETS = java.util.Set.of(
        "copper_helm", "copper_breastplate", "copper_greaves", "copper_sabaton",
        "bronze_helm", "bronze_breastplate", "bronze_greaves", "bronze_sabaton",
        "iron_helm",   "iron_breastplate",   "iron_greaves",   "iron_sabaton",
        "steel_helm",  "steel_breastplate",  "steel_greaves",  "steel_sabaton"
    );

    /**
     * Runs after the blueprint handler and after vanilla's SmithingTransformRecipe
     * has produced a result. For copper/bronze/iron/steel armor smithing, if the
     * base slot holds a leather armor piece, transfers <b>half</b> of the leather's
     * damage percentage onto the metal result. Pristine leather (0% damage) yields
     * a pristine result; a 40%-damaged leather yields a 20%-damaged result; fully
     * broken leather yields a 50%-damaged (never fully broken) result.
     *
     * Gold armor smithing is deliberately excluded: its recipe still uses ExactChoice
     * against pristine leather, so this handler never matches gold.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMetalArmorLeatherDurabilityTransfer(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();
        ItemStack base     = inv.getItem(1);
        ItemStack addition = inv.getItem(2);
        ItemStack result   = event.getResult();

        if (base == null || addition == null) return;
        if (result == null || result.getType().isAir()) return;
        if (!isLeatherArmor(base.getType())) return;

        CustomItem addCI = CustomItemManager.getInstance().getCustomItem(addition);
        if (addCI == null) return;
        if (!LEATHER_DURABILITY_TRANSFER_TARGETS.contains(addCI.getId())) return;

        // Metal armor inherits only HALF of the leather base's damage, so worn leather
        // still produces usable gear (see the user's "100% − damage%/2" rule).
        transferDamagePercentage(base, result, 0.5);
        event.setResult(result);
    }

    private static boolean isLeatherArmor(Material mat) {
        return mat == Material.LEATHER_HELMET
            || mat == Material.LEATHER_CHESTPLATE
            || mat == Material.LEATHER_LEGGINGS
            || mat == Material.LEATHER_BOOTS;
    }

    // ═════════════════════════════════════════════════════════════
    //  Diamond smithing — blueprint + toughened steel + diamond component
    // ═════════════════════════════════════════════════════════════

    /**
     * Resolves a diamond smithing recipe and transfers the steel base's remaining
     * durability percentage onto the vanilla diamond result.
     *
     * Recipe layout for both tools and armor:
     *   template  = diamond_{slot|tool}_blueprint
     *   base      = vanilla IRON_{slot|tool} that has been finished as toughened (blue) steel
     *   addition  = diamond_{piece}  (diamond_helm, diamond_sword_head, ...)
     *   result    = vanilla DIAMOND_{slot|tool} with damage = diamondMax * (1 - baseDurabilityRatio)
     */
    private void handleDiamondSmithing(PrepareSmithingEvent event, ItemStack base,
                                       ItemStack addition, String templateId) {
        // Base must be a toughened-steel item. Purple/hardened and un-marked legacy
        // items are rejected so they can't be shortcut to diamond.
        if (base.getItemMeta() == null
            || !base.getItemMeta().getPersistentDataContainer()
                .has(TOUGHENED_STEEL_KEY, PersistentDataType.BYTE)) {
            event.setResult(null);
            return;
        }

        CustomItem addCI = CustomItemManager.getInstance().getCustomItem(addition);
        if (addCI == null) {
            event.setResult(null);
            return;
        }
        String addId = addCI.getId();

        ItemStack result = resolveDiamondResult(templateId, addId, base.getType());
        if (result == null) {
            event.setResult(null);
            return;
        }

        // Diamond inherits the steel base's damage 1:1 — a 30%-damaged steel helmet
        // produces a 30%-damaged diamond helmet.
        transferDamagePercentage(base, result, 1.0);

        event.setResult(result);
    }

    /**
     * Scales a fraction of {@code base}'s damage percentage onto {@code result}.
     *
     * <p>With {@code damageMultiplier == 1.0} the result's damage % mirrors the base
     * (a 30%-damaged base produces a 30%-damaged result — this is the diamond case).
     * With {@code damageMultiplier == 0.5} the result only receives half the damage
     * (a 30%-damaged base produces a 15%-damaged result — this is the leather-armor
     * case for copper/bronze/iron/steel).</p>
     *
     * <p>The result is clamped so it never ends up fully broken; at worst it keeps
     * 1 durability remaining.</p>
     */
    private static void transferDamagePercentage(ItemStack base, ItemStack result, double damageMultiplier) {
        if (!(base.getItemMeta() instanceof Damageable)) return;
        int baseMax = effectiveMaxDamage(base);
        if (baseMax <= 0) return;
        int baseDamage = effectiveDamage(base);
        double damageRatio = Math.max(0.0, Math.min(1.0, (double) baseDamage / baseMax));
        double scaled = Math.max(0.0, Math.min(1.0, damageRatio * damageMultiplier));
        int resMax = effectiveMaxDamage(result);
        if (resMax <= 0) return;
        int dmg = (int) Math.round(resMax * scaled);
        int finalDmg = Math.max(0, Math.min(resMax - 1, dmg));
        result.editMeta(m -> {
            if (m instanceof Damageable rd) {
                rd.setDamage(finalDmg);
            }
        });
    }

    /**
     * Effective max-damage for an item. Damageable.getMaxDamage() throws when the
     * item carries no explicit max_damage component (most fresh vanilla armor /
     * tools), so we fall back to the material's vanilla max durability.
     */
    private static int effectiveMaxDamage(ItemStack stack) {
        if (stack.getItemMeta() instanceof Damageable d && d.hasMaxDamage()) {
            return d.getMaxDamage();
        }
        return stack.getType().getMaxDurability();
    }

    /** Current damage on an item, or 0 if it has none. Mirrors {@link #effectiveMaxDamage}. */
    private static int effectiveDamage(ItemStack stack) {
        if (stack.getItemMeta() instanceof Damageable d && d.hasDamage()) {
            return d.getDamage();
        }
        return 0;
    }

    /**
     * Matches a diamond blueprint + diamond component + iron base against the
     * armor/tool maps and returns the corresponding vanilla diamond result stack,
     * or null if no recipe applies. Durability transfer is applied by the caller.
     */
    private ItemStack resolveDiamondResult(String templateId, String addId, Material baseType) {
        // Tools
        for (var entry : TOOL_HEADS.entrySet()) {
            String headSuffix = entry.getKey();             // e.g. sword_head
            String toolName   = entry.getValue()[0];        // e.g. sword
            Material ironMat  = Material.valueOf(entry.getValue()[1]); // IRON_SWORD

            if (!templateId.equals("diamond_" + toolName + "_blueprint")) continue;
            if (!addId.equals("diamond_" + headSuffix)) continue;
            if (baseType != ironMat) continue;

            return new ItemStack(Material.valueOf("DIAMOND_" + toolName.toUpperCase()));
        }

        // Armor
        for (var entry : ARMOR_PIECES.entrySet()) {
            String pieceSuffix = entry.getKey();            // e.g. helm
            String slotName    = entry.getValue()[0];       // e.g. helmet
            Material ironMat   = Material.valueOf(entry.getValue()[2]); // IRON_HELMET

            if (!templateId.equals("diamond_" + slotName + "_blueprint")) continue;
            if (!addId.equals("diamond_" + pieceSuffix)) continue;
            if (baseType != ironMat) continue;

            return new ItemStack(Material.valueOf("DIAMOND_" + slotName.toUpperCase()));
        }

        return null;
    }

    // ═════════════════════════════════════════════════════════════
    //  InventoryClickEvent — consume inputs on result take
    // ═════════════════════════════════════════════════════════════

    /**
     * Grants XP for vanilla smithing recipes (copper/gold armor that don't use blueprints).
     * Runs at MONITOR so it doesn't interfere with slot manipulation.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVanillaSmithingXp(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType().isAir()) return;

        // Skip blueprint recipes — those are handled by onSmithingResultTake
        ItemStack template = smithing.getItem(0);
        if (template != null) {
            CustomItem templateCI = CustomItemManager.getInstance().getCustomItem(template);
            if (templateCI != null && templateCI.getId().endsWith("_blueprint")) return;
        }

        // Check the addition slot for a custom metal piece
        ItemStack addition = smithing.getItem(2);
        if (addition == null) return;
        CustomItem addCI = CustomItemManager.getInstance().getCustomItem(addition);
        if (addCI == null) return;

        String addId = addCI.getId();
        // Match copper/gold armor pieces in the addition slot
        Double xp = SMITHING_XP.get(addId.replace("_helm", "_helmet_smithing")
                .replace("_breastplate", "_chestplate_smithing")
                .replace("_greaves", "_leggings_smithing")
                .replace("_sabaton", "_boots_smithing"));
        if (xp == null) {
            // Try tool assembly keys
            xp = SMITHING_XP.get(addId.replace("_head", "_assembly")
                    .replace("_sword_assembly", "_sword_assembly") // no-op for swords without _head
            );
        }
        if (xp == null) return;

        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) cp.addSkillXp(SkillType.BLACKSMITH, xp);
    }

    /**
     * Manually consumes smithing inputs when the player takes a blueprint-recipe result.
     * This is needed because the vanilla recipe system may not have matched the recipe
     * (due to ExactChoice + custom item component differences), so it won't auto-consume.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithingResultTake(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof SmithingInventory smithing)) return;
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;

        ItemStack resultItem = event.getCurrentItem();
        if (resultItem == null || resultItem.getType().isAir()) return;

        // Only intercept our blueprint recipes
        ItemStack template = smithing.getItem(0);
        if (template == null) return;
        CustomItem templateCI = CustomItemManager.getInstance().getCustomItem(template);
        if (templateCI == null || !templateCI.getId().endsWith("_blueprint")) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true);

        ItemStack result = resultItem.clone();

        // Consume one of each input
        decrementSlot(smithing, 0);
        decrementSlot(smithing, 1);
        decrementSlot(smithing, 2);

        // Give result to player
        player.setItemOnCursor(result);

        // ─── Grant Blacksmith XP based on plates used in the metal component ───
        grantSmithingXp(player, templateCI.getId());

        // Force smithing table to re-evaluate (updates the preview for remaining items)
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() instanceof SmithingInventory si) {
                // Trigger a PrepareSmithingEvent by nudging the inventory
                ItemStack s0 = si.getItem(0);
                si.setItem(0, s0);
            }
        });
    }

    private void decrementSlot(SmithingInventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null) return;
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            inv.setItem(slot, null);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Smithing XP: based on plates used in the metal component
    // ─────────────────────────────────────────────────────────────

    /** Blacksmith XP for smithing table recipes: 2 XP per plate/plateset in the component's base recipe. */
    private static final Map<String, Double> SMITHING_XP = Map.ofEntries(
        // Armor (plates used in component → 2xp each)
        // Copper armor smithing: uses copper plates directly
        Map.entry("copper_helmet_smithing",     10.0),  // helm = 5 plates
        Map.entry("copper_chestplate_smithing", 16.0),  // breastplate = 8 plates
        Map.entry("copper_leggings_smithing",   14.0),  // greaves = 7 plates
        Map.entry("copper_boots_smithing",       8.0),  // sabaton = 4 plates
        // Gold armor smithing: uses gold plate sets (3 plates each)
        Map.entry("gold_helmet_smithing",       10.0),  // 5 platesets = 15 plates → 10xp (per plateset)
        Map.entry("gold_chestplate_smithing",   16.0),
        Map.entry("gold_leggings_smithing",     14.0),
        Map.entry("gold_boots_smithing",         8.0),
        // Bronze armor smithing: uses bronze plates directly
        Map.entry("bronze_helmet_smithing",     10.0),
        Map.entry("bronze_chestplate_smithing", 16.0),
        Map.entry("bronze_leggings_smithing",   14.0),
        Map.entry("bronze_boots_smithing",       8.0),
        // Iron armor smithing: uses iron plate sets
        Map.entry("iron_helmet_smithing",       10.0),
        Map.entry("iron_chestplate_smithing",   16.0),
        Map.entry("iron_leggings_smithing",     14.0),
        Map.entry("iron_boots_smithing",         8.0),
        // Copper tool assembly
        Map.entry("copper_sword_assembly",   4.0),   // 2 plates
        Map.entry("copper_axe_assembly",     6.0),   // 3 plates
        Map.entry("copper_pickaxe_assembly", 6.0),   // 3 plates
        Map.entry("copper_hoe_assembly",     4.0),   // 2 plates
        Map.entry("copper_shovel_assembly",  2.0),   // 1 plate
        // Gold tool assembly
        Map.entry("gold_sword_assembly",     4.0),
        Map.entry("gold_axe_assembly",       6.0),
        Map.entry("gold_pickaxe_assembly",   6.0),
        Map.entry("gold_hoe_assembly",       4.0),
        Map.entry("gold_shovel_assembly",    2.0),
        // Bronze tool assembly (blueprint)
        Map.entry("bronze_sword_assembly",   4.0),
        Map.entry("bronze_axe_assembly",     6.0),
        Map.entry("bronze_pickaxe_assembly", 6.0),
        Map.entry("bronze_hoe_assembly",     4.0),
        Map.entry("bronze_shovel_assembly",  2.0),
        // Iron tool assembly (blueprint)
        Map.entry("iron_sword_assembly",     4.0),
        Map.entry("iron_axe_assembly",       6.0),
        Map.entry("iron_pickaxe_assembly",   6.0),
        Map.entry("iron_hoe_assembly",       4.0),
        Map.entry("iron_shovel_assembly",    2.0),
        // Hammer assembly
        Map.entry("bronze_hammer_assembly",  8.0),   // 4 plates
        Map.entry("iron_hammer_assembly",    8.0),
        // Steel armor smithing
        Map.entry("steel_helmet_smithing",     10.0),
        Map.entry("steel_chestplate_smithing", 16.0),
        Map.entry("steel_leggings_smithing",   14.0),
        Map.entry("steel_boots_smithing",       8.0),
        // Steel tool assembly
        Map.entry("steel_sword_assembly",    4.0),
        Map.entry("steel_axe_assembly",      6.0),
        Map.entry("steel_pickaxe_assembly",  6.0),
        Map.entry("steel_hoe_assembly",      4.0),
        Map.entry("steel_shovel_assembly",   2.0),
        Map.entry("steel_hammer_assembly",   8.0),
        // Diamond armor smithing — 10× plate sets used in the addition component.
        // (diamond_helm = 5 plate sets, breastplate = 8, greaves = 7, sabaton = 4)
        Map.entry("diamond_helmet_smithing",     50.0),
        Map.entry("diamond_chestplate_smithing", 80.0),
        Map.entry("diamond_leggings_smithing",   70.0),
        Map.entry("diamond_boots_smithing",      40.0),
        // Diamond tool assembly — 10× plate sets used in the head.
        // (sword_head = 2, axe_head = 3, pickaxe_head = 3, hoe_head = 2, shovel_head = 1)
        Map.entry("diamond_sword_assembly",    20.0),
        Map.entry("diamond_axe_assembly",      30.0),
        Map.entry("diamond_pickaxe_assembly",  30.0),
        Map.entry("diamond_hoe_assembly",      20.0),
        Map.entry("diamond_shovel_assembly",   10.0)
    );

    private void grantSmithingXp(Player player, String blueprintId) {
        String base = blueprintId.replace("_blueprint", "");
        Double xp = SMITHING_XP.get(base + "_assembly");
        if (xp == null) xp = SMITHING_XP.get(base + "_smithing");
        if (xp == null) return;

        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) {
            cp.addSkillXp(SkillType.BLACKSMITH, xp);
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  Steel stat application based on temper tier
    // ═════════════════════════════════════════════════════════════

    // Iron base durabilities
    private static final Map<Material, Integer> IRON_DURABILITY = Map.ofEntries(
        Map.entry(Material.IRON_SWORD,       250),
        Map.entry(Material.IRON_AXE,         250),
        Map.entry(Material.IRON_PICKAXE,     250),
        Map.entry(Material.IRON_HOE,         250),
        Map.entry(Material.IRON_SHOVEL,      250),
        Map.entry(Material.IRON_HELMET,      165),
        Map.entry(Material.IRON_CHESTPLATE,  240),
        Map.entry(Material.IRON_LEGGINGS,    225),
        Map.entry(Material.IRON_BOOTS,       195)
    );

    // Iron base armor values per slot
    private static final Map<Material, Double> IRON_ARMOR_VALUES = Map.of(
        Material.IRON_HELMET,     2.0,
        Material.IRON_CHESTPLATE, 6.0,
        Material.IRON_LEGGINGS,   5.0,
        Material.IRON_BOOTS,      2.0
    );

    /** PDC key to identify purple tempered steel armor for the combat system. */
    public static final NamespacedKey PURPLE_STEEL_KEY =
        new NamespacedKey("specialization", "purple_steel_armor");

    /** PDC key marking blue (toughened) steel tools and armor — required as the base for diamond smithing. */
    public static final NamespacedKey TOUGHENED_STEEL_KEY =
        new NamespacedKey("specialization", "toughened_steel");

    private void applySteelStats(ItemStack result, int temperTier, int poorlyHardened) {
        Material mat = result.getType();
        boolean isPurple = (temperTier == IronBloomSystem.TEMPER_PURPLE);
        boolean isArmor = IRON_ARMOR_VALUES.containsKey(mat);
        boolean isTool = IRON_DURABILITY.containsKey(mat) && !isArmor;

        Integer ironDur = IRON_DURABILITY.get(mat);
        if (ironDur == null) return;

        // Calculate durability
        // Purple: iron base for both tools and armor
        // Blue: 150% iron for both tools and armor
        int baseDur = isPurple ? ironDur : (int)(ironDur * 1.50);

        // Apply poorly hardened reduction: -25% per level
        if (poorlyHardened > 0) {
            double reduction = poorlyHardened * 0.25;
            baseDur = (int) Math.max(1, baseDur * (1.0 - reduction));
        }

        final int finalDur = baseDur;
        final boolean purple = isPurple;

        result.editMeta(m -> {
            // Tag blue (toughened) steel so diamond smithing can validate the base slot.
            if (!purple) {
                m.getPersistentDataContainer().set(
                    TOUGHENED_STEEL_KEY, PersistentDataType.BYTE, (byte) 1);
            }

            // Durability
            if (m instanceof Damageable d) {
                d.setMaxDamage(finalDur);
                d.setDamage(0);
            }

            if (isArmor) {
                Double ironArmor = IRON_ARMOR_VALUES.get(mat);
                if (ironArmor == null) return;

                EquipmentSlotGroup slotGroup = getArmorSlotGroup(mat);
                String slotName = getArmorSlotName(mat);

                // Purple: helmet+1, boots+1 armor; no toughness; PDC tag for combat system
                // Blue: iron base armor; +1 toughness per piece
                double armorVal = ironArmor;
                if (purple && (mat == Material.IRON_HELMET || mat == Material.IRON_BOOTS)) {
                    armorVal = ironArmor + 1.0;
                }

                m.addAttributeModifier(Attribute.ARMOR,
                    new AttributeModifier(
                        new NamespacedKey("steel", slotName + "_armor"),
                        armorVal, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

                if (!purple) {
                    // Blue: +1 armor toughness per piece
                    m.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            new NamespacedKey("steel", slotName + "_toughness"),
                            1.0, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
                }

                if (purple) {
                    // Tag for combat system to apply special damage mechanics
                    m.getPersistentDataContainer().set(PURPLE_STEEL_KEY,
                        org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                }

                // Equippable model for steel texture
                var equip = m.getEquippable();
                equip.setModel(NamespacedKey.minecraft("steel"));
                equip.setSlot(getArmorEquipSlot(mat));
                m.setEquippable(equip);
            }

            if (isTool) {
                // Purple: diamond tier speed (8.0), +1 attack damage on sword/axe
                // Blue: iron tier speed (6.0), base attack damage
                var tool = m.getTool();
                if (tool != null) {
                    float speed = purple ? 8.0f : 6.0f;
                    tool.setDamagePerBlock(1);
                    tool.setRules(java.util.List.of());
                    switch (mat) {
                        case IRON_PICKAXE -> tool.addRule(org.bukkit.Tag.MINEABLE_PICKAXE, speed, true);
                        case IRON_AXE     -> tool.addRule(org.bukkit.Tag.MINEABLE_AXE,     speed, true);
                        case IRON_SHOVEL  -> tool.addRule(org.bukkit.Tag.MINEABLE_SHOVEL,   speed, true);
                        case IRON_HOE     -> tool.addRule(org.bukkit.Tag.MINEABLE_HOE,      speed, true);
                        case IRON_SWORD   -> tool.addRule(java.util.List.of(Material.COBWEB), 15.0f, true);
                        default -> {}
                    }
                    m.setTool(tool);
                }

                // Purple steel: must set BOTH damage + speed since adding any custom
                // attribute modifier replaces ALL vanilla default modifiers in 1.21.8.
                // Hide raw modifier tooltip and show clean stats in lore instead.
                if (purple) {
                    if (mat == Material.IRON_SWORD) {
                        // 7 total damage (1 base + 6), 1.6 speed (4 base - 2.4)
                        m.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                            new AttributeModifier(new NamespacedKey("steel", "attack_damage"),
                                6.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                        m.addAttributeModifier(Attribute.ATTACK_SPEED,
                            new AttributeModifier(new NamespacedKey("steel", "attack_speed"),
                                -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                    } else if (mat == Material.IRON_AXE) {
                        // 9 total damage (1 base + 8), 1.0 speed (4 base - 3.0)
                        m.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                            new AttributeModifier(new NamespacedKey("steel", "attack_damage"),
                                8.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                        m.addAttributeModifier(Attribute.ATTACK_SPEED,
                            new AttributeModifier(new NamespacedKey("steel", "attack_speed"),
                                -3.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                    }
                    // Hide the raw modifier lines, show clean vanilla-style stats in lore
                    m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
                }
            }

            // Lore: stats (if hidden) + temper tag + poorly hardened
            List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();

            // For purple tools with hidden attributes, show clean stat lines
            if (purple && isTool) {
                lore.add(Component.empty());
                lore.add(Component.text("When in Main Hand:").color(NamedTextColor.GRAY));
                if (mat == Material.IRON_SWORD) {
                    lore.add(Component.text(" 7 Attack Damage").color(NamedTextColor.DARK_GREEN));
                    lore.add(Component.text(" 1.6 Attack Speed").color(NamedTextColor.DARK_GREEN));
                } else if (mat == Material.IRON_AXE) {
                    lore.add(Component.text(" 9 Attack Damage").color(NamedTextColor.DARK_GREEN));
                    lore.add(Component.text(" 1.0 Attack Speed").color(NamedTextColor.DARK_GREEN));
                }
            }

            lore.add(purple
                    ? Component.text("Hardened").color(NamedTextColor.DARK_PURPLE)
                    : Component.text("Toughened").color(NamedTextColor.AQUA));
            if (poorlyHardened > 0) {
                lore.add(Component.text("Poorly Hardened " + toRoman(poorlyHardened)).color(NamedTextColor.RED));
            }
            m.lore(lore);
        });
    }

    private static EquipmentSlotGroup getArmorSlotGroup(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET"))     return EquipmentSlotGroup.HEAD;
        if (name.endsWith("_CHESTPLATE")) return EquipmentSlotGroup.CHEST;
        if (name.endsWith("_LEGGINGS"))   return EquipmentSlotGroup.LEGS;
        if (name.endsWith("_BOOTS"))      return EquipmentSlotGroup.FEET;
        return EquipmentSlotGroup.ARMOR;
    }

    private static org.bukkit.inventory.EquipmentSlot getArmorEquipSlot(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET"))     return org.bukkit.inventory.EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return org.bukkit.inventory.EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS"))   return org.bukkit.inventory.EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS"))      return org.bukkit.inventory.EquipmentSlot.FEET;
        return org.bukkit.inventory.EquipmentSlot.HEAD;
    }

    private static String getArmorSlotName(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET"))     return "head";
        if (name.endsWith("_CHESTPLATE")) return "chest";
        if (name.endsWith("_LEGGINGS"))   return "legs";
        if (name.endsWith("_BOOTS"))      return "feet";
        return "head";
    }

    private static String toRoman(int num) {
        return switch (num) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(num);
        };
    }
}

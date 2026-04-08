package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

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

    private static final String[] METALS = {"iron", "bronze"};

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

        CustomItem baseCI = CustomItemManager.getInstance().getCustomItem(base);
        CustomItem addCI  = CustomItemManager.getInstance().getCustomItem(addition);

        // ─── Try tool assembly (template=blueprint, base=tool_handle, addition=head) ───
        ItemStack result = tryToolAssembly(templateId, baseCI, addCI);

        // ─── Try armor assembly (template=blueprint, base=leather armor, addition=piece) ───
        if (result == null) {
            result = tryArmorAssembly(templateId, base, baseCI, addCI);
        }

        if (result != null) {
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
                    // Vanilla item — no custom display name
                    return new ItemStack(Material.valueOf(ironMat));
                } else {
                    CustomItem def = CustomItemManager.getInstance().getCustomItem("bronze_" + toolName);
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
                    CustomItem def = CustomItemManager.getInstance().getCustomItem("bronze_" + slotName);
                    return def != null ? def.createItemStack() : null;
                }
            }
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
        Map.entry("iron_hammer_assembly",    8.0)
    );

    private void grantSmithingXp(Player player, String blueprintId) {
        // Derive the recipe key from the blueprint ID: e.g. iron_sword_blueprint → iron_sword_assembly
        // or iron_helmet_blueprint → iron_helmet_smithing
        String base = blueprintId.replace("_blueprint", "");

        // Check tool assembly first, then armor smithing
        Double xp = SMITHING_XP.get(base + "_assembly");
        if (xp == null) xp = SMITHING_XP.get(base + "_smithing");
        if (xp == null) return;

        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp != null) {
            cp.addSkillXp(SkillType.BLACKSMITH, xp);
        }
    }
}

package com.minecraftcivilizations.specialization.CustomItem;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Defines all metalworking custom items for the plate-based crafting system.
 *
 * Item hierarchy per metal (copper/gold/iron):
 *   Ingot → Plate → Plate Set → Armor Piece / Tool Head
 *   Armor Piece + Leather Armor (smithing table) → Finished Armor
 *   Tool Head + Tool Handle (crafting table) → Finished Tool
 *
 * Base material mapping (for resource pack custom_model_data):
 *   - Plates, plate sets, armor pieces, tool heads → their metal's INGOT
 *   - Copper finished armor → LEATHER_* items
 *   - Copper finished tools → STONE_* items
 *   - Tool handle → STICK
 *
 * @author Generated for CivLabs metalworking system
 */
public class MetalworkingItems {

    public static void init() {
        // ─── UTILITY / MATERIALS ───
        new SimpleMetalItem("tool_handle",           "§fTool Handle",      Material.STICK);
        new SimpleMetalItem("plant_fiber",           "§aPlant Fiber",      Material.STRING);
        new SimpleMetalItem("padded_leather",        "§6Padded Leather",   Material.LEATHER);
        new SimpleMetalItem("armor_plate_chainmail", "§7Chainmail Plate",  Material.IRON_INGOT);
        new DurabilityItem("wood_shear",             "§eWood Shears",      Material.SHEARS, 60);
        new SimpleMetalItem("whetstone",             "§7Whetstone",        Material.CARROT_ON_A_STICK);

        // ─── IRON BLOOM / HAMMER SYSTEM ───
        new IronBloomItem();
        new SimpleMetalItem("wrought_iron_ingot", "\u00A77Wrought Iron Ingot", Material.IRON_INGOT);
        new SteelModelItem("bronze_hammer_head", "\u00A76Bronze Hammer Head", Material.GOLD_INGOT);
        new SteelModelItem("iron_hammer_head",   "\u00A77Iron Hammer Head",   Material.IRON_INGOT);
        new WorkableIronComponent("iron_hammer_head_raw", "\u00A77Iron Hammer Head", 8, "iron_hammer_head");
        new HammerItem("bronze_hammer", "\u00A76Bronze Hammer", 144);
        new HammerItem("iron_hammer",   "\u00A77Iron Hammer",   144);

        // ─── STEEL SYSTEM ───
        new CoalCokeItem();
        new SteelBlendItem();
        new PigIronItem();
        new SteelModelItem("steel_ingot",  "\u00A7bSteel Ingot",  Material.IRON_INGOT);
        new SteelModelItem("steel_hammer_head", "\u00A7bSteel Hammer Head", Material.IRON_INGOT);
        new WorkableSteelComponent("steel_hammer_head_raw", "\u00A7bSteel Hammer Head", 4, "steel_hammer_head");
        new HammerItem("steel_hammer", "\u00A7bSteel Hammer", 144);

        // ─── STEEL (intermediates on IRON_INGOT, using itemModel → steel: namespace) ───
        new SteelModelItem("armor_plate_steel",    "\u00A7bSteel Plate",     Material.IRON_INGOT);
        new SteelModelItem("steel_armor_plateset", "\u00A7bSteel Plate Set", Material.IRON_INGOT);
        new SteelModelItem("steel_helm",        "\u00A7bSteel Helm",        Material.IRON_INGOT);
        new SteelModelItem("steel_breastplate", "\u00A7bSteel Breastplate", Material.IRON_INGOT);
        new SteelModelItem("steel_greaves",     "\u00A7bSteel Greaves",     Material.IRON_INGOT);
        new SteelModelItem("steel_sabaton",     "\u00A7bSteel Sabaton",     Material.IRON_INGOT);
        new SteelModelItem("steel_sword_head",   "\u00A7bSteel Sword Head",   Material.IRON_INGOT);
        new SteelModelItem("steel_axe_head",     "\u00A7bSteel Axe Head",     Material.IRON_INGOT);
        new SteelModelItem("steel_pickaxe_head", "\u00A7bSteel Pickaxe Head", Material.IRON_INGOT);
        new SteelModelItem("steel_hoe_head",     "\u00A7bSteel Hoe Head",     Material.IRON_INGOT);
        new SteelModelItem("steel_shovel_head",  "\u00A7bSteel Shovel Head",  Material.IRON_INGOT);
        initSteelWorkables();
        initSteelFinished();

        // ─── COPPER (intermediates on COPPER_INGOT) ───
        initMetalIntermediates("copper", Material.COPPER_INGOT, "§6");
        initCopperFinished();

        // ─── GOLD (intermediates on GOLD_INGOT) ───
        initMetalIntermediates("gold", Material.GOLD_INGOT, "§e");

        // ─── IRON (intermediates on IRON_INGOT) ───
        initMetalIntermediates("iron", Material.IRON_INGOT, "§7");
        initIronWorkables();

        // ─── BRONZE (uses bronze: namespace with itemModel, not customModelData) ───
        initBronze();

        // ─── DIAMOND (intermediates on DIAMOND base; finished gear is vanilla DIAMOND_*) ───
        initMetalIntermediates("diamond", Material.DIAMOND, "§b");

        // ─── BLUEPRINTS (iron + bronze + steel + diamond components) ───
        initBlueprints();
    }

    /** Maps component piece suffix → finished result name for blueprint naming. */
    private static final String[][] BLUEPRINT_PIECE_MAP = {
        {"helm",        "helmet"},
        {"breastplate", "chestplate"},
        {"greaves",     "leggings"},
        {"sabaton",     "boots"},
        {"sword_head",  "sword"},
        {"axe_head",    "axe"},
        {"pickaxe_head","pickaxe"},
        {"hoe_head",    "hoe"},
        {"shovel_head", "shovel"},
    };

    /**
     * Registers blueprint items for all iron and bronze armor pieces and tool heads.
     * Blueprint IDs are named after the RESULT (e.g. iron_helmet_blueprint, not iron_helm_blueprint).
     */
    private static void initBlueprints() {
        for (String metal : new String[]{"iron", "bronze", "steel", "diamond"}) {
            String cap = capitalize(metal);
            for (String[] entry : BLUEPRINT_PIECE_MAP) {
                String result = entry[1];
                String label = capitalizeWords(result.replace("_", " "));
                new BlueprintItem(metal + "_" + result + "_blueprint",
                    "\u00A7b\u00A7l" + cap + " " + label + " Blueprint");
            }
            // Hammer blueprint (diamond has no hammer tier)
            if (!metal.equals("diamond")) {
                new BlueprintItem(metal + "_hammer_blueprint",
                    "\u00A7b\u00A7l" + cap + " Hammer Blueprint");
            }
        }
    }

    /**
     * Registers the 11 intermediate items for a given metal:
     * plate, plate set, 4 armor pieces, 5 tool heads
     */
    private static void initMetalIntermediates(String metal, Material ingot, String color) {
        String cap = capitalize(metal);

        // Plate & Plate Set
        new SimpleMetalItem("armor_plate_" + metal,    color + cap + " Plate",     ingot);
        new SimpleMetalItem(metal + "_armor_plateset",  color + cap + " Plate Set", ingot);

        // Armor Pieces (intermediate — still on ingot base)
        new SimpleMetalItem(metal + "_helm",        color + cap + " Helm",        ingot);
        new SimpleMetalItem(metal + "_breastplate", color + cap + " Breastplate", ingot);
        new SimpleMetalItem(metal + "_greaves",     color + cap + " Greaves",     ingot);
        new SimpleMetalItem(metal + "_sabaton",     color + cap + " Sabaton",     ingot);

        // Tool Heads
        new SimpleMetalItem(metal + "_sword_head",   color + cap + " Sword Head",   ingot);
        new SimpleMetalItem(metal + "_axe_head",     color + cap + " Axe Head",     ingot);
        new SimpleMetalItem(metal + "_pickaxe_head", color + cap + " Pickaxe Head", ingot);
        new SimpleMetalItem(metal + "_hoe_head",     color + cap + " Hoe Head",     ingot);
        new SimpleMetalItem(metal + "_shovel_head",  color + cap + " Shovel Head",  ingot);
    }

    /**
     * Registers the 9 finished copper items:
     * 4 armor pieces (on leather base) + 5 tools (on stone base)
     */
    private static void initCopperFinished() {
        // Stone durability = 131, copper = 20% more → 157
        int copperToolDurability = (int) (131 * 1.2);

        // ─── Copper Armor (leather base, copper equipment model) ───
        new CopperArmorItem("copper_helmet",     "§6Copper Helmet",     Material.LEATHER_HELMET,     121);
        new CopperArmorItem("copper_chestplate", "§6Copper Chestplate", Material.LEATHER_CHESTPLATE, 176);
        new CopperArmorItem("copper_leggings",   "§6Copper Leggings",   Material.LEATHER_LEGGINGS,   165);
        new CopperArmorItem("copper_boots",      "§6Copper Boots",      Material.LEATHER_BOOTS,      143);

        // ─── Copper Tools (stone base, boosted durability) ───
        new CopperToolItem("copper_sword",   "§6Copper Sword",   Material.STONE_SWORD,   copperToolDurability);
        new CopperToolItem("copper_axe",     "§6Copper Axe",     Material.STONE_AXE,     copperToolDurability);
        new CopperPickaxeItem(copperToolDurability);
        new CopperToolItem("copper_hoe",     "§6Copper Hoe",     Material.STONE_HOE,     copperToolDurability);
        new CopperToolItem("copper_shovel",  "§6Copper Shovel",  Material.STONE_SHOVEL,  copperToolDurability);
    }

    // ─────────────────────────────────────────────────────────────
    //  Inner item classes
    // ─────────────────────────────────────────────────────────────

    /**
     * A basic custom item with no special on-create behavior.
     * Used for plates, plate sets, armor pieces (intermediate), and tool heads.
     */
    private static class SimpleMetalItem extends CustomItem {
        public SimpleMetalItem(String id, String displayName, Material material) {
            // id is used as customModelData string → maps to resource pack select case
            super(id, displayName, material, id, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            // No special behavior
        }
    }

    /**
     * Blueprint item: PAPER base, all share customModelData "blueprint" for the same texture.
     * Each has a unique ID/displayName for recipe matching via ExactChoice.
     */
    private static class BlueprintItem extends CustomItem {
        public BlueprintItem(String id, String displayName) {
            super(id, displayName, Material.PAPER, "blueprint", false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
        }
    }

    /**
     * Coal Coke: COAL base with steel: namespace itemModel.
     * Smelted from vanilla coal in a regular furnace (40s).
     * Used exclusively as blast furnace fuel.
     */
    private static class CoalCokeItem extends CustomItem {
        public CoalCokeItem() {
            super("coal_coke", "\u00A77Coal Coke", Material.COAL, null, 64, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", "coal_coke"));
                m.setMaxStackSize(64);
            });
        }
    }

    /**
     * Steel Blend: RAW_IRON base with steel: namespace itemModel.
     * Smelted in blast furnace to produce pig iron.
     */
    private static class SteelBlendItem extends CustomItem {
        public SteelBlendItem() {
            super("steel_blend", "\u00A78Steel Blend", Material.RAW_IRON, null, 64, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", "steel_blend"));
                m.setMaxStackSize(64);
            });
        }
    }

    /**
     * Pig Iron: CARROT_ON_A_STICK base with steel: namespace itemModel.
     * Max damage 25, stack size 1. Worked on anvil with iron/steel hammer.
     */
    private static class PigIronItem extends CustomItem {
        public PigIronItem() {
            super("pig_iron", "\u00A77Pig Iron", Material.CARROT_ON_A_STICK, null, 1, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", "pig_iron"));
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(IronBloomSystem.PIG_IRON_MAX_DAMAGE);
                    d.setDamage(0);
                }
                m.setMaxStackSize(1);
            });
        }
    }

    /**
     * Iron Bloom: CARROT_ON_A_STICK base with steel: namespace itemModel.
     * Max damage 25, stack size 1.
     */
    private static class IronBloomItem extends CustomItem {
        public IronBloomItem() {
            super("iron_bloom", "\u00A77Iron Bloom", Material.CARROT_ON_A_STICK, null, 1, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", "iron_bloom"));
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(IronBloomSystem.BLOOM_MAX_DAMAGE);
                    d.setDamage(0);
                }
                m.setMaxStackSize(1);
            });
        }
    }

    /**
     * Finished Hammer: CARROT_ON_A_STICK base with steel: namespace itemModel.
     * Used in anvil for working iron blooms and heated iron ingots/plates.
     */
    private static class HammerItem extends CustomItem {
        private final int durability;

        public HammerItem(String id, String displayName, int durability) {
            super(id, displayName, Material.CARROT_ON_A_STICK, null, 1, true, false);
            this.durability = durability;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", getId()));
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }
                m.setMaxStackSize(1);
            });
        }
    }

    /**
     * A custom item with overridden durability but no other special behavior.
     * Used for wood shears and similar items.
     */
    private static class DurabilityItem extends CustomItem {
        private final int durability;

        public DurabilityItem(String id, String displayName, Material material, int durability) {
            super(id, displayName, material, id, false);
            this.durability = durability;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }
            });
        }
    }

    /**
     * Finished copper armor: sets durability + equippable model to "copper"
     * so the equipment texture from assets/minecraft/equipment/copper.json is used.
     */
    private static class CopperArmorItem extends CustomItem {
        private final int durability;
        private final org.bukkit.inventory.EquipmentSlot slot;

        public CopperArmorItem(String id, String displayName, Material material, int durability) {
            super(id, displayName, material, id, false);
            this.durability = durability;
            this.slot = slotFromMaterial(material);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }
                var equip = m.getEquippable();
                equip.setModel(NamespacedKey.minecraft("copper"));
                equip.setSlot(slot);
                m.setEquippable(equip);
            });
        }
    }

    /**
     * Finished copper tool: sets durability to 20% above stone.
     * Explicitly rebuilds tool component rules at stone-equivalent speed (4.0f)
     * because inherited rules from wrapItemStack's stale meta are unreliable.
     */
    private static class CopperToolItem extends CustomItem {
        private final int durability;
        private final Material material;

        public CopperToolItem(String id, String displayName, Material material, int durability) {
            super(id, displayName, material, id, false);
            this.durability = durability;
            this.material = material;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }
                var tool = m.getTool();
                if (tool != null) {
                    tool.setDamagePerBlock(1);
                    tool.setRules(java.util.List.of());
                    switch (material) {
                        case STONE_AXE    -> tool.addRule(org.bukkit.Tag.MINEABLE_AXE,    4.0f, true);
                        case STONE_HOE    -> tool.addRule(org.bukkit.Tag.MINEABLE_HOE,    4.0f, true);
                        case STONE_SHOVEL -> tool.addRule(org.bukkit.Tag.MINEABLE_SHOVEL, 4.0f, true);
                        case STONE_SWORD  -> tool.addRule(java.util.List.of(Material.COBWEB), 15.0f, true);
                    }
                    m.setTool(tool);
                }
            });
        }
    }

    /**
     * Copper Pickaxe: stone tier with custom modifications.
     * Cannot mine: diamond, emerald, redstone, iron ores (no drops, slow speed).
     * Can mine: gold ore at copper speed with drops.
     * All other pickaxe-mineable blocks at copper speed (5.0).
     * Rule order matters — first matching rule wins in 1.21+ tool components.
     */
    private static class CopperPickaxeItem extends CustomItem {
        private final int durability;

        public CopperPickaxeItem(int durability) {
            super("copper_pickaxe", "§6Copper Pickaxe", Material.STONE_PICKAXE, "copper_pickaxe", false);
            this.durability = durability;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }

                var tool = m.getTool();
                if (tool != null) {
                    tool.setDamagePerBlock(1);
                    tool.setRules(java.util.List.of());

                    // ── DENY rules FIRST (first match wins) ──
                    // Stone tier cannot mine these ores
                    tool.addRule(java.util.List.of(
                        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
                        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
                        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE
                    ), 1.0f, false);

                    // ── ALLOW gold ore at copper speed (overrides general rule below) ──
                    tool.addRule(java.util.List.of(
                        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE
                    ), 5.0f, true);

                    // ── GENERAL: all other pickaxe-mineable at copper speed ──
                    tool.addRule(org.bukkit.Tag.MINEABLE_PICKAXE, 5.0f, true);

                    m.setTool(tool);
                }
            });
        }
    }

    /**
     * Finished bronze tool on IRON_* base.
     * Sets itemModel (bronze:bronze_*), durability 350, enchantability 29.
     * Iron-tier mining is inherited from the IRON_* base material.
     */
    private static class BronzeToolItem extends CustomItem {
        private final int durability = 350;
        private final String modelId;

        public BronzeToolItem(String id, String displayName, Material material, String modelId) {
            super(id, displayName, material, null, -1, true, false);
            this.modelId = modelId;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(durability);
                    d.setDamage(0);
                }
                m.setItemModel(new NamespacedKey("bronze", modelId));
                m.setEnchantable(29);
                var tool = m.getTool();
                if (tool != null) {
                    tool.setDamagePerBlock(1);
                    m.setTool(tool);
                }
            });
        }
    }

    /**
     * Bronze Pickaxe: iron tier with restrictions.
     * Cannot mine: diamond ore (no drops, slow speed).
     * Can mine: anvils (chipped, damaged) at iron speed.
     * All other pickaxe-mineable at iron speed (6.0).
     * Must clear inherited IRON_PICKAXE rules and rebuild — addRule appends
     * to the END, and first-match-wins means appended rules never fire.
     */
    private static class BronzePickaxeItem extends CustomItem {
        public BronzePickaxeItem() {
            super("bronze_pickaxe", "§6Bronze Pickaxe", Material.IRON_PICKAXE, null, -1, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(350);
                    d.setDamage(0);
                }

                m.setItemModel(new NamespacedKey("bronze", "bronze_pickaxe"));
                m.setEnchantable(29);

                var tool = m.getTool();
                if (tool != null) {
                    tool.setDamagePerBlock(1);
                    // Clear inherited IRON_PICKAXE rules — must rebuild from scratch
                    tool.setRules(java.util.List.of());

                    // ── DENY diamond ores FIRST (first match wins) ──
                    tool.addRule(java.util.List.of(
                        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE
                    ), 1.0f, false);

                    // ── ALLOW anvils at iron speed with drops ──
                    tool.addRule(java.util.List.of(
                        Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
                    ), 8.0f, true);

                    // ── GENERAL: all pickaxe-mineable at iron speed ──
                    tool.addRule(org.bukkit.Tag.MINEABLE_PICKAXE, 6.0f, true);

                    m.setTool(tool);
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** Capitalize each word in a space-separated string. "sword head" → "Sword Head" */
    private static String capitalizeWords(String s) {
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(capitalize(words[i]));
        }
        return sb.toString();
    }

    /** Shared utility: derive EquipmentSlot from armor Material name. */
    static org.bukkit.inventory.EquipmentSlot slotFromMaterial(Material mat) {
        String name = mat.name();
        if (name.endsWith("_HELMET"))     return org.bukkit.inventory.EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE")) return org.bukkit.inventory.EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS"))   return org.bukkit.inventory.EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS"))      return org.bukkit.inventory.EquipmentSlot.FEET;
        return org.bukkit.inventory.EquipmentSlot.HEAD; // fallback
    }

    /**
     * Registers all unworked (raw) iron armor and tool components.
     * Each starts at damage = (strikesNeeded - 1) so only 1 durability remains.
     * The model/customModelData string matches the finished item, so they look identical.
     * Finished items are yielded by IronBloomSystem after the required anvil strikes.
     *
     * strikes = 2 × plate sets in the crafting recipe
     */
    private static void initIronWorkables() {
        // Armor components (plate sets in recipe × 2)
        new WorkableIronComponent("iron_helm_raw",         "§7Iron Helm",        10, "iron_helm");
        new WorkableIronComponent("iron_breastplate_raw",  "§7Iron Breastplate", 16, "iron_breastplate");
        new WorkableIronComponent("iron_greaves_raw",      "§7Iron Greaves",     14, "iron_greaves");
        new WorkableIronComponent("iron_sabaton_raw",      "§7Iron Sabaton",      8, "iron_sabaton");
        // Tool heads
        new WorkableIronComponent("iron_sword_head_raw",   "§7Iron Sword Head",   4, "iron_sword_head");
        new WorkableIronComponent("iron_axe_head_raw",     "§7Iron Axe Head",     6, "iron_axe_head");
        new WorkableIronComponent("iron_pickaxe_head_raw", "§7Iron Pickaxe Head", 6, "iron_pickaxe_head");
        new WorkableIronComponent("iron_hoe_head_raw",     "§7Iron Hoe Head",     4, "iron_hoe_head");
        new WorkableIronComponent("iron_shovel_head_raw",  "§7Iron Shovel Head",  2, "iron_shovel_head");
    }

    /**
     * Registers finished steel armor and tools.
     * These are the final smithing table outputs. Stats can be adjusted later.
     * Uses steel: namespace itemModel for custom textures.
     */
    private static void initSteelFinished() {
        // Finished armor (IRON_* base — same as bronze pattern on GOLDEN_* base)
        new SteelModelItem("steel_helmet",     "\u00A7bSteel Helmet",     Material.IRON_HELMET);
        new SteelModelItem("steel_chestplate", "\u00A7bSteel Chestplate", Material.IRON_CHESTPLATE);
        new SteelModelItem("steel_leggings",   "\u00A7bSteel Leggings",   Material.IRON_LEGGINGS);
        new SteelModelItem("steel_boots",      "\u00A7bSteel Boots",      Material.IRON_BOOTS);

        // Finished tools (IRON_* base)
        new SteelModelItem("steel_sword",   "\u00A7bSteel Sword",   Material.IRON_SWORD);
        new SteelModelItem("steel_axe",     "\u00A7bSteel Axe",     Material.IRON_AXE);
        new SteelModelItem("steel_pickaxe", "\u00A7bSteel Pickaxe", Material.IRON_PICKAXE);
        new SteelModelItem("steel_hoe",     "\u00A7bSteel Hoe",     Material.IRON_HOE);
        new SteelModelItem("steel_shovel",  "\u00A7bSteel Shovel",  Material.IRON_SHOVEL);
    }

    /**
     * Registers all unworked (raw) steel armor and tool components.
     * Follows the same pattern as iron: strikes = 2 × plate sets in recipe.
     */
    private static void initSteelWorkables() {
        // Armor components (plateSets used in recipe)
        new WorkableSteelComponent("steel_helm_raw",         "\u00A7bSteel Helm",         5, "steel_helm");
        new WorkableSteelComponent("steel_breastplate_raw",  "\u00A7bSteel Breastplate",  8, "steel_breastplate");
        new WorkableSteelComponent("steel_greaves_raw",      "\u00A7bSteel Greaves",      7, "steel_greaves");
        new WorkableSteelComponent("steel_sabaton_raw",      "\u00A7bSteel Sabaton",      4, "steel_sabaton");
        // Tool heads
        new WorkableSteelComponent("steel_sword_head_raw",   "\u00A7bSteel Sword Head",   2, "steel_sword_head");
        new WorkableSteelComponent("steel_axe_head_raw",     "\u00A7bSteel Axe Head",     3, "steel_axe_head");
        new WorkableSteelComponent("steel_pickaxe_head_raw", "\u00A7bSteel Pickaxe Head", 3, "steel_pickaxe_head");
        new WorkableSteelComponent("steel_hoe_head_raw",     "\u00A7bSteel Hoe Head",     2, "steel_hoe_head");
        new WorkableSteelComponent("steel_shovel_head_raw",  "\u00A7bSteel Shovel Head",  1, "steel_shovel_head");
    }

    /**
     * Unworked iron armor / tool component.
     * Shares customModelData with its finished counterpart so it looks identical.
     * Starts at damage = strikesNeeded - 1 (1 durability remaining).
     * Stack size = 1 (cannot stack while being worked).
     */
    private static class WorkableIronComponent extends CustomItem {
        private final int strikesNeeded;

        public WorkableIronComponent(String id, String displayName, int strikesNeeded, String modelId) {
            // customModelData = modelId matches finished item → same texture
            super(id, displayName, Material.IRON_INGOT, modelId, false);
            this.strikesNeeded = strikesNeeded;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            final int strikes = this.strikesNeeded;
            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(strikes + 1);
                    d.setDamage(strikes);   // 1 durability remaining = just formed
                }
                m.setMaxStackSize(1);
            });
        }
    }

    /**
     * Unworked steel armor / tool component.
     * Uses the steel smithing system: heat-variable damage, annealing, quenching.
     * maxDamage = 2 × plateSets × 6.  Starts with "Annealed" tag.
     */
    private static class WorkableSteelComponent extends CustomItem {
        private final int plateSets;
        private final String modelId;

        public WorkableSteelComponent(String id, String displayName, int plateSets, String modelId) {
            super(id, displayName, Material.IRON_INGOT, null, -1, true, false);
            this.plateSets = plateSets;
            this.modelId = modelId;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            final int maxDmg = 2 * plateSets * 6;
            itemStack.editMeta(m -> {
                m.setItemModel(new NamespacedKey("steel", modelId));
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(maxDmg);
                    d.setDamage(maxDmg - 1);  // 1 durability remaining = just formed
                }
                m.setMaxStackSize(1);
                m.getPersistentDataContainer().set(
                    IronBloomSystem.ANNEALED_KEY,
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                java.util.List<String> lore = m.hasLore() && m.getLore() != null
                    ? new java.util.ArrayList<>(m.getLore()) : new java.util.ArrayList<>();
                lore.add("\u00A7aAnnealed");
                m.setLore(lore);
            });
        }
    }

    /**
     * Item whose model lives under assets/steel/ (NamespacedKey "steel:id").
     * Used for hammer heads and other steel-namespace items.
     */
    private static class SteelModelItem extends CustomItem {
        public SteelModelItem(String id, String displayName, Material material) {
            super(id, displayName, material, null, -1, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            itemStack.editMeta(m -> m.setItemModel(new NamespacedKey("steel", getId())));
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Bronze system
    // ─────────────────────────────────────────────────────────────

    /**
     * Bronze items use the bronze: namespace with itemModel component
     * rather than customModelData on vanilla items.
     *
     * Bronze Blend (GOLD_ORE) → smelt → Bronze Ingot (GOLD_INGOT)
     * → Plates → Plate Set → Armor Pieces / Tool Heads
     * Armor Piece + Golden Armor (smithing) → Finished Bronze Armor
     */
    private static void initBronze() {
        // ─── Crushed Ores (bronze: namespace itemModel) ───
        new CrushedOreItem("crushed_copper_ore", "§6Crushed Copper Ore", Material.RAW_COPPER, "crushed_copper_ore");
        new CrushedOreItem("crushed_iron_ore",   "§7Crushed Iron Ore",   Material.RAW_IRON,   "crushed_iron_ore");
        new CrushedOreItem("crushed_gold_ore",   "§eCrushed Gold Ore",   Material.RAW_GOLD,   "crushed_gold_ore");

        // ─── Materials (bronze: namespace itemModel) ───
        new BronzeBlendItem();
        new BronzeItem("bronze_ingot", "§6Bronze Ingot", Material.GOLD_INGOT, "bronze_ingot");

        // ─── Intermediates (customModelData on GOLD_INGOT → gold textures via gold_ingot.json cases) ───
        new SimpleMetalItem("armor_plate_bronze",    "§6Bronze Plate",     Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_armor_plateset",  "§6Bronze Plate Set", Material.GOLD_INGOT);

        new SimpleMetalItem("bronze_helm",        "§6Bronze Helm",        Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_breastplate", "§6Bronze Breastplate", Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_greaves",     "§6Bronze Greaves",     Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_sabaton",     "§6Bronze Sabaton",     Material.GOLD_INGOT);

        new SimpleMetalItem("bronze_sword_head",   "§6Bronze Sword Head",   Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_axe_head",     "§6Bronze Axe Head",     Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_pickaxe_head", "§6Bronze Pickaxe Head", Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_hoe_head",     "§6Bronze Hoe Head",     Material.GOLD_INGOT);
        new SimpleMetalItem("bronze_shovel_head",  "§6Bronze Shovel Head",  Material.GOLD_INGOT);

        // ─── Finished Bronze Armor (GOLDEN_* base, custom stats from datapack) ───
        //                                                                          dur  armor tough
        new BronzeArmorItem("bronze_helmet",     "§6Bronze Helmet",     Material.GOLDEN_HELMET,     240, 3.0, 1.0);
        new BronzeArmorItem("bronze_chestplate", "§6Bronze Chestplate", Material.GOLDEN_CHESTPLATE, 240, 6.0, 1.0);
        new BronzeArmorItem("bronze_leggings",   "§6Bronze Skirt",      Material.GOLDEN_LEGGINGS,   240, 3.0, 1.0);
        new BronzeArmorItem("bronze_boots",      "§6Bronze Greaves",    Material.GOLDEN_BOOTS,      240, 1.0, 0.0);

        // ─── Finished Bronze Tools (IRON_* base, 350 dur, enchant 29, bronze: model) ───
        new BronzeToolItem("bronze_sword",   "§6Bronze Sword",   Material.IRON_SWORD,   "bronze_sword");
        new BronzeToolItem("bronze_axe",     "§6Bronze Axe",     Material.IRON_AXE,     "bronze_axe");
        new BronzePickaxeItem();
        new BronzeToolItem("bronze_hoe",     "§6Bronze Hoe",     Material.IRON_HOE,     "bronze_hoe");
        new BronzeToolItem("bronze_shovel",  "§6Bronze Shovel",  Material.IRON_SHOVEL,  "bronze_shovel");
    }

    // ─────────────────────────────────────────────────────────────
    //  Bronze inner classes
    // ─────────────────────────────────────────────────────────────

    /**
     * A bronze item that uses itemModel (bronze: namespace) instead of customModelData.
     * Passes null for customModelData so wrapItemStack skips that component entirely.
     */
    private static class BronzeItem extends CustomItem {
        private final String modelId;

        public BronzeItem(String id, String displayName, Material material, String modelId) {
            // null customModelData → wrapItemStack won't set CustomModelDataComponent
            super(id, displayName, material, null, -1, true, false);
            this.modelId = modelId;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            meta.setItemModel(new NamespacedKey("bronze", modelId));
            itemStack.setItemMeta(meta);
        }
    }

    /**
     * Crushed Bronze Blend: GOLD_ORE base with custom model.
     * Stacks to 64.
     */
    private static class BronzeBlendItem extends CustomItem {
        public BronzeBlendItem() {
            super("bronze_blend", "§6Crushed Bronze Blend", Material.GOLD_ORE, null, 64, true, false);
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            meta.setItemModel(new NamespacedKey("bronze", "bronze_blend"));
            meta.setMaxStackSize(64);
            itemStack.setItemMeta(meta);
        }
    }

    /**
     * Crushed Ore item: uses itemModel in the bronze: namespace, stacks to 64.
     * Base material matches the corresponding raw ore for visual fallback.
     */
    private static class CrushedOreItem extends CustomItem {
        private final String modelId;

        public CrushedOreItem(String id, String displayName, Material base, String modelId) {
            super(id, displayName, base, null, 64, true, false);
            this.modelId = modelId;
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            meta.setItemModel(new NamespacedKey("bronze", modelId));
            meta.setMaxStackSize(64);
            itemStack.setItemMeta(meta);
        }
    }

    /**
     * Finished bronze armor on GOLDEN_* base.
     * Sets itemModel (bronze:), equippable model (minecraft:bronze),
     * custom durability, and explicit armor/toughness attribute modifiers
     * matching the datapack values.
     */
    private static class BronzeArmorItem extends CustomItem {
        private final int durability;
        private final double armor;
        private final double toughness;
        private final org.bukkit.inventory.EquipmentSlot slot;
        private final String modelId;

        public BronzeArmorItem(String id, String displayName, Material material,
                               int durability, double armor, double toughness) {
            super(id, displayName, material, null, -1, true, false);
            this.durability = durability;
            this.armor = armor;
            this.toughness = toughness;
            this.slot = slotFromMaterial(material);
            this.modelId = id; // e.g. "bronze_helmet"
        }

        @Override
        public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
            final org.bukkit.inventory.EquipmentSlot eqSlot = this.slot;
            final double armorVal = this.armor;
            final double toughnessVal = this.toughness;
            final String model = this.modelId;
            final int dur = this.durability;

            itemStack.editMeta(m -> {
                if (m instanceof org.bukkit.inventory.meta.Damageable d) {
                    d.setMaxDamage(dur);
                    d.setDamage(0);
                }

                m.setItemModel(new NamespacedKey("bronze", model));

                var equip = m.getEquippable();
                equip.setModel(NamespacedKey.minecraft("bronze"));
                equip.setSlot(eqSlot);
                m.setEquippable(equip);

                EquipmentSlotGroup slotGroup = switch (eqSlot) {
                    case HEAD  -> EquipmentSlotGroup.HEAD;
                    case CHEST -> EquipmentSlotGroup.CHEST;
                    case LEGS  -> EquipmentSlotGroup.LEGS;
                    case FEET  -> EquipmentSlotGroup.FEET;
                    default    -> EquipmentSlotGroup.ARMOR;
                };
                String slotName = eqSlot.name().toLowerCase();

                m.addAttributeModifier(Attribute.ARMOR,
                    new AttributeModifier(
                        new NamespacedKey("bronze", slotName + "_armor"),
                        armorVal, AttributeModifier.Operation.ADD_NUMBER, slotGroup));

                if (toughnessVal > 0) {
                    m.addAttributeModifier(Attribute.ARMOR_TOUGHNESS,
                        new AttributeModifier(
                            new NamespacedKey("bronze", slotName + "_toughness"),
                            toughnessVal, AttributeModifier.Operation.ADD_NUMBER, slotGroup));
                }
            });
        }
    }
}

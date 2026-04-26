package com.minecraftcivilizations.specialization.Recipe;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.*;

import java.util.List;

/**
 * Registers all metalworking recipes for copper, gold, and iron.
 *
 * Recipe chain per metal:
 *   1. 3 ingots (row)          → 2 plates            (shaped)
 *   2. 3 plates (column)       → 1 plate set          (shaped, ExactChoice)
 *   3. plate sets (armor shape) → armor piece          (shaped, ExactChoice)
 *   4. plate sets (tool shape)  → tool head            (shaped, ExactChoice)
 *   5. armor piece + leather    → finished armor       (smithing transform)
 *   6. tool head + tool handle  → finished tool        (shaped, ExactChoice)
 *
 * Plus: tool handle recipe (leather + string + stick)
 *
 * Copper finished items are custom items (leather/stone base).
 * Gold/iron finished items are vanilla (GOLDEN_ / IRON_ variants).
 *
 * @author Generated for CivLabs metalworking system
 */
public class MetalworkingRecipes {

    private static int successCount;

    /**
     * Register all metalworking recipes. Call from {@link Recipes#registerRecipes(boolean)}.
     *
     * @return number of successfully registered recipes
     */
    public static int register(List<String> failedExceptions) {
        successCount = 0;

        // ─── Utility items ───
        registerToolHandle(failedExceptions);
        registerWoodShears(failedExceptions);
        registerWhetstone(failedExceptions);

        // ─── Material chain: padded leather → chainmail plate → armor ───
        registerPaddedLeather(failedExceptions);
        registerChainmailPlate(failedExceptions);
        registerLeatherArmor(failedExceptions);
        registerChainmailArmor(failedExceptions);

        // ─── Per-metal crafting recipes (plates → plate sets → pieces/heads) ───
        registerMetalCrafting("copper", Material.COPPER_INGOT, true,  "", failedExceptions);
        registerMetalCrafting("gold",   Material.GOLD_INGOT,   false, "", failedExceptions);
        registerMetalCrafting("iron",   Material.IRON_INGOT,   false, "_raw", failedExceptions);

        // ─── Smithing: armor piece + leather armor → finished armor ───
        registerArmorSmithing("copper", true,  failedExceptions);
        registerArmorSmithing("gold",   false, failedExceptions);

        // ─── Tool Assembly: tool head + tool handle → finished tool ───
        registerToolAssembly("copper", true,  failedExceptions);
        registerToolAssembly("gold",   false, failedExceptions);

        // ─── Iron & Bronze Blueprints ───
        registerBlueprints("iron",   failedExceptions);
        registerBlueprints("bronze", failedExceptions);

        // ─── Iron: blueprint-required smithing for armor & tools ───
        registerBlueprintArmorSmithing("iron", failedExceptions);
        registerBlueprintToolSmithing("iron",  failedExceptions);

        // ─── Bronze: blend → smelt → ingot → plates → armor/tools ───
        registerBronzeBlend(failedExceptions);
        registerBronzeIngotSmelting(failedExceptions);
        registerBronzeCrafting(failedExceptions);
        registerBlueprintArmorSmithing("bronze", failedExceptions);
        registerBlueprintToolSmithing("bronze",  failedExceptions);

        // ─── Crushed Ores: crafting + smelting (copper, iron, gold) ───
        registerCrushedOres(failedExceptions);

        // ─── Steel System: coal coke, steel blend, pig iron, intermediates ───
        registerCoalCokeSmelting(failedExceptions);
        registerSteelBlend(failedExceptions);
        registerPigIronReheat(failedExceptions);
        registerSteelCrafting(failedExceptions);
        registerSteelTempering(failedExceptions);

        // ─── Steel Blueprints & Smithing Assembly ───
        registerBlueprints("steel", failedExceptions);
        registerBlueprintArmorSmithing("steel", failedExceptions);
        registerBlueprintToolSmithing("steel", failedExceptions);

        // ─── Iron Bloom System (furnace only, results overridden by IronBloomSystem) ───
        registerIronBloomSmelting(failedExceptions);

        // ─── Iron Plate: heating iron ingots ───
        registerIronIngotHeating(failedExceptions);

        // ─── Hammer system: heads, blueprints, assembly ───
        registerHammerHeads(failedExceptions);
        registerHammerBlueprints(failedExceptions);
        registerHammerAssembly(failedExceptions);

        // ─── Smithing Table (bronze alternative) ───
        registerBronzeSmithingTable(failedExceptions);

        // ─── Bronze Anvil ───
        registerBronzeAnvil(failedExceptions);

        // ─── Diamond: plates, plate sets, components, blueprints, smithing ───
        registerDiamondPlates(failedExceptions);
        registerArmorPieces("diamond", false, "", failedExceptions);
        registerToolHeads("diamond", false, "", failedExceptions);
        registerBlueprints("diamond", failedExceptions);
        registerDiamondArmorSmithing(failedExceptions);
        registerDiamondToolSmithing(failedExceptions);

        Bukkit.getLogger().info("[MetalworkingRecipes] Registered " + successCount + " metalworking recipes.");
        return successCount;
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private static NamespacedKey key(String name) {
        return new NamespacedKey(Specialization.getInstance(), name);
    }

    private static CustomItem getItem(String id) {
        return CustomItemManager.getInstance().getCustomItem(id);
    }

    /** Creates a 1-count ItemStack for the given custom item (for results / ExactChoice). */
    private static ItemStack stack(String customItemId) {
        CustomItem item = getItem(customItemId);
        if (item == null) throw new IllegalStateException("Missing custom item: '" + customItemId + "'");
        return item.createItemStack();
    }

    /** Creates an n-count ItemStack for the given custom item. */
    private static ItemStack stack(String customItemId, int amount) {
        CustomItem item = getItem(customItemId);
        if (item == null) throw new IllegalStateException("Missing custom item: '" + customItemId + "'");
        return item.createItemStack(amount);
    }

    /** ExactChoice matching a specific custom item. */
    private static RecipeChoice.ExactChoice exact(String customItemId) {
        return new RecipeChoice.ExactChoice(stack(customItemId));
    }

    private static void add(Recipe recipe, String name, List<String> failed) {
        try {
            Bukkit.addRecipe(recipe, true);
            successCount++;
        } catch (Exception e) {
            failed.add(name + " (" + e.getMessage() + ")");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Tool Handle
    // ─────────────────────────────────────────────────────────────

    /**
     * Tool Handle recipe:
     * <pre>
     *  _L_
     *  STS
     *  _L_
     * </pre>
     * L = Leather, S = String, T = Stick
     */
    private static void registerToolHandle(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("tool_handle"), stack("tool_handle"));
        r.shape(" L ", "STS", " L ");
        r.setIngredient('L', Material.LEATHER);
        r.setIngredient('S', Material.STRING);
        r.setIngredient('T', Material.STICK);
        add(r, "tool_handle", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Wood Shears, Whetstone, Padded Leather, Chainmail, Leather Armor
    // ─────────────────────────────────────────────────────────────

    /**
     * Wood Shears: 2 of any log in a diagonal.
     * <pre>
     *  L_
     *  _L
     * </pre>
     */
    private static void registerWoodShears(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("wood_shear"), stack("wood_shear"));
        r.shape("L ", " L");
        r.setIngredient('L', new RecipeChoice.MaterialChoice(Tag.LOGS));
        add(r, "wood_shear", failed);
    }

    /**
     * Whetstone: 3 granite over 3 cobblestone.
     * <pre>
     *  GGG
     *  CCC
     * </pre>
     */
    private static void registerWhetstone(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("whetstone"), stack("whetstone"));
        r.shape("GGG", "CCC");
        r.setIngredient('G', Material.GRANITE);
        r.setIngredient('C', Material.COBBLESTONE);
        add(r, "whetstone", failed);
    }

    /**
     * Padded Leather: 1 leather + 1 wool (any color), shapeless.
     */
    private static void registerPaddedLeather(List<String> failed) {
        ShapelessRecipe r = new ShapelessRecipe(key("padded_leather"), stack("padded_leather"));
        r.addIngredient(Material.LEATHER);
        r.addIngredient(new RecipeChoice.MaterialChoice(Tag.WOOL));
        add(r, "padded_leather", failed);
    }

    /**
     * Chainmail Plate: padded leather in center, 8 iron nuggets around it.
     * <pre>
     *  NNN
     *  NPN
     *  NNN
     * </pre>
     */
    private static void registerChainmailPlate(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("armor_plate_chainmail"), stack("armor_plate_chainmail"));
        r.shape("NNN", "NPN", "NNN");
        r.setIngredient('N', Material.IRON_NUGGET);
        r.setIngredient('P', exact("padded_leather"));
        add(r, "armor_plate_chainmail", failed);
    }

    /**
     * Leather armor crafted from padded leather in vanilla armor patterns.
     */
    private static void registerLeatherArmor(List<String> failed) {
        RecipeChoice pl = exact("padded_leather");

        ShapedRecipe helmet = new ShapedRecipe(key("padded_leather_helmet"), new ItemStack(Material.LEATHER_HELMET));
        helmet.shape("PPP", "P P");
        helmet.setIngredient('P', pl);
        add(helmet, "padded_leather_helmet", failed);

        ShapedRecipe chest = new ShapedRecipe(key("padded_leather_chestplate"), new ItemStack(Material.LEATHER_CHESTPLATE));
        chest.shape("P P", "PPP", "PPP");
        chest.setIngredient('P', pl);
        add(chest, "padded_leather_chestplate", failed);

        ShapedRecipe legs = new ShapedRecipe(key("padded_leather_leggings"), new ItemStack(Material.LEATHER_LEGGINGS));
        legs.shape("PPP", "P P", "P P");
        legs.setIngredient('P', pl);
        add(legs, "padded_leather_leggings", failed);

        ShapedRecipe boots = new ShapedRecipe(key("padded_leather_boots"), new ItemStack(Material.LEATHER_BOOTS));
        boots.shape("P P", "P P");
        boots.setIngredient('P', pl);
        add(boots, "padded_leather_boots", failed);
    }

    /**
     * Chainmail armor crafted from chainmail plates in vanilla armor patterns.
     * Results are vanilla CHAINMAIL_* items.
     */
    private static void registerChainmailArmor(List<String> failed) {
        RecipeChoice cp = exact("armor_plate_chainmail");

        ShapedRecipe helmet = new ShapedRecipe(key("chainmail_helmet"), new ItemStack(Material.CHAINMAIL_HELMET));
        helmet.shape("PPP", "P P");
        helmet.setIngredient('P', cp);
        add(helmet, "chainmail_helmet", failed);

        ShapedRecipe chest = new ShapedRecipe(key("chainmail_chestplate"), new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        chest.shape("P P", "PPP", "PPP");
        chest.setIngredient('P', cp);
        add(chest, "chainmail_chestplate", failed);

        ShapedRecipe legs = new ShapedRecipe(key("chainmail_leggings"), new ItemStack(Material.CHAINMAIL_LEGGINGS));
        legs.shape("PPP", "P P", "P P");
        legs.setIngredient('P', cp);
        add(legs, "chainmail_leggings", failed);

        ShapedRecipe boots = new ShapedRecipe(key("chainmail_boots"), new ItemStack(Material.CHAINMAIL_BOOTS));
        boots.shape("P P", "P P");
        boots.setIngredient('P', cp);
        add(boots, "chainmail_boots", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Per-metal crafting: plates, plate sets, armor pieces, tool heads
    // ─────────────────────────────────────────────────────────────

    /**
     * @param usePlatesDirectly if true, armor pieces and tool heads use plates
     *                          instead of plate stacks, and the plate set recipe is skipped.
     */
    private static void registerMetalCrafting(String metal, Material ingot, boolean usePlatesDirectly, String resultSuffix, List<String> failed) {
        registerPlate(metal, ingot, failed);
        if (!usePlatesDirectly) {
            registerPlateSet(metal, failed);
        } else {
            // ─── Copper plate set recipe disabled — copper uses plates directly ───
        }
        registerArmorPieces(metal, usePlatesDirectly, resultSuffix, failed);
        registerToolHeads(metal, usePlatesDirectly, resultSuffix, failed);
    }

    /**
     * 3 ingots in a row → 2 plates
     * <pre>III</pre>
     */
    private static void registerPlate(String metal, Material ingot, List<String> failed) {
        String id = "armor_plate_" + metal;
        ShapedRecipe r = new ShapedRecipe(key(id), stack(id, 2));
        r.shape("III");
        // Use ExactChoice with a plain ingot so that custom items sharing the
        // same base material (e.g. plates, plate sets) are NOT accepted.
        r.setIngredient('I', new RecipeChoice.ExactChoice(new ItemStack(ingot)));
        add(r, id, failed);
    }

    /**
     * 3 plates in a column → 1 plate set
     * <pre>
     * P
     * P
     * P
     * </pre>
     */
    private static void registerPlateSet(String metal, List<String> failed) {
        String id = metal + "_armor_plateset";
        ShapedRecipe r = new ShapedRecipe(key(id), stack(id));
        r.shape("P", "P", "P");
        r.setIngredient('P', exact("armor_plate_" + metal));
        add(r, id, failed);
    }

    /**
     * Armor pieces use plate sets in vanilla armor crafting patterns.
     * <pre>
     * Helm:        PPP    Breastplate: P P    Greaves: PPP    Sabaton: P P
     *              P P                 PPP              P P             P P
     *                                  PPP              P P
     * </pre>
     */
    private static void registerArmorPieces(String metal, boolean usePlatesDirectly, String resultSuffix, List<String> failed) {
        RecipeChoice ps = usePlatesDirectly
                ? exact("armor_plate_" + metal)
                : exact(metal + "_armor_plateset");

        // Helm: PPP / P P
        ShapedRecipe helm = new ShapedRecipe(key(metal + "_helm" + resultSuffix), stack(metal + "_helm" + resultSuffix));
        helm.shape("PPP", "P P");
        helm.setIngredient('P', ps);
        add(helm, metal + "_helm" + resultSuffix, failed);

        // Breastplate: P P / PPP / PPP
        ShapedRecipe breast = new ShapedRecipe(key(metal + "_breastplate" + resultSuffix), stack(metal + "_breastplate" + resultSuffix));
        breast.shape("P P", "PPP", "PPP");
        breast.setIngredient('P', ps);
        add(breast, metal + "_breastplate" + resultSuffix, failed);

        // Greaves: PPP / P P / P P
        ShapedRecipe greaves = new ShapedRecipe(key(metal + "_greaves" + resultSuffix), stack(metal + "_greaves" + resultSuffix));
        greaves.shape("PPP", "P P", "P P");
        greaves.setIngredient('P', ps);
        add(greaves, metal + "_greaves" + resultSuffix, failed);

        // Sabaton: P P / P P
        ShapedRecipe sabaton = new ShapedRecipe(key(metal + "_sabaton" + resultSuffix), stack(metal + "_sabaton" + resultSuffix));
        sabaton.shape("P P", "P P");
        sabaton.setIngredient('P', ps);
        add(sabaton, metal + "_sabaton" + resultSuffix, failed);
    }

    /**
     * Tool heads use plate sets in the material portion of vanilla tool patterns.
     * <pre>
     * Sword: P    Axe: PP    Pickaxe: PPP    Hoe: PP    Shovel: P
     *        P         P
     * </pre>
     */
    private static void registerToolHeads(String metal, boolean usePlatesDirectly, String resultSuffix, List<String> failed) {
        RecipeChoice ps = usePlatesDirectly
                ? exact("armor_plate_" + metal)
                : exact(metal + "_armor_plateset");

        // Sword head: P / P (2 plate sets)
        ShapedRecipe sword = new ShapedRecipe(key(metal + "_sword_head" + resultSuffix), stack(metal + "_sword_head" + resultSuffix));
        sword.shape("P", "P");
        sword.setIngredient('P', ps);
        add(sword, metal + "_sword_head" + resultSuffix, failed);

        // Axe head: PP / P_ (3 plate sets)
        ShapedRecipe axe = new ShapedRecipe(key(metal + "_axe_head" + resultSuffix), stack(metal + "_axe_head" + resultSuffix));
        axe.shape("PP", "P ");
        axe.setIngredient('P', ps);
        add(axe, metal + "_axe_head" + resultSuffix, failed);

        // Pickaxe head: PPP (3 plate sets)
        ShapedRecipe pick = new ShapedRecipe(key(metal + "_pickaxe_head" + resultSuffix), stack(metal + "_pickaxe_head" + resultSuffix));
        pick.shape("PPP");
        pick.setIngredient('P', ps);
        add(pick, metal + "_pickaxe_head" + resultSuffix, failed);

        // Hoe head: PP (2 plate sets)
        ShapedRecipe hoe = new ShapedRecipe(key(metal + "_hoe_head" + resultSuffix), stack(metal + "_hoe_head" + resultSuffix));
        hoe.shape("PP");
        hoe.setIngredient('P', ps);
        add(hoe, metal + "_hoe_head" + resultSuffix, failed);

        // Shovel head: P (1 plate set)
        ShapedRecipe shovel = new ShapedRecipe(key(metal + "_shovel_head" + resultSuffix), stack(metal + "_shovel_head" + resultSuffix));
        shovel.shape("P");
        shovel.setIngredient('P', ps);
        add(shovel, metal + "_shovel_head" + resultSuffix, failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Smithing: armor piece + leather armor → finished armor
    // ─────────────────────────────────────────────────────────────

    /** Maps: [pieceSuffix, armorSlot, leatherMat, goldMat, ironMat] */
    private static final String[][] ARMOR_MAP = {
        {"helm",        "helmet",     "LEATHER_HELMET",     "GOLDEN_HELMET",     "IRON_HELMET"},
        {"breastplate", "chestplate", "LEATHER_CHESTPLATE", "GOLDEN_CHESTPLATE", "IRON_CHESTPLATE"},
        {"greaves",     "leggings",   "LEATHER_LEGGINGS",   "GOLDEN_LEGGINGS",   "IRON_LEGGINGS"},
        {"sabaton",     "boots",      "LEATHER_BOOTS",      "GOLDEN_BOOTS",      "IRON_BOOTS"},
    };

    /**
     * Smithing Transform recipes:
     *   Template: (empty)  |  Base: leather armor  |  Addition: armor piece
     *   → Result: copper custom armor  OR  vanilla gold/iron armor
     */
    private static void registerArmorSmithing(String metal, boolean isCopper, List<String> failed) {
        for (String[] entry : ARMOR_MAP) {
            String piece     = entry[0]; // helm, breastplate, greaves, sabaton
            String slot      = entry[1]; // helmet, chestplate, leggings, boots
            Material leather = Material.valueOf(entry[2]);

            ItemStack result;
            String name;

            if (isCopper) {
                // Result is a custom copper armor item on leather base
                result = stack("copper_" + slot);
                name   = "copper_" + slot + "_smithing";
            } else if (metal.equals("gold")) {
                result = new ItemStack(Material.valueOf(entry[3]));
                name   = "gold_" + slot + "_smithing";
            } else { // iron
                result = new ItemStack(Material.valueOf(entry[4]));
                name   = "iron_" + slot + "_smithing";
            }

            // Copper accepts any leather armor of the right slot — damaged, enchanted, etc.
            // The leather's remaining-durability % is transferred onto the result in
            // SmithingAssemblyListener. Gold keeps ExactChoice so only pristine leather
            // can craft gold armor (the original pattern).
            RecipeChoice baseChoice = isCopper
                ? new RecipeChoice.MaterialChoice(leather)
                : new RecipeChoice.ExactChoice(new ItemStack(leather));

            SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                key(name),
                result,
                null,                                                      // no template
                baseChoice,                                                // base: leather armor
                exact(metal + "_" + piece)                                 // addition: armor piece
            );
            add(recipe, name, failed);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Tool Assembly: tool head + tool handle → finished tool
    // ─────────────────────────────────────────────────────────────

    /** Maps: [headSuffix, toolName, stoneMat, goldMat, ironMat] */
    private static final String[][] TOOL_MAP = {
        {"sword_head",   "sword",   "STONE_SWORD",   "GOLDEN_SWORD",   "IRON_SWORD"},
        {"axe_head",     "axe",     "STONE_AXE",     "GOLDEN_AXE",     "IRON_AXE"},
        {"pickaxe_head", "pickaxe", "STONE_PICKAXE", "GOLDEN_PICKAXE", "IRON_PICKAXE"},
        {"hoe_head",     "hoe",     "STONE_HOE",     "GOLDEN_HOE",     "IRON_HOE"},
        {"shovel_head",  "shovel",  "STONE_SHOVEL",  "GOLDEN_SHOVEL",  "IRON_SHOVEL"},
    };

    /**
     * Shaped recipe:
     * <pre>
     *  H
     *  T
     * </pre>
     * H = tool head (ExactChoice),  T = tool handle (ExactChoice)
     * → Result: copper custom tool  OR  vanilla gold/iron tool
     */
    private static void registerToolAssembly(String metal, boolean isCopper, List<String> failed) {
        for (String[] entry : TOOL_MAP) {
            String head = entry[0]; // sword_head, axe_head, …
            String tool = entry[1]; // sword, axe, …

            ItemStack result;
            String name;

            if (isCopper) {
                result = stack("copper_" + tool);
                name   = "copper_" + tool + "_assembly";
            } else if (metal.equals("gold")) {
                result = new ItemStack(Material.valueOf(entry[3]));
                name   = "gold_" + tool + "_assembly";
            } else { // iron
                result = new ItemStack(Material.valueOf(entry[4]));
                name   = "iron_" + tool + "_assembly";
            }

            ShapedRecipe recipe = new ShapedRecipe(key(name), result);
            recipe.shape("H", "T");
            recipe.setIngredient('H', exact(metal + "_" + head));
            recipe.setIngredient('T', exact("tool_handle"));
            add(recipe, name, failed);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Bronze recipes
    // ─────────────────────────────────────────────────────────────

    /**
     * Crushed Bronze Blend: raw copper surrounding, coal in cross, raw gold center → yields 1.
     * <pre>
     *  #1#
     *  121
     *  #1#
     * </pre>
     * # = Raw Copper, 1 = Coal, 2 = Raw Gold
     */
    private static void registerBronzeBlend(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("bronze_blend"), stack("bronze_blend", 1));
        r.shape("#1#", "121", "#1#");
        r.setIngredient('#', Material.RAW_COPPER);
        r.setIngredient('1', Material.COAL);
        r.setIngredient('2', Material.RAW_GOLD);
        add(r, "bronze_blend", failed);
    }

    /**
     * Bronze Ingot: smelt a bronze blend in a furnace.
     */
    private static void registerBronzeIngotSmelting(List<String> failed) {
        // Furnace
        FurnaceRecipe furnace = new FurnaceRecipe(
            key("bronze_ingot"),
            stack("bronze_ingot"),
            new RecipeChoice.ExactChoice(stack("bronze_blend")),
            0.7f,    // experience (same as gold)
            200      // cook time ticks (10 seconds)
        );
        add(furnace, "bronze_ingot", failed);

        // Blast Furnace (half the cook time)
        BlastingRecipe blast = new BlastingRecipe(
            key("bronze_ingot_blasting"),
            stack("bronze_ingot"),
            new RecipeChoice.ExactChoice(stack("bronze_blend")),
            0.7f,
            100      // 5 seconds
        );
        add(blast, "bronze_ingot_blasting", failed);
    }

    /**
     * Bronze crafting chain:
     * 3 bronze ingots → 2 bronze plates
     * plates → armor pieces (helm, breastplate, greaves, sabaton)
     * plates → tool heads (sword, axe, pickaxe, hoe, shovel)
     */
    private static void registerBronzeCrafting(List<String> failed) {
        // Plate: 3 bronze ingots in a row → 2 plates
        ShapedRecipe plate = new ShapedRecipe(key("armor_plate_bronze"), stack("armor_plate_bronze", 2));
        plate.shape("III");
        plate.setIngredient('I', exact("bronze_ingot"));
        add(plate, "armor_plate_bronze", failed);

        // ─── Bronze plate set recipe disabled — bronze uses plates directly ───
        // ShapedRecipe plateset = new ShapedRecipe(key("bronze_armor_plateset"), stack("bronze_armor_plateset"));
        // plateset.shape("P", "P", "P");
        // plateset.setIngredient('P', exact("armor_plate_bronze"));
        // add(plateset, "bronze_armor_plateset", failed);

        // Armor pieces (use plates directly)
        RecipeChoice ps = exact("armor_plate_bronze");

        ShapedRecipe helm = new ShapedRecipe(key("bronze_helm"), stack("bronze_helm"));
        helm.shape("PPP", "P P");
        helm.setIngredient('P', ps);
        add(helm, "bronze_helm", failed);

        ShapedRecipe breast = new ShapedRecipe(key("bronze_breastplate"), stack("bronze_breastplate"));
        breast.shape("P P", "PPP", "PPP");
        breast.setIngredient('P', ps);
        add(breast, "bronze_breastplate", failed);

        ShapedRecipe greaves = new ShapedRecipe(key("bronze_greaves"), stack("bronze_greaves"));
        greaves.shape("PPP", "P P", "P P");
        greaves.setIngredient('P', ps);
        add(greaves, "bronze_greaves", failed);

        ShapedRecipe sabaton = new ShapedRecipe(key("bronze_sabaton"), stack("bronze_sabaton"));
        sabaton.shape("P P", "P P");
        sabaton.setIngredient('P', ps);
        add(sabaton, "bronze_sabaton", failed);

        // Tool heads (use plates directly)
        ShapedRecipe sword = new ShapedRecipe(key("bronze_sword_head"), stack("bronze_sword_head"));
        sword.shape("P", "P");
        sword.setIngredient('P', ps);
        add(sword, "bronze_sword_head", failed);

        ShapedRecipe axe = new ShapedRecipe(key("bronze_axe_head"), stack("bronze_axe_head"));
        axe.shape("PP", "P ");
        axe.setIngredient('P', ps);
        add(axe, "bronze_axe_head", failed);

        ShapedRecipe pick = new ShapedRecipe(key("bronze_pickaxe_head"), stack("bronze_pickaxe_head"));
        pick.shape("PPP");
        pick.setIngredient('P', ps);
        add(pick, "bronze_pickaxe_head", failed);

        ShapedRecipe hoe = new ShapedRecipe(key("bronze_hoe_head"), stack("bronze_hoe_head"));
        hoe.shape("PP");
        hoe.setIngredient('P', ps);
        add(hoe, "bronze_hoe_head", failed);

        ShapedRecipe shovel = new ShapedRecipe(key("bronze_shovel_head"), stack("bronze_shovel_head"));
        shovel.shape("P");
        shovel.setIngredient('P', ps);
        add(shovel, "bronze_shovel_head", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Blueprints: crafting + blueprint-required smithing (iron & bronze)
    // ─────────────────────────────────────────────────────────────

    /** Maps: [componentSuffix, resultName] for blueprint naming. */
    private static final String[][] BP_PIECE_MAP = {
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
     * Blueprint crafting: component + paper + light blue dye → blueprint (shapeless).
     * Blueprint is named after the RESULT, e.g. iron_helm + paper + dye → iron_helmet_blueprint.
     */
    private static void registerBlueprints(String metal, List<String> failed) {
        for (String[] entry : BP_PIECE_MAP) {
            String piece  = entry[0]; // helm, sword_head, etc.
            String result = entry[1]; // helmet, sword, etc.
            String compId = metal + "_" + piece;
            String bpId   = metal + "_" + result + "_blueprint";

            // Steel blueprints are handled manually in CraftingListener.onPrepareItemCraft
            // because hardened/tempered steel pieces have extra PDC that ExactChoice can't match.
            if (metal.equals("steel")) continue;

            ShapelessRecipe r = new ShapelessRecipe(key(bpId), stack(bpId));
            r.addIngredient(new RecipeChoice.ExactChoice(stack(compId)));
            r.addIngredient(Material.PAPER);
            r.addIngredient(Material.LIGHT_BLUE_DYE);
            add(r, bpId, failed);
        }
    }

    /** Maps piece suffix → [armorSlot, leatherMat, ironResultMat] */
    private static final String[][] BP_ARMOR_MAP = {
        {"helm",        "helmet",     "LEATHER_HELMET",     "IRON_HELMET"},
        {"breastplate", "chestplate", "LEATHER_CHESTPLATE", "IRON_CHESTPLATE"},
        {"greaves",     "leggings",   "LEATHER_LEGGINGS",   "IRON_LEGGINGS"},
        {"sabaton",     "boots",      "LEATHER_BOOTS",      "IRON_BOOTS"},
    };

    /**
     * Blueprint-required armor smithing:
     *   Template: result-named blueprint | Base: leather armor | Addition: armor piece → finished armor
     */
    private static void registerBlueprintArmorSmithing(String metal, List<String> failed) {
        for (String[] entry : BP_ARMOR_MAP) {
            try {
                String piece     = entry[0]; // helm, breastplate, etc.
                String slot      = entry[1]; // helmet, chestplate, etc.
                Material leather = Material.valueOf(entry[2]);

                ItemStack result;
                String name;

                if (metal.equals("iron")) {
                    result = new ItemStack(Material.valueOf(entry[3]));
                    name   = "iron_" + slot + "_smithing";
                } else {
                    // bronze or steel — custom item result
                    result = stack(metal + "_" + slot);
                    name   = metal + "_" + slot + "_smithing";
                }

                // Any leather armor of the right slot is accepted; the leather's
                // remaining-durability % is transferred onto the result in
                // SmithingAssemblyListener.
                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    key(name),
                    result,
                    exact(metal + "_" + slot + "_blueprint"),              // template: result-named blueprint
                    new RecipeChoice.MaterialChoice(leather),              // base: any leather armor of this slot
                    exact(metal + "_" + piece)                             // addition: armor piece
                );
                add(recipe, name, failed);
            } catch (Exception e) {
                failed.add(metal + "_" + entry[1] + "_smithing [setup] (" + e.getMessage() + ")");
            }
        }
    }

    /** Maps piece suffix → [toolName, ironResultMat] */
    private static final String[][] BP_TOOL_MAP = {
        {"sword_head",   "sword",   "IRON_SWORD"},
        {"axe_head",     "axe",     "IRON_AXE"},
        {"pickaxe_head", "pickaxe", "IRON_PICKAXE"},
        {"hoe_head",     "hoe",     "IRON_HOE"},
        {"shovel_head",  "shovel",  "IRON_SHOVEL"},
    };

    /**
     * Blueprint-required tool assembly:
     *   Template: result-named blueprint | Base: tool handle | Addition: tool head → finished tool
     *   SmithingAssemblyListener handles client-side display and name fixing.
     */
    private static void registerBlueprintToolSmithing(String metal, List<String> failed) {
        for (String[] entry : BP_TOOL_MAP) {
            try {
                String head = entry[0];
                String tool = entry[1];

                ItemStack result;
                String name;

                if (metal.equals("iron")) {
                    result = new ItemStack(Material.valueOf(entry[2]));
                    name   = "iron_" + tool + "_assembly";
                } else {
                    // bronze or steel — custom item result
                    result = stack(metal + "_" + tool);
                    name   = metal + "_" + tool + "_assembly";
                }

                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    key(name),
                    result,
                    exact(metal + "_" + tool + "_blueprint"),  // template
                    exact("tool_handle"),                      // base
                    exact(metal + "_" + head)                  // addition
                );
                add(recipe, name, failed);
            } catch (Exception e) {
                failed.add(metal + "_" + entry[1] + "_assembly [setup] (" + e.getMessage() + ")");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Smithing Table (bronze alternative)
    // ─────────────────────────────────────────────────────────────

    /**
     * Smithing Table: 2 bronze ingots on top + 4 planks on bottom (replaces iron in vanilla recipe).
     * <pre>
     *  BB_
     *  PPP
     *  PPP
     * </pre>
     * (Intentionally non-standard: vanilla uses 2 iron + 4 planks in a 2-wide shape.
     *  We use 3-wide with the bronze on the left to avoid conflicts.)
     */
    private static void registerBronzeSmithingTable(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("bronze_smithing_table"), new ItemStack(Material.SMITHING_TABLE));
        r.shape("BB", "PP", "PP");
        r.setIngredient('B', exact("bronze_ingot"));
        r.setIngredient('P', new RecipeChoice.MaterialChoice(Tag.PLANKS));
        add(r, "bronze_smithing_table", failed);
    }

    /**
     * Bronze Anvil (Chipped): 7 bronze plates in an anvil pattern.
     * <pre>
     *  PPP
     *   P
     *  PPP
     * </pre>
     */
    private static void registerBronzeAnvil(List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key("bronze_chipped_anvil"), new ItemStack(Material.CHIPPED_ANVIL));
        r.shape("PPP", " P ", "PPP");
        r.setIngredient('P', exact("armor_plate_bronze"));
        add(r, "bronze_chipped_anvil", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Crushed Ores: crafting + smelting
    // ─────────────────────────────────────────────────────────────

    /**
     * Crushed ore crafting and smelting.
     * Pattern (same cross as bronze blend):
     * <pre>
     *  R C R
     *  C R C
     *  R C R
     * </pre>
     * R = 5 raw ore, C = 4 coal
     * Copper → 1, Gold → 2, Iron → 3
     *
     * Smelting: crushed ore → ingot (furnace + blast furnace)
     */
    private static void registerCrushedOres(List<String> failed) {
        // ─── Crafting ───
        registerCrushedOreCrafting("crushed_copper_ore", Material.RAW_COPPER, 1, failed);
        registerCrushedOreCrafting("crushed_iron_ore",   Material.RAW_IRON,   3, failed);
        registerCrushedOreCrafting("crushed_gold_ore",   Material.RAW_GOLD,   2, failed);

        // ─── Smelting: copper & gold → direct ingot (furnace + blast furnace) ───
        registerCrushedOreSmelting("crushed_copper_ore", Material.COPPER_INGOT, "copper_ingot", 0.7f, failed);
        registerCrushedOreSmelting("crushed_gold_ore",   Material.GOLD_INGOT,   "gold_ingot",   1.0f, failed);
    }

    private static void registerCrushedOreCrafting(String id, Material rawOre, int yield, List<String> failed) {
        ShapedRecipe r = new ShapedRecipe(key(id), stack(id, yield));
        r.shape("RCR", "CRC", "RCR");
        r.setIngredient('R', rawOre);
        r.setIngredient('C', Material.COAL);
        add(r, id, failed);
    }

    private static void registerCrushedOreSmelting(String crushedId, Material resultIngot, String keyPrefix, float xp, List<String> failed) {
        // Furnace
        FurnaceRecipe furnace = new FurnaceRecipe(
            key(keyPrefix + "_from_crushed"),
            new ItemStack(resultIngot),
            new RecipeChoice.ExactChoice(stack(crushedId)),
            xp,
            200  // 10 seconds
        );
        add(furnace, keyPrefix + "_from_crushed", failed);

        // Blast Furnace (half cook time)
        BlastingRecipe blast = new BlastingRecipe(
            key(keyPrefix + "_from_crushed_blasting"),
            new ItemStack(resultIngot),
            new RecipeChoice.ExactChoice(stack(crushedId)),
            xp,
            100  // 5 seconds
        );
        add(blast, keyPrefix + "_from_crushed_blasting", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Steel System: coal coke, steel blend, pig iron
    // ─────────────────────────────────────────────────────────────

    /**
     * Coal → Coal Coke in regular furnace (40 seconds).
     * IronBloomSystem overrides the result and blocks blast furnace / re-smelting.
     */
    private static void registerCoalCokeSmelting(List<String> failed) {
        try {
            FurnaceRecipe coke = new FurnaceRecipe(
                key("coal_coke_smelting"),
                stack("coal_coke"),
                new RecipeChoice.MaterialChoice(Material.COAL),
                1.0f,  // 1 vanilla experience point
                800  // 40 seconds
            );
            add(coke, "coal_coke_smelting", failed);
        } catch (Exception e) {
            failed.add("coal_coke_smelting [setup] (" + e.getMessage() + ")");
        }
    }

    /**
     * Steel Blend crafting (TNT pattern: 5 raw iron + 4 coal coke → 3 steel blend)
     * and blast furnace smelting (steel blend → pig iron, overridden by IronBloomSystem).
     */
    private static void registerSteelBlend(List<String> failed) {
        try {
            // Crafting: 5 raw iron + 4 coal coke in TNT pattern → 3 steel blend
            ShapedRecipe craft = new ShapedRecipe(key("steel_blend"), stack("steel_blend", 2));
            craft.shape("RCR", "CRC", "RCR");
            craft.setIngredient('R', Material.RAW_IRON);
            craft.setIngredient('C', exact("coal_coke"));
            add(craft, "steel_blend", failed);

            // Blast furnace: steel blend → pig iron (result overridden by IronBloomSystem)
            BlastingRecipe blast = new BlastingRecipe(
                key("steel_blend_smelting"),
                stack("pig_iron"),
                new RecipeChoice.ExactChoice(stack("steel_blend")),
                0.0f,
                400  // 20 seconds (overridden by FurnaceStartSmeltEvent)
            );
            add(blast, "steel_blend_smelting", failed);
        } catch (Exception e) {
            failed.add("steel_blend [setup] (" + e.getMessage() + ")");
        }
    }

    /**
     * Smoker recipe for steel tempering. Uses MaterialChoice(IRON_INGOT) so any
     * hardened steel piece can be placed. IronBloomSystem blocks non-steel items
     * and handles the lore-based tempering progression.
     */
    private static void registerSteelTempering(List<String> failed) {
        try {
            SmokingRecipe temper = new SmokingRecipe(
                key("steel_tempering"),
                new ItemStack(Material.IRON_INGOT), // placeholder result — never fires
                new RecipeChoice.MaterialChoice(Material.IRON_INGOT),
                0.0f,
                Integer.MAX_VALUE // never completes — tempering is lore-based
            );
            add(temper, "steel_tempering", failed);
        } catch (Exception e) {
            failed.add("steel_tempering [setup] (" + e.getMessage() + ")");
        }
    }

    /**
     * Pig iron reheat in blast furnace (with coal coke as fuel).
     * Regular furnace reheat is covered by the iron_bloom_reheat FurnaceRecipe
     * (MaterialChoice CARROT_ON_A_STICK). Blast furnace needs its own BlastingRecipe.
     */
    private static void registerPigIronReheat(List<String> failed) {
        try {
            BlastingRecipe reheat = new BlastingRecipe(
                key("pig_iron_reheat_blasting"),
                stack("pig_iron"),
                new RecipeChoice.MaterialChoice(Material.CARROT_ON_A_STICK),
                0.0f,
                160  // overridden by FurnaceStartSmeltEvent
            );
            add(reheat, "pig_iron_reheat_blasting", failed);
        } catch (Exception e) {
            failed.add("pig_iron_reheat_blasting [setup] (" + e.getMessage() + ")");
        }
    }

    /**
     * Steel intermediates: plate, plate set, armor pieces, tool heads.
     * Uses steel_ingot (ExactChoice) instead of vanilla IRON_INGOT to prevent
     * wrought iron or vanilla iron from substituting.
     * Follows the same patterns as iron (plate set based, no _raw suffix).
     */
    private static void registerSteelCrafting(List<String> failed) {
        try {
        // Steel plates and platesets are made on the anvil (matching iron system):
        //   heated steel ingot (Orange+) + hammer → armor_plate_steel
        //   2 heated steel plates (Orange+) → steel_armor_plateset

        // ─── Armor pieces from steel_armor_plateset (produce _raw variants) ───
        RecipeChoice ps = exact("steel_armor_plateset");

        ShapedRecipe helm = new ShapedRecipe(key("steel_helm_raw"), stack("steel_helm_raw"));
        helm.shape("PPP", "P P");
        helm.setIngredient('P', ps);
        add(helm, "steel_helm_raw", failed);

        ShapedRecipe breast = new ShapedRecipe(key("steel_breastplate_raw"), stack("steel_breastplate_raw"));
        breast.shape("P P", "PPP", "PPP");
        breast.setIngredient('P', ps);
        add(breast, "steel_breastplate_raw", failed);

        ShapedRecipe greaves = new ShapedRecipe(key("steel_greaves_raw"), stack("steel_greaves_raw"));
        greaves.shape("PPP", "P P", "P P");
        greaves.setIngredient('P', ps);
        add(greaves, "steel_greaves_raw", failed);

        ShapedRecipe sabaton = new ShapedRecipe(key("steel_sabaton_raw"), stack("steel_sabaton_raw"));
        sabaton.shape("P P", "P P");
        sabaton.setIngredient('P', ps);
        add(sabaton, "steel_sabaton_raw", failed);

        // ─── Tool heads from steel_armor_plateset (produce _raw variants) ───
        ShapedRecipe sword = new ShapedRecipe(key("steel_sword_head_raw"), stack("steel_sword_head_raw"));
        sword.shape("P", "P");
        sword.setIngredient('P', ps);
        add(sword, "steel_sword_head_raw", failed);

        ShapedRecipe axe = new ShapedRecipe(key("steel_axe_head_raw"), stack("steel_axe_head_raw"));
        axe.shape("PP", "P ");
        axe.setIngredient('P', ps);
        add(axe, "steel_axe_head_raw", failed);

        ShapedRecipe pick = new ShapedRecipe(key("steel_pickaxe_head_raw"), stack("steel_pickaxe_head_raw"));
        pick.shape("PPP");
        pick.setIngredient('P', ps);
        add(pick, "steel_pickaxe_head_raw", failed);

        ShapedRecipe hoe = new ShapedRecipe(key("steel_hoe_head_raw"), stack("steel_hoe_head_raw"));
        hoe.shape("PP");
        hoe.setIngredient('P', ps);
        add(hoe, "steel_hoe_head_raw", failed);

        ShapedRecipe shovel = new ShapedRecipe(key("steel_shovel_head_raw"), stack("steel_shovel_head_raw"));
        shovel.shape("P");
        shovel.setIngredient('P', ps);
        add(shovel, "steel_shovel_head_raw", failed);

        // ─── Steel hammer head: 4 steel plate sets in 2×2 → raw (needs anvil working) ───
        ShapedRecipe hammerHead = new ShapedRecipe(key("steel_hammer_head_raw"), stack("steel_hammer_head_raw"));
        hammerHead.shape("SS", "SS");
        hammerHead.setIngredient('S', exact("steel_armor_plateset"));
        add(hammerHead, "steel_hammer_head_raw", failed);
        } catch (Exception e) {
            failed.add("steel_crafting [setup] (" + e.getMessage() + ")");
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Iron Bloom: initial smelting + reheat
    // ─────────────────────────────────────────────────────────────

    /**
     * Iron bloom creation and reheating.
     * IronBloomSystem.onFurnaceSmelt overrides the results with properly
     * initialized blooms (damage + heat level).
     * Reheat uses MaterialChoice so worked blooms with different damage/PDC match.
     */
    private static void registerIronBloomSmelting(List<String> failed) {
        // Initial: crushed_iron_ore → bloom
        FurnaceRecipe create = new FurnaceRecipe(
            key("iron_bloom_from_crushed"),
            stack("iron_bloom"),
            new RecipeChoice.ExactChoice(stack("crushed_iron_ore")),
            0.7f,
            200  // 10 seconds
        );
        add(create, "iron_bloom_from_crushed", failed);

        // Reheat: CARROT_ON_A_STICK → bloom (MaterialChoice so any damage/PDC matches)
        // IronBloomSystem validates it's actually an iron_bloom and blocks others
        FurnaceRecipe reheat = new FurnaceRecipe(
            key("iron_bloom_reheat"),
            stack("iron_bloom"),
            new RecipeChoice.MaterialChoice(Material.CARROT_ON_A_STICK),
            0.0f,
            160  // overridden by FurnaceStartSmeltEvent
        );
        add(reheat, "iron_bloom_reheat", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Iron Plate: heating iron ingots (+ armor_plate_iron reheating)
    // ─────────────────────────────────────────────────────────────

    /**
     * Furnace recipe for heating iron ingots and reheating armor_plate_iron.
     * Both are IRON_INGOT base, so one MaterialChoice recipe handles both.
     * IronBloomSystem.onFurnaceSmelt distinguishes by custom ID and sets
     * the result with Yellow Hot + unstackable.
     */
    private static void registerIronIngotHeating(List<String> failed) {
        FurnaceRecipe heat = new FurnaceRecipe(
            key("iron_ingot_heat"),
            new ItemStack(Material.IRON_INGOT),
            new RecipeChoice.MaterialChoice(Material.IRON_INGOT),
            0.0f,
            200  // 10 seconds default; overridden by FurnaceStartSmeltEvent for plates
        );
        add(heat, "iron_ingot_heat", failed);
    }

    // ─────────────────────────────────────────────────────────────
    //  Hammer system: heads, blueprints, assembly
    // ─────────────────────────────────────────────────────────────

    private static void registerHammerHeads(List<String> failed) {
        // Bronze hammer head: 4 bronze plates in 2x2
        ShapedRecipe bronzeHead = new ShapedRecipe(key("bronze_hammer_head"), stack("bronze_hammer_head"));
        bronzeHead.shape("PP", "PP");
        bronzeHead.setIngredient('P', exact("armor_plate_bronze"));
        add(bronzeHead, "bronze_hammer_head", failed);

        // Iron hammer head: 4 iron plate sets (cold) in 2x2 → raw (needs anvil working)
        ShapedRecipe ironHead = new ShapedRecipe(key("iron_hammer_head_raw"), stack("iron_hammer_head_raw"));
        ironHead.shape("SS", "SS");
        ironHead.setIngredient('S', exact("iron_armor_plateset"));
        add(ironHead, "iron_hammer_head_raw", failed);
    }

    private static void registerHammerBlueprints(List<String> failed) {
        // Steel hammer blueprint handled manually in CraftingListener (same as other steel blueprints)
        for (String metal : new String[]{"bronze", "iron"}) {
            String bpId = metal + "_hammer_blueprint";
            String headId = metal + "_hammer_head";

            ShapelessRecipe r = new ShapelessRecipe(key(bpId), stack(bpId));
            r.addIngredient(new RecipeChoice.ExactChoice(stack(headId)));
            r.addIngredient(Material.PAPER);
            r.addIngredient(Material.LIGHT_BLUE_DYE);
            add(r, bpId, failed);
        }
    }

    private static void registerHammerAssembly(List<String> failed) {
        for (String metal : new String[]{"bronze", "iron", "steel"}) {
            String name = metal + "_hammer_assembly";

            SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                key(name),
                stack(metal + "_hammer"),
                exact(metal + "_hammer_blueprint"),
                exact("tool_handle"),
                exact(metal + "_hammer_head")
            );
            add(recipe, name, failed);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Diamond recipes
    // ─────────────────────────────────────────────────────────────

    /**
     * Diamond plates and plate sets. Diamond differs from other metals:
     * the plate-to-plate-set ratio is 3 → 2 (other metals are 3 → 1),
     * so diamond is more efficient per plate than iron or steel.
     * <pre>
     *   3 diamonds   (row)    → 2 diamond plates
     *   3 plates     (column) → 2 diamond plate sets
     * </pre>
     */
    private static void registerDiamondPlates(List<String> failed) {
        // 3 diamonds → 2 plates
        ShapedRecipe plate = new ShapedRecipe(key("armor_plate_diamond"), stack("armor_plate_diamond", 2));
        plate.shape("DDD");
        plate.setIngredient('D', new RecipeChoice.ExactChoice(new ItemStack(Material.DIAMOND)));
        add(plate, "armor_plate_diamond", failed);

        // 3 plates → 2 plate sets
        ShapedRecipe plateset = new ShapedRecipe(key("diamond_armor_plateset"), stack("diamond_armor_plateset", 2));
        plateset.shape("P", "P", "P");
        plateset.setIngredient('P', exact("armor_plate_diamond"));
        add(plateset, "diamond_armor_plateset", failed);
    }

    /**
     * Diamond armor smithing:
     *   template = diamond_{slot}_blueprint
     *   base     = vanilla IRON_{slot}     (SmithingAssemblyListener validates it is toughened steel)
     *   addition = diamond_{piece}         (diamond_helm / diamond_breastplate / ...)
     *   result   = vanilla DIAMOND_{slot}; damage is set at prepare-time so the result
     *              preserves the same remaining-durability % as the steel base.
     *
     * The base is MaterialChoice because toughened-steel IRON_* items carry variable
     * PDC (attribute modifiers, temper tier, TOUGHENED_STEEL_KEY) that ExactChoice
     * can't match. Validation and durability transfer are done in the listener.
     */
    private static void registerDiamondArmorSmithing(List<String> failed) {
        for (String[] entry : BP_ARMOR_MAP) {
            try {
                String piece = entry[0];                     // helm, breastplate, greaves, sabaton
                String slot  = entry[1];                     // helmet, chestplate, leggings, boots
                Material ironMat    = Material.valueOf(entry[3]);
                Material diamondMat = Material.valueOf("DIAMOND_" + slot.toUpperCase());

                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    key("diamond_" + slot + "_smithing"),
                    new ItemStack(diamondMat),
                    exact("diamond_" + slot + "_blueprint"),
                    new RecipeChoice.MaterialChoice(ironMat),
                    exact("diamond_" + piece)
                );
                add(recipe, "diamond_" + slot + "_smithing", failed);
            } catch (Exception e) {
                failed.add("diamond_" + entry[1] + "_smithing [setup] (" + e.getMessage() + ")");
            }
        }
    }

    /**
     * Diamond tool smithing:
     *   template = diamond_{tool}_blueprint
     *   base     = vanilla IRON_{tool}    (validated as toughened steel in listener)
     *   addition = diamond_{tool}_head
     *   result   = vanilla DIAMOND_{tool}
     * Durability-transfer matches the armor case.
     */
    private static void registerDiamondToolSmithing(List<String> failed) {
        for (String[] entry : BP_TOOL_MAP) {
            try {
                String head = entry[0];                      // sword_head, axe_head, ...
                String tool = entry[1];                      // sword, axe, ...
                Material ironMat    = Material.valueOf(entry[2]);
                Material diamondMat = Material.valueOf("DIAMOND_" + tool.toUpperCase());

                SmithingTransformRecipe recipe = new SmithingTransformRecipe(
                    key("diamond_" + tool + "_assembly"),
                    new ItemStack(diamondMat),
                    exact("diamond_" + tool + "_blueprint"),
                    new RecipeChoice.MaterialChoice(ironMat),
                    exact("diamond_" + head)
                );
                add(recipe, "diamond_" + tool + "_assembly", failed);
            } catch (Exception e) {
                failed.add("diamond_" + entry[1] + "_assembly [setup] (" + e.getMessage() + ")");
            }
        }
    }
}

package com.minecraftcivilizations.specialization.Recipe;

import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItem;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItemRegistry;
import org.bukkit.*;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;

public class Recipes {

    public static void init() {
        registerCustomItems();
        unregisterRecipes();
        registerRecipes(false);
    }

    private static void registerCustomItems() {
        // Your custom items – unchanged
    }

    @SuppressWarnings("deprecation") // NamespacedKey(String,String) needed for non-plugin namespace "gearplus"
    public static void unregisterRecipes() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("rail"));

        // ─── Block vanilla leather armor recipes ───
        // Players must craft leather armor from padded leather.
        Bukkit.removeRecipe(NamespacedKey.minecraft("leather_helmet"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("leather_chestplate"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("leather_leggings"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("leather_boots"));

        // ─── Block vanilla iron armor & tool recipes ───
        // Players must use the plates system (MetalworkingRecipes) to craft these.
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_helmet"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_chestplate"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_leggings"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_boots"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_sword"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_pickaxe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_axe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_shovel"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_hoe"));

        // ─── Block vanilla gold armor & tool recipes ───
        // NOTE: Vanilla uses "golden_" prefix, NOT "gold_".
        // The BiomeDependentResources datapack overrides some of these with
        // "Bronze" recipes — removing them here blocks those too.
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_helmet"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_chestplate"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_leggings"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_boots"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_sword"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_pickaxe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_axe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_shovel"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("golden_hoe"));

        // ─── Block vanilla copper armor recipes (added in 1.21.5+) ───
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_helmet"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_chestplate"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_leggings"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_boots"));

        // ─── Block datapack copper tool recipes (gearplus namespace) ───
        // These are registered by BiomeDependentResources under data/gearplus/recipe/.
        NamespacedKey gpCopperAxe     = new NamespacedKey("gearplus", "copper_axe");
        NamespacedKey gpCopperPickaxe = new NamespacedKey("gearplus", "copper_pickaxe");
        NamespacedKey gpCopperShovel  = new NamespacedKey("gearplus", "copper_shovel");
        NamespacedKey gpCopperSword   = new NamespacedKey("gearplus", "copper_sword");
        NamespacedKey gpCopperHoe     = new NamespacedKey("gearplus", "copper_hoe");
        Bukkit.removeRecipe(gpCopperAxe);
        Bukkit.removeRecipe(gpCopperPickaxe);
        Bukkit.removeRecipe(gpCopperShovel);
        Bukkit.removeRecipe(gpCopperSword);
        Bukkit.removeRecipe(gpCopperHoe);

        // Defensive: also try minecraft-namespaced copper tools in case
        // a future Minecraft version adds them as vanilla recipes.
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_sword"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_pickaxe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_axe"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_shovel"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_hoe"));

        // ─── Block vanilla smelting of raw copper, iron, gold ───
        // Only crushed ores should yield ingots via smelting.
        // Raw ore → furnace
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_smelting_raw_copper"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_smelting_raw_iron"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_smelting_raw_gold"));
        // Raw ore → blast furnace
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_blasting_raw_copper"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_blasting_raw_iron"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_blasting_raw_gold"));
        // Ore block → furnace
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_smelting_copper_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_smelting_deepslate_copper_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_smelting_iron_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_smelting_deepslate_iron_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_smelting_gold_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_smelting_deepslate_gold_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_smelting_nether_gold_ore"));
        // Ore block → blast furnace
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_blasting_copper_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("copper_ingot_from_blasting_deepslate_copper_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_blasting_iron_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("iron_ingot_from_blasting_deepslate_iron_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_blasting_gold_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_blasting_deepslate_gold_ore"));
        Bukkit.removeRecipe(NamespacedKey.minecraft("gold_ingot_from_blasting_nether_gold_ore"));
    }

    public static void registerRecipes(boolean reloading) {
        int successCount = 0;
        int skippedMissingCount = 0;
        int skippedDuplicateCount = 0;
        List<String> failedExceptions = new ArrayList<>();

        // ----- CUSTOM ITEM RECIPES -----
        for (NamespacedKey key : CustomItemRegistry.getItems().keySet()) {
            CustomItem customItem = CustomItemRegistry.getItem(key);
            if (customItem == null) {
                skippedMissingCount++;
                continue;
            }

            if (recipeExists(key, customItem.getItem())) {
                skippedDuplicateCount++;
                continue;
            }

            try {
                Bukkit.addRecipe(new ShapelessRecipe(key, customItem.getItem()), true);
                successCount++;
            } catch (Exception e) {
                failedExceptions.add(key.getKey() + " (" + e.getMessage() + ")");
            }
        }

        // ----- CUSTOM "RAIL" RECIPE -----
        NamespacedKey railKey = new NamespacedKey(Specialization.getInstance(), "rail_alt");
        ShapedRecipe rail = new ShapedRecipe(railKey, new ItemStack(Material.RAIL, 64));
        rail.shape("I I", "ISI", "I I");
        rail.setIngredient('I', new RecipeChoice.ExactChoice(
                CustomItemManager.getInstance().getCustomItem("armor_plate_iron").createItemStack()));
        rail.setIngredient('S', Material.STICK);
        if (!recipeExists(railKey, rail.getResult())) {
            try {
                Bukkit.addRecipe(rail, true);
                successCount++;
            } catch (Exception e) {
                failedExceptions.add("rail_alt (" + e.getMessage() + ")");
            }
        } else skippedDuplicateCount++;

        // ----- EXTRA RECIPES -----
        successCount += addNetherRecipes(failedExceptions, skippedDuplicateCount);
        addUnobtainableRecipes(failedExceptions, skippedDuplicateCount);
        addArmorTrims(failedExceptions, skippedDuplicateCount);

        // ----- METALWORKING RECIPES (plates, plate sets, armor pieces, tool heads, smithing, assembly) -----
        successCount += MetalworkingRecipes.register(failedExceptions);

        // ----- FINAL LOG -----
        Bukkit.getLogger().info("[Recipes] Registration complete. Total successes: " + successCount);
        if (skippedMissingCount > 0)
            Bukkit.getLogger().info("[Recipes] Skipped " + skippedMissingCount + " recipes: missing items.");
        if (skippedDuplicateCount > 0)
            Bukkit.getLogger().info("[Recipes] Skipped " + skippedDuplicateCount + " recipes: duplicates.");
        if (!failedExceptions.isEmpty()) {
            Bukkit.getLogger().warning("[Recipes] Failed recipes due to exceptions (" + failedExceptions.size() + "):");
            failedExceptions.forEach(f -> Bukkit.getLogger().warning(" - " + f));
        }
    }

    private static boolean recipeExists(NamespacedKey key, ItemStack result) {
        return Bukkit.getRecipesFor(result).stream()
                .filter(r -> r instanceof Keyed)
                .map(r -> (Keyed) r)
                .anyMatch(r -> r.getKey().equals(key));
    }

    public static int addNetherRecipes(List<String> failedExceptions, int skippedDuplicateCount) {
        int count = 0;

        NamespacedKey netheriteKey = new NamespacedKey(Specialization.getInstance(), "netherite_upgrade");
        ShapelessRecipe netheriteUpgrade = new ShapelessRecipe(netheriteKey,
                new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        netheriteUpgrade.addIngredient(1, Material.NETHERITE_INGOT);
        netheriteUpgrade.addIngredient(6, Material.DIAMOND);
        netheriteUpgrade.addIngredient(1, Material.NETHER_WART_BLOCK);
        if (!recipeExists(netheriteKey, netheriteUpgrade.getResult())) {
            try { Bukkit.addRecipe(netheriteUpgrade, true); count++; }
            catch (Exception e) { failedExceptions.add("netherite_upgrade (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey blazeRodKey = new NamespacedKey(Specialization.getInstance(), "blaze_rod");
        ShapelessRecipe blazeRod = new ShapelessRecipe(blazeRodKey, new ItemStack(Material.BLAZE_ROD));
        blazeRod.addIngredient(1, Material.GOLD_INGOT);
        blazeRod.addIngredient(3, Material.GUNPOWDER);
        blazeRod.addIngredient(1, Material.CRIMSON_NYLIUM);
        blazeRod.addIngredient(1, Material.WARPED_NYLIUM);
        if (!recipeExists(blazeRodKey, blazeRod.getResult())) {
            try { Bukkit.addRecipe(blazeRod, true); count++; }
            catch (Exception e) { failedExceptions.add("blaze_rod (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey netherWartKey = new NamespacedKey(Specialization.getInstance(), "nether_wart");
        ShapedRecipe netherWart = new ShapedRecipe(netherWartKey, new ItemStack(Material.NETHER_WART));
        netherWart.shape(" E ", "DDD", " B ");
        netherWart.setIngredient('E', Material.BEETROOT);
        netherWart.setIngredient('D', Material.COARSE_DIRT);
        netherWart.setIngredient('B', Material.BLAZE_POWDER);
        if (!recipeExists(netherWartKey, netherWart.getResult())) {
            try { Bukkit.addRecipe(netherWart, true); count++; }
            catch (Exception e) { failedExceptions.add("nether_wart (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        return count;
    }

    public static void addUnobtainableRecipes(List<String> failedExceptions, int skippedDuplicateCount) {
        NamespacedKey catEggKey = new NamespacedKey(Specialization.getInstance(), "cat_spawn_egg");
        ShapedRecipe catEgg = new ShapedRecipe(catEggKey, new ItemStack(Material.CAT_SPAWN_EGG));
        catEgg.shape("FFF", " E ", "FDF");
        catEgg.setIngredient('F', Material.TROPICAL_FISH);
        catEgg.setIngredient('E', Material.EGG);
        catEgg.setIngredient('D', Material.DIAMOND);
        if (!recipeExists(catEggKey, catEgg.getResult())) {
            try { Bukkit.addRecipe(catEgg, true); }
            catch (Exception e) { failedExceptions.add("cat_spawn_egg (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey bellKey = new NamespacedKey(Specialization.getInstance(), "bell");
        ShapedRecipe bell = new ShapedRecipe(bellKey, new ItemStack(Material.BELL));
        bell.shape(" W ", "GGG", "GGG");
        bell.setIngredient('W', new RecipeChoice.MaterialChoice(Tag.PLANKS));
        bell.setIngredient('G', Material.GOLD_INGOT);
        if (!recipeExists(bellKey, bell.getResult())) {
            try { Bukkit.addRecipe(bell, true); }
            catch (Exception e) { failedExceptions.add("bell (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey cobwebKey = new NamespacedKey(Specialization.getInstance(), "cobweb");
        ShapedRecipe cobweb = new ShapedRecipe(cobwebKey, new ItemStack(Material.COBWEB));
        cobweb.shape("SSS", "SSS", "SSS");
        cobweb.setIngredient('S', Material.STRING);
        if (!recipeExists(cobwebKey, cobweb.getResult())) {
            try { Bukkit.addRecipe(cobweb, true); }
            catch (Exception e) { failedExceptions.add("cobweb (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey beaconKey = new NamespacedKey(Specialization.getInstance(), "beacon");
        ShapedRecipe beacon = new ShapedRecipe(beaconKey, new ItemStack(Material.BEACON));
        beacon.shape("GCG", "APA", "BBB");
        beacon.setIngredient('G', Material.GLASS);
        beacon.setIngredient('C', Material.CLOCK);
        beacon.setIngredient('A', Material.GOLDEN_APPLE);
        beacon.setIngredient('P', Material.COMPASS);
        beacon.setIngredient('B', Material.COPPER_BLOCK);
        if (!recipeExists(beaconKey, beacon.getResult())) {
            try { Bukkit.addRecipe(beacon, true); }
            catch (Exception e) { failedExceptions.add("beacon (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;
    }

    public static void addArmorTrims(List<String> failedExceptions, int skippedDuplicateCount) {
        NamespacedKey boltTrim = new NamespacedKey(Specialization.getInstance(), "bolt_trim");
        ShapedRecipe bolt = new ShapedRecipe(boltTrim, new ItemStack(Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE));
        bolt.shape("ABA", "BBB", "AAA");
        bolt.setIngredient('A', Material.LIGHT_BLUE_DYE);
        bolt.setIngredient('B', Material.COPPER_BLOCK);
        if (!recipeExists(boltTrim, bolt.getResult())) {
            try { Bukkit.addRecipe(bolt, true); }
            catch (Exception e) { failedExceptions.add("bolt_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey flowTrim = new NamespacedKey(Specialization.getInstance(), "flow_trim");
        ShapedRecipe flow = new ShapedRecipe(flowTrim, new ItemStack(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE));
        flow.shape("ABA", "BBA", "ABB");
        flow.setIngredient('A', Material.LIGHT_BLUE_DYE);
        flow.setIngredient('B', Material.LIGHT_BLUE_TERRACOTTA);
        if (!recipeExists(flowTrim, flow.getResult())) {
            try { Bukkit.addRecipe(flow, true); }
            catch (Exception e) { failedExceptions.add("flow_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey tideTrim = new NamespacedKey(Specialization.getInstance(), "tide_trim");
        ShapedRecipe tide = new ShapedRecipe(tideTrim, new ItemStack(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE));
        tide.shape("ABA", "ABA", "BAB");
        tide.setIngredient('A', Material.DEAD_BRAIN_CORAL_BLOCK);
        tide.setIngredient('B', Material.LIGHT_BLUE_TERRACOTTA);
        if (!recipeExists(tideTrim, tide.getResult())) {
            try { Bukkit.addRecipe(tide, true); }
            catch (Exception e) { failedExceptions.add("tide_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey silenceTrim = new NamespacedKey(Specialization.getInstance(), "silence_trim");
        ShapedRecipe silence = new ShapedRecipe(silenceTrim, new ItemStack(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE));
        silence.shape("ABC", "ABA", "CBA");
        silence.setIngredient('A', Material.DEEPSLATE);
        silence.setIngredient('B', Material.LIGHT_BLUE_DYE);
        silence.setIngredient('C', Material.SCULK);
        if (!recipeExists(silenceTrim, silence.getResult())) {
            try { Bukkit.addRecipe(silence, true); }
            catch (Exception e) { failedExceptions.add("silence_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wardTrim = new NamespacedKey(Specialization.getInstance(), "ward_trim");
        ShapedRecipe ward = new ShapedRecipe(wardTrim, new ItemStack(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE));
        ward.shape("ABA", "BAB", "BBB");
        ward.setIngredient('A', Material.LIGHT_BLUE_DYE);
        ward.setIngredient('B', Material.DEEPSLATE);
        if (!recipeExists(wardTrim, ward.getResult())) {
            try { Bukkit.addRecipe(ward, true); }
            catch (Exception e) { failedExceptions.add("ward_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey snoutTrim = new NamespacedKey(Specialization.getInstance(), "snout_trim");
        ShapedRecipe snout = new ShapedRecipe(snoutTrim, new ItemStack(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE));
        snout.shape("AAA", "BAB", "AAA");
        snout.setIngredient('A', Material.BLACKSTONE);
        snout.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(snoutTrim, snout.getResult())) {
            try { Bukkit.addRecipe(snout, true); }
            catch (Exception e) { failedExceptions.add("snout_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey raiserTrim = new NamespacedKey(Specialization.getInstance(), "raiser_trim");
        ShapedRecipe raiser = new ShapedRecipe(raiserTrim, new ItemStack(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE));
        raiser.shape("ABB", "BAB", "BBA");
        raiser.setIngredient('A', Material.LIGHT_BLUE_DYE);
        raiser.setIngredient('B', Material.TERRACOTTA);
        if (!recipeExists(raiserTrim, raiser.getResult())) {
            try { Bukkit.addRecipe(raiser, true); }
            catch (Exception e) { failedExceptions.add("raiser_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey shaperTrim = new NamespacedKey(Specialization.getInstance(), "shaper_trim");
        ShapedRecipe shaper = new ShapedRecipe(shaperTrim, new ItemStack(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE));
        shaper.shape("AAB", "BBB", "BAA");
        shaper.setIngredient('A', Material.TERRACOTTA);
        shaper.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(shaperTrim, shaper.getResult())) {
            try { Bukkit.addRecipe(shaper, true); }
            catch (Exception e) { failedExceptions.add("shaper_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey hostTrim = new NamespacedKey(Specialization.getInstance(), "host_trim");
        ShapedRecipe host = new ShapedRecipe(hostTrim, new ItemStack(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE));
        host.shape("ABA", "ABB", "ABA");
        host.setIngredient('A', Material.TERRACOTTA);
        host.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(hostTrim, host.getResult())) {
            try { Bukkit.addRecipe(host, true); }
            catch (Exception e) { failedExceptions.add("host_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wildTrim = new NamespacedKey(Specialization.getInstance(), "wild_trim");
        ShapedRecipe wild = new ShapedRecipe(wildTrim, new ItemStack(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE));
        wild.shape("AAA", "BBB", "AAA");
        wild.setIngredient('A', Material.MOSSY_COBBLESTONE);
        wild.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(wildTrim, wild.getResult())) {
            try { Bukkit.addRecipe(wild, true); }
            catch (Exception e) { failedExceptions.add("wild_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey coastTrim = new NamespacedKey(Specialization.getInstance(), "coast_trim");
        ShapedRecipe coast = new ShapedRecipe(coastTrim, new ItemStack(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE));
        coast.shape("ABA", "CAC", "CCC");
        coast.setIngredient('A', Material.LIGHT_BLUE_DYE);
        coast.setIngredient('B', Material.DEAD_TUBE_CORAL);
        coast.setIngredient('C', Material.COBBLED_DEEPSLATE);
        if (!recipeExists(coastTrim, coast.getResult())) {
            try { Bukkit.addRecipe(coast, true); }
            catch (Exception e) { failedExceptions.add("coast_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey duneTrim = new NamespacedKey(Specialization.getInstance(), "dune_trim");
        ShapedRecipe dune = new ShapedRecipe(duneTrim, new ItemStack(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE));
        dune.shape("AAA", "BBB", "AAA");
        dune.setIngredient('A', Material.LIGHT_BLUE_DYE);
        dune.setIngredient('B', Material.SANDSTONE);
        if (!recipeExists(duneTrim, dune.getResult())) {
            try { Bukkit.addRecipe(dune, true); }
            catch (Exception e) { failedExceptions.add("dune_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey eyeTrim = new NamespacedKey(Specialization.getInstance(), "eye_trim");
        ShapedRecipe eye = new ShapedRecipe(eyeTrim, new ItemStack(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE));
        eye.shape("ABA", "BAB", "ABA");
        eye.setIngredient('A', Material.SANDSTONE);
        eye.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(eyeTrim, eye.getResult())) {
            try { Bukkit.addRecipe(eye, true); }
            catch (Exception e) { failedExceptions.add("eye_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey sentryTrim = new NamespacedKey(Specialization.getInstance(), "sentry_trim");
        ShapedRecipe sentry = new ShapedRecipe(sentryTrim, new ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
        sentry.shape("ABA", "BAB", "ABA");
        sentry.setIngredient('A', Material.COBBLESTONE);
        sentry.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(sentryTrim, sentry.getResult())) {
            try { Bukkit.addRecipe(sentry, true); }
            catch (Exception e) { failedExceptions.add("sentry_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey vexTrim = new NamespacedKey(Specialization.getInstance(), "vex_trim");
        ShapedRecipe vex = new ShapedRecipe(vexTrim, new ItemStack(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE));
        vex.shape("ABB", "BAB", "BBA");
        vex.setIngredient('A', Material.LIGHT_BLUE_DYE);
        vex.setIngredient('B', Material.COBBLESTONE);
        if (!recipeExists(vexTrim, vex.getResult())) {
            try { Bukkit.addRecipe(vex, true); }
            catch (Exception e) { failedExceptions.add("vex_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wayfinderTrim = new NamespacedKey(Specialization.getInstance(), "wayfinder_trim");
        ShapedRecipe wayfinder = new ShapedRecipe(wayfinderTrim, new ItemStack(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE));
        wayfinder.shape("AAA", "ABA", "BAB");
        wayfinder.setIngredient('A', Material.TERRACOTTA);
        wayfinder.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(wayfinderTrim, wayfinder.getResult())) {
            try { Bukkit.addRecipe(wayfinder, true); }
            catch (Exception e) { failedExceptions.add("wayfinder_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey spireTrim = new NamespacedKey(Specialization.getInstance(), "spire_trim");
        ShapedRecipe spire = new ShapedRecipe(spireTrim, new ItemStack(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE));
        spire.shape("AAA", "ABA", "AAA");
        spire.setIngredient('A', Material.PINK_CONCRETE);
        spire.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(spireTrim, spire.getResult())) {
            try { Bukkit.addRecipe(spire, true); }
            catch (Exception e) { failedExceptions.add("spire_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;
    }
}

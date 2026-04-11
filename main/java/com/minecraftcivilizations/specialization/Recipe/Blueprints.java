package com.minecraftcivilizations.specialization.Recipe;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import minecraftcivilizations.com.minecraftCivilizationsCore.Ability.AbilityCastEvent;
import minecraftcivilizations.com.minecraftCivilizationsCore.Ability.CustomAbility;
import minecraftcivilizations.com.minecraftCivilizationsCore.Ability.CustomItemAbilityRegistry;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItem;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItemRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;

public class Blueprints {

    public static void init() {
        registerBlueprintItems();
        registerBlueprintRecipes(true);
    }

    private static void registerBlueprintItems() {
        Bukkit.getLogger().info("[Blueprints] Registering blueprint items...");
        int count = 0;

        for (ItemStack blueprintBase : getBluePrintBaseItems()) {
            try {
                CustomItem customItem = new CustomItem(blueprintBase.getType(), blueprintBase.displayName().append(Component.text(" Blueprint")));
                customItem.addLore(Specialization.getInstance(), List.of(
                        Component.empty(),
                        Component.text("Right click to learn this recipe!").color(NamedTextColor.WHITE)
                ));

                String blueprintName = PlainTextComponentSerializer.plainText().serialize(blueprintBase.displayName());
                NamespacedKey blueprintKey = new NamespacedKey(Specialization.getInstance(), sanitizeNamespacedKey(blueprintName + "_blueprint"));

                CustomItemAbilityRegistry.register(blueprintKey, makeBlueprintAbility(blueprintName, blueprintKey));
                customItem.addAbility(blueprintKey);
                CustomItemRegistry.register(blueprintKey, customItem);
                count++;
            } catch (Exception e) {
//                Bukkit.getLogger().warning("[Blueprints] Failed to register blueprint item - " + e.getMessage());
            }
        }

//        Bukkit.getLogger().info("[Blueprints] Registered " + count + " blueprint items");
    }

    public static void registerBlueprintRecipes(boolean reloading) {
        int successCount = 0;
        int skippedCount = 0;
        int missingCount = 0;
        List<String> failedExceptions = new ArrayList<>();

        for (ItemStack blueprintBase : getBluePrintBaseItems()) {
            String blueprintName = PlainTextComponentSerializer.plainText().serialize(blueprintBase.displayName());
            NamespacedKey blueprintKey = new NamespacedKey(Specialization.getInstance(), sanitizeNamespacedKey(blueprintName + "_blueprint"));

            CustomItem customItem = CustomItemRegistry.getItem(blueprintKey);
            if (customItem == null) {
                missingCount++;
                continue;
            }

            Recipe recipe = makeBlueprintRecipe(blueprintBase.getType(), customItem.getItem(), blueprintKey);

            // Count as skipped if null (missing ingredients)
            if (recipe == null) {
                skippedCount++;
                continue;
            }

            // Optional extra check for empty ingredient lists
            boolean hasIngredients = false;
            if (recipe instanceof ShapelessRecipe shapeless) {
                hasIngredients = !shapeless.getIngredientList().isEmpty();
            } else if (recipe instanceof ShapedRecipe shaped) {
                hasIngredients = !shaped.getIngredientMap().isEmpty();
            }

            if (!hasIngredients) {
                skippedCount++;
                continue;
            }


            // Check if a recipe with this key already exists (e.g. registered by MetalworkingRecipes)
            boolean alreadyExists = Bukkit.getRecipe(blueprintKey) != null;

            if (!alreadyExists) {
                try {
                    Bukkit.addRecipe(recipe, true);
                    successCount++;
                } catch (Exception e) {
                    failedExceptions.add(blueprintKey + " (" + e.getMessage() + ")");
                }
            } else {
                skippedCount++;
            }


        }

        Bukkit.getLogger().info("[Blueprints] Registration complete: " + successCount + " successful, "
                + skippedCount + " skipped (duplicates or empty), "
                + missingCount + " missing.");

        if (!failedExceptions.isEmpty()) {
            Bukkit.getLogger().warning("[Blueprints] Failed recipes due to exceptions (" + failedExceptions.size() + "):");
            failedExceptions.forEach(f -> Bukkit.getLogger().warning(" - " + f));
        }
    }


//    private static void startPeriodicBlueprintRefresh() {
//        Bukkit.getScheduler().runTaskTimerAsynchronously(Specialization.getInstance(), () -> {
//          Bukkit.getLogger().info("[Blueprints] Periodic blueprint recipe refresh triggered");
//           Debug.broadcast("recipes", "Periodic blueprint recipe refresh triggered", null, true);
//            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> Blueprints.registerBlueprintRecipes(true));
//        }, 1200L, 1200L);
//    }

    private static Recipe makeBlueprintRecipe(Material ingredient, ItemStack newItem, NamespacedKey key) {
        // Skip invalid or empty ingredients
        if (ingredient == null || ingredient == Material.AIR || newItem == null || newItem.getType() == Material.AIR) {
            return null;
        }

        ShapelessRecipe shapelessRecipe = new ShapelessRecipe(key, newItem);
        shapelessRecipe.addIngredient(Material.BLUE_DYE);
        shapelessRecipe.addIngredient(3, Material.PAPER);
        shapelessRecipe.addIngredient(new RecipeChoice.MaterialChoice(ingredient));
        return shapelessRecipe;
    }


    private static CustomAbility makeBlueprintAbility(String blueprintName, NamespacedKey recipe) {
        CustomAbility ability = new CustomAbility();
        ability.setAbilityFunction(player -> {
            CustomPlayer customPlayer = CoreUtil.getPlayer(player.getUniqueId());
            if (customPlayer.getSkillLevel(SkillType.BLACKSMITH) < 1) {
                player.sendRichMessage("<red>You must be an apprentice blacksmith to use this blueprint!</red>");
                return;
            }
            player.getInventory().getItemInMainHand().setAmount(0);
            player.discoverRecipe(recipe);
            customPlayer.getAdditionUnlockedRecipes().add(recipe);
            player.sendRichMessage("<green>Unlocked " + blueprintName + "</green>");
        });
        ability.setCastEvent(AbilityCastEvent.RIGHT_CLICK);
        ability.setName(blueprintName + " Blueprint");
        ability.setDescription(blueprintName + " Blueprint");

        return ability;
    }

    public static List<ItemStack> getBluePrintBaseItems() {
        String regex = SpecializationConfig.getBlueprintConfig().get("BLUEPRINT_ITEM_RECIPES", String.class);
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM).stream().filter(item -> item.key().value().matches(regex)).map(ItemType::createItemStack).toList();
    }


    private static String sanitizeNamespacedKey(String key) {
        return key.toLowerCase()
                .replaceAll("[^a-z0-9_.-/]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
    }

}

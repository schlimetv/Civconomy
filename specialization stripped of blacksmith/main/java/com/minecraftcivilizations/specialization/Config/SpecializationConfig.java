package com.minecraftcivilizations.specialization.Config;

import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import lombok.Getter;
import minecraftcivilizations.com.minecraftCivilizationsCore.Config.ConfigFile;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
public class SpecializationConfig {
    @Getter
    private static ConfigFile xpMonitorConfig;
    @Getter
    private static ConfigFile playerConfig;
    @Getter
    private static ConfigFile skillsConfig;
    @Getter
    private static ConfigFile blockHardnessConfig;
    @Getter
    private static ConfigFile skillRequirementsConfig;
    @Getter
    private static ConfigFile unlockedRecipesConfig;
    @Getter
    private static ConfigFile allRecipeBank;
    @Getter
    private static ConfigFile xpGainFromStonecuttingConfig;
    @Getter
    private static ConfigFile xpGainFromSmeltingConfig;
    @Getter
    private static ConfigFile xpGainFromCraftingConfig;
    @Getter
    private static ConfigFile xpGainFromBlastingConfig;
    @Getter
    private static ConfigFile xpGainFromSmokingConfig;
    @Getter
    private static ConfigFile xpGainFromBreakingConfig;
    @Getter
    private static ConfigFile xpGainFromPlacingConfig;
    @Getter
    private static ConfigFile xpGainFromEnchantingConfig;
    @Getter
    private static ConfigFile xpGainFromCartographyConfig;
    @Getter
    private static ConfigFile classSkillEffectsConfig;
    @Getter
    private static ConfigFile librarianConfig;
    @Getter
    private static ConfigFile farmerConfig;
    @Getter
    private static ConfigFile tameableConfig;
    @Getter
    private static ConfigFile canUseBlockConfig;
    @Getter
    private static ConfigFile xpGainFromRepairingConfig;
    @Getter
    private static ConfigFile combatConfig;
    @Getter
    private static ConfigFile chatConfig;
    @Getter
    private static ConfigFile blueprintConfig;
    @Getter
    private static ConfigFile mobConfig;
    @Getter
    private static ConfigFile mobDropsConfig;
    @Getter
    private static ConfigFile guardsmanConfig;
    @Getter
    private static ConfigFile berserkConfig;
    @Getter
    private static ConfigFile reinforcementConfig;
    @Getter
    private static ConfigFile hungerConfig;
    @Getter
    private static ConfigFile downedConfig;
    @Getter
    private static ConfigFile canMinerLvlBreakConfig;
    @Getter
    private static ConfigFile canFarmerBreakConfig;
    @Getter
    private static ConfigFile armorDamageReductionConfig;
    @Getter
    private static ConfigFile healthConfig;
    @Getter
    private static ConfigFile bedOwnershipConfig;
    @Getter
    private static ConfigFile serverConfig;
    @Getter
    private static ConfigFile instinctConfig;
    @Getter
    private static ConfigFile locatorBarConfig;

    private static List<EntityType> BREEDABLE =  List.of(EntityType.AXOLOTL, EntityType.CAMEL, EntityType.CAT, EntityType.CHICKEN, EntityType.COD, EntityType.COW, EntityType.DONKEY, EntityType.FOX, EntityType.FROG, EntityType.GOAT, EntityType.HOGLIN, EntityType.HORSE, EntityType.LLAMA, EntityType.MOOSHROOM, EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT, EntityType.PIG, EntityType.RABBIT, EntityType.SHEEP, EntityType.STRIDER, EntityType.TADPOLE, EntityType.TURTLE, EntityType.WOLF);
    public static final List<EntityType> TAMEABLE = List.of(EntityType.WOLF, EntityType.OCELOT, EntityType.CAT, EntityType.PARROT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA);


    public static void initialize() {
        playerConfig = new ConfigFile(Specialization.getInstance(), "playerConfig", null, fields -> {
            fields.add(new Pair<>("SPECIALIZATION_BONUS", 0.3));
            fields.add(new Pair<>("MULTI_CLASS_PENALTY", 0.15));
            fields.add(new Pair<>("LINEAR_DECAY_RATE", 0.02));
            fields.add(new Pair<>("CROSS_SKILL_PENALTY", 0.25));

        });

        locatorBarConfig = new ConfigFile(Specialization.getInstance(), "locatorBarConfig", null, fields -> {
            fields.add(new Pair<>("LOCATOR_BAR_ENABLED", true));
            fields.add(new Pair<>("DEFAULT_RECEIVE_RANGE", 0.0));
            fields.add(new Pair<>("DEFAULT_TRANSMIT_RANGE", 64.0));
            fields.add(new Pair<>("TEMPORARY_VISIBILITY_RANGE", 128.0));
            fields.add(new Pair<>("OBSERVER_RECEIVE_RANGE", 128.0));
        });

        reinforcementConfig = new ConfigFile(Specialization.getInstance(), "reinforcementConfig", null, fields -> {
            fields.add(new Pair<>("LIGHT_REINFORCEMENT_MULTIPLIER", 0.2D));
            fields.add(new Pair<>("HEAVY_REINFORCEMENT_MULTIPLIER", 0.1D));
            fields.add(new Pair<>("LIGHT_REINFORCEMENT_LEVEL", 1));
            fields.add(new Pair<>("HEAVY_REINFORCEMENT_LEVEL", 2));
            fields.add(new Pair<>("LIGHT_EXPLOSION_RESISTANCE", 0.75));
            fields.add(new Pair<>("HEAVY_EXPLOSION_RESISTANCE", 0.95));
            // XP awarded per reinforcement tier
            fields.add(new Pair<>("WOODEN_XP", 5.0D));
            fields.add(new Pair<>("LIGHT_XP", 15.0D));
            fields.add(new Pair<>("HEAVY_XP", 30.0D));
            // Decay times in world ticks (1 MC day = 24000 ticks)
            fields.add(new Pair<>("WOODEN_DECAY_TICKS", 48000L));   // 2 MC days
            fields.add(new Pair<>("LIGHT_DECAY_TICKS", 336000L));   // 14 MC days
            // Heavy does not decay (no config entry needed)
        });


        unlockedRecipesConfig = new ConfigFile(Specialization.getInstance(), "unlockedRecipesConfig", "The array of unlocked recipes, they don't need to repeat between levels, the ones for novice are unlocked for the next ones", fields -> {
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    fields.add(new Pair<>(skillType + "_" + skillLevel, new HashSet<NamespacedKey>()));
                }
            }
        });

        xpGainFromCraftingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromCrafting", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, 1D)));
                }
            }
        });

        xpGainFromStonecuttingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromStonecutting", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
                            if (stonecuttingRecipe.getResult().equals(ItemStack.of(inputMaterial))) {
                                fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "1")));
                            }
                        }
                    });
                }
            }
        });

        combatConfig = new ConfigFile(Specialization.getInstance(), "combatConfig", null, fields -> {
            fields.add(new Pair<>("CROSSBOW_BASE_VELOCITY", 1.2));
            fields.add(new Pair<>("CROSSBOW_BASE_PIERCING_VELOCITY", 1.1));
            fields.add(new Pair<>("CROSSBOW_BASE_MULTISHOT_VELOCITY", 1.3));
            fields.add(new Pair<>("CROSSBOW_BASE_QUICKCHARGE_VELOCITY", 1.15));
        });

        serverConfig = new ConfigFile(Specialization.getInstance(), "serverConfig", null, fields -> {
            fields.add(new Pair<>("SERVER_ANALYTIC","server_1"));
        });

        hungerConfig = new ConfigFile(Specialization.getInstance(), "hungerConfig", null, fields -> {
            fields.add(new Pair<>("SPRINTING_DRAIN", 2));
            fields.add(new Pair<>("WALKING_DRAIN", 0.5));
            fields.add(new Pair<>("CROUCHING_DRAIN", 0.2));
            fields.add(new Pair<>("SWIMMING_DRAIN", 4));
            fields.add(new Pair<>("IDLE_DRAIN", 0.1));
            fields.add(new Pair<>("DRAIN_INTERVAL_IN_TICKS", 100L));
            fields.add(new Pair<>("IDLE_CHECK_TIME_IN_TICKS", 100L));
            fields.add(new Pair<>("HUNGER_REDUCTION_ON_NON_UNIQUE_CONSECUTIVE_FOOD", 1));
        });

        mobConfig = new ConfigFile(Specialization.getInstance(), "mobConfig", null, fields -> {
            fields.add(new Pair<>("DAYTIME_MOB_DAMAGE_MULTIPLIER", 4.0));
            fields.add(new Pair<>("NIGHTTIME_MOB_DAMAGE_MULTIPLIER", 10.0));
            fields.add(new Pair<>("NIGHT_GUARDSMAN_MOB_DAMAGE_PERCENT_REDUCTION", 30));
            fields.add(new Pair<>("DAYTIME_SPEED_BUFF", .03));
            fields.add(new Pair<>("NIGHTTIME_SPEED_BUFF", .2));
            fields.add(new Pair<>("MOB_RULE_TARGET_RANGE", 48));

            fields.add(new Pair<>("BLOCK_BREAK_CHANCE_PERCENTAGE", 30));
            fields.add(new Pair<>("BLOCK_BREAK_IGNORE_LIST_REGEX", List.of()));
            fields.add(new Pair<>("VISUAL_BREAKING_INCREASE_PER_TICK_PERCENTAGE", 1f));
        });

        mobDropsConfig = new ConfigFile(Specialization.getInstance(), "mobDrops", null, fields -> {
            for(EntityType entityType : EntityType.values()) {
                if(entityType.isAlive()){
                    fields.add(new Pair<>(entityType, List.of(Material.AIR.getKey())));
                }
            }
        });

        xpGainFromRepairingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromRepairing", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR && inputMaterial.getMaxDurability() > 0) {
                    fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.LIBRARIAN, "1")));
                }
            }
        });

        guardsmanConfig = new ConfigFile(Specialization.getInstance(), "guardsmanConfig", null, fields -> {
            for(EntityType entityType : EntityType.values()) {
                fields.add(new Pair<>(entityType, 1D));
            }
            fields.add(new Pair<>("NON_GUARDSMAN_DAMAGE_REDUCTION", 0.25));
        });

        downedConfig = new ConfigFile(Specialization.getInstance(), "downedConfig", null, fields -> {
            for(PotionEffectType potionEffectType : Registry.EFFECT) {
                fields.add(new Pair<>(potionEffectType.getKey().getKey(), new Pair<>(1D, 0D)));
            }
            fields.add(new Pair<>("TIME_TO_DEATH_IN_TICKS", 2400));
            fields.add(new Pair<>("OFFSET_TO_GROUND", 1.9));
        });

        canFarmerBreakConfig = new ConfigFile(Specialization.getInstance(), "canFarmerBreakConfig", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    fields.add(new Pair<>(inputMaterial.toString(), SkillLevel.NOVICE));
                }
            }
        });

        canMinerLvlBreakConfig = new ConfigFile(Specialization.getInstance(), "canMinerLvlBreakConfig", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    fields.add(new Pair<>(inputMaterial.toString(), SkillLevel.NOVICE));
                }
            }
        });


        berserkConfig = new ConfigFile(Specialization.getInstance(), "berserkConfig", null, fields -> {
            for(PotionEffectType potionEffectType : Registry.MOB_EFFECT) {
                fields.add(new Pair<>(potionEffectType.getKey(), new PotionEffectData(0, 0)));
            }
        });

        xpGainFromBlastingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromBlasting", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof BlastingRecipe blastingRecipe) {
                            if (blastingRecipe.getResult().equals(ItemStack.of(inputMaterial))) {
                                fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "1")));
                            }
                        }
                    });
                }
            }
        });

        xpGainFromSmeltingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromSmelting", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof FurnaceRecipe furnaceRecipe) {
                            if (furnaceRecipe.getResult().equals(ItemStack.of(inputMaterial))) {
                                fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "1")));
                            }
                        }
                    });
                }
            }
        });

        xpGainFromSmokingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromSmoking", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof SmokingRecipe smokingRecipe) {
                            if (smokingRecipe.getResult().equals(ItemStack.of(inputMaterial))) {
                                fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "1")));
                            }
                        }
                    });
                }
            }
        });

        xpGainFromBreakingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromBreaking", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "0")));
                }
            }
        });

        xpGainFromPlacingConfig = new ConfigFile(Specialization.getInstance(), "xpGainFromPlacing", null, fields -> {
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    fields.add(new Pair<>(inputMaterial, new Pair<>(SkillType.FARMER, "0")));
                }
            }
        });
        canUseBlockConfig = new ConfigFile(Specialization.getInstance(), "canUseBlock", "use InventoryType's not blocks, full list here: https://jd.papermc.io/paper/1.21.8/org/bukkit/event/inventory/InventoryType.html", fields -> {
            fields.add(new Pair<>("default", List.of(InventoryType.CRAFTING, InventoryType.FURNACE)));
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    fields.add(new Pair<>(skillType + "_" + skillLevel, List.of()));
                }
            }
        });

        classSkillEffectsConfig = new ConfigFile(Specialization.getInstance(), "classSkillEffectsConfig", "use potion effect type, full list here: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/potion/PotionEffectType.html", fields -> {
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    fields.add(new Pair<>(skillType + "_" + skillLevel, List.of(new Pair<>(PotionEffectType.STRENGTH.getKey(), 0))));
                }
            }
        });

        allRecipeBank = new ConfigFile(Specialization.getInstance(), "allRecipeBank", null, fields -> {
            Set<NamespacedKey> allRecipes = new HashSet<>();

            Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                if (recipe instanceof Keyed keyed) {
                    allRecipes.add(keyed.getKey());
                }
            });

            fields.add(new Pair<>("ALL_RECIPES", allRecipes));
        });

        blockHardnessConfig = new ConfigFile(Specialization.getInstance(), "blockHardnessConfig", null, fields -> {
            for (Material material : Material.values()) {
                if (material.isBlock() && !material.isAir()) {
                    fields.add(new Pair<>(material, 1D));
                }
            }
        });

        armorDamageReductionConfig = new ConfigFile(Specialization.getInstance(), "armorDamageReductionConfig", "Flat percentage damage reduction against mobs for each armor piece. Values are percentages (0.1 = 10% reduction)", fields -> {
            // Base armor slot reductions (applied to all armor materials)
            fields.add(new Pair<>("HELMET_BASE_REDUCTION", 0.05));
            fields.add(new Pair<>("CHESTPLATE_BASE_REDUCTION", 0.15));
            fields.add(new Pair<>("LEGGINGS_BASE_REDUCTION", 0.10));
            fields.add(new Pair<>("BOOTS_BASE_REDUCTION", 0.05));
            
            // Material-specific multipliers (multiply base reduction)
            fields.add(new Pair<>("LEATHER_MULTIPLIER", 0.5));
            fields.add(new Pair<>("CHAINMAIL_MULTIPLIER", 0.75));
            fields.add(new Pair<>("IRON_MULTIPLIER", 1.0));
            fields.add(new Pair<>("DIAMOND_MULTIPLIER", 1.5));
            fields.add(new Pair<>("GOLDEN_MULTIPLIER", 0.8));
            fields.add(new Pair<>("NETHERITE_MULTIPLIER", 2.0));
            
            // Maximum total damage reduction cap (prevents invincibility)
            fields.add(new Pair<>("MAX_TOTAL_REDUCTION", 0.8));
            
            // Enable/disable the system
            fields.add(new Pair<>("ENABLED", true));
        });

        healthConfig = new ConfigFile(Specialization.getInstance(), "healthConfig", null, fields -> {
            fields.add(new Pair<>("MAX_HEALTH", 20D));
            fields.add(new Pair<>("DEATH_REDUCED_MAX_HEALTH", 8D));
            fields.add(new Pair<>("BLESSED_FOOD_HEALTH_RESTORE_AMOUNT", 2D));
            fields.add(new Pair<>("HEALTH_ENABLED", true));
        });

        bedOwnershipConfig = new ConfigFile(Specialization.getInstance(), "bedOwnershipConfig", null, fields -> {
            fields.add(new Pair<>("BED_OWNERSHIP_ENABLED", true));
            fields.add(new Pair<>("ALLOW_BED_SHARING", false));
            fields.add(new Pair<>("BED_OWNERSHIP_MESSAGE", "§cThis bed is already claimed by another player!"));
            fields.add(new Pair<>("BED_CLAIM_MESSAGE", "§aYou have claimed this bed as your spawn point!"));
            fields.add(new Pair<>("BED_UNCLAIM_MESSAGE", "§7Your previous bed has been unclaimed."));
            fields.add(new Pair<>("BED_RESPAWN_HUNGER_REDUCTION_ENABLED", true));
            fields.add(new Pair<>("BED_RESPAWN_HUNGER_DIVISOR", 3));
            fields.add(new Pair<>("BED_RESPAWN_MINIMUM_HUNGER", 1));
            fields.add(new Pair<>("BED_RESPAWN_SHOW_MESSAGE", true));
        });

        skillsConfig = new ConfigFile(Specialization.getInstance(), "skillsConfig", null, fields -> {
            for (SkillType skillType : SkillType.values()) {
                fields.add(new Pair<>(skillType + "_WORKSTATION", Material.COMPOSTER));
                fields.add(new Pair<>(skillType + "_DESCRIPTION", "Description"));
            }
        });

        skillRequirementsConfig = new ConfigFile(Specialization.getInstance(), "skillRequirementsConfig", "the number represents the percentage of total xp in this skill needed to level it up each level", fields -> {
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    fields.add(new Pair<>(skillType + "_" + skillLevel + "_REQUIREMENT", 0D));
                }
            }
        });

        librarianConfig = new ConfigFile(Specialization.getInstance(), "librarianConfig", null, fields -> {
            fields.add(new Pair<>("ENCHANTABLE_TOOL_REGEX", "^(?i)(?:(wooden|stone|iron|diamond|golden|netherite)_(?:(pickaxe|axe|shovel|sword|hoe))|(leather|chainmail|iron|diamond|golden|netherite)_(?:(helmet|chestplate|leggings|boots))|fishing_rod|shears|flint_and_steel|bow|crossbow|trident|mace|elytra|book|shield)"));
            fields.add(new Pair<>("BANNED_BLESS_ENCHANTS", List.of(Enchantment.MENDING.getKey())));
            fields.add(new Pair<>("BLESS_ITEM_LIBRARIAN_LEVEL", 2));
            fields.add(new Pair<>("BLESS_ITEM_XP_LEVEL_REQUIREMENT", 3));
            fields.add(new Pair<>("ITEM_LORE_LIBRARIAN_LEVEL", 3));
        });

        farmerConfig = new ConfigFile(Specialization.getInstance(), "farmerConfig", null, fields -> {
            for(EntityType animal : BREEDABLE) {
                fields.add(new Pair<>("FARMER_BREED_LEVEL_" + animal,  SkillLevel.JOURNEYMAN.getLevel()));
            }
            for(SkillLevel skillLevel : SkillLevel.values()) {
                fields.add(new Pair<>("FARMER_GET_DROPS_CHANCE_" + skillLevel, 0.5));
            }
            //push
        });

        tameableConfig = new ConfigFile(Specialization.getInstance(), "tamingConfig", null, fields -> {
            for(EntityType tameable : TAMEABLE) {
                fields.add(new Pair<>("TAME_" + tameable, new Pair<>(SkillType.FARMER, SkillLevel.NOVICE.getLevel())));
            }
        });

        blueprintConfig = new ConfigFile(Specialization.getInstance(), "librarianConfig", null, fields -> {
            fields.add(new Pair<>("BLUEPRINT_ITEM_RECIPES", "^(?i)(?:(wooden|stone|iron|diamond|golden|netherite)_(?:(pickaxe|axe|shovel|sword|hoe))|(leather|chainmail|iron|diamond|golden|netherite)_(?:(helmet|chestplate|leggings|boots))|fishing_rod|shears|flint_and_steel|bow|crossbow|trident|mace|elytra|book|shield)"));
        });

        chatConfig = new ConfigFile(Specialization.getInstance(), "chatConfig", null, fields -> {
            fields.add(new Pair<>("CHAT_RADIUS", 32.0));
            fields.add(new Pair<>("DEFAULT_FORMAT", "%s > %s"));
            fields.add(new Pair<>("ANNOUNCEMENT_FORMAT", "<aqua>[Announcement]<gray> %s"));
            fields.add(new Pair<>("ANNOUNCEMENT_PREFIX", "#"));
            // Join/quit locality (radius in blocks; format uses %s for player name)
            fields.add(new Pair<>("JOIN_QUIT_RADIUS", 150.0));
            fields.add(new Pair<>("JOIN_FORMAT", "<gray>[<green>+</green>] <white>%s</white> joined nearby."));
            fields.add(new Pair<>("QUIT_FORMAT", "<gray>[<red>-</red>] <white>%s</white> left nearby."));
            // Ship assembly locality — set patterns to substrings found in Saint's Ships messages
            fields.add(new Pair<>("ASSEMBLY_RADIUS", 150.0));
            fields.add(new Pair<>("ASSEMBLY_SUCCESS_PATTERN", ""));
            fields.add(new Pair<>("ASSEMBLY_FAIL_PATTERN", ""));
        });

        instinctConfig = new ConfigFile(Specialization.getInstance(), "instinctConfig", null, fields -> {
            fields.add(new Pair<>("INSTINCT_ENABLED", true));
            fields.add(new Pair<>("INSTINCT_DETECTION_RADIUS_LEVEL_1", 8.0));
            fields.add(new Pair<>("INSTINCT_DETECTION_RADIUS_LEVEL_2", 12.0));
            fields.add(new Pair<>("INSTINCT_DETECTION_RADIUS_LEVEL_3", 16.0));
            fields.add(new Pair<>("INSTINCT_GLOW_DURATION_TICKS", 300));
        });

        xpMonitorConfig = new ConfigFile(Specialization.getInstance(), "XpMonitorAlertThresholds", null, fields -> {
            // FARMER
            fields.add(new Pair<>("FARMER.threshold", 600.0));
            fields.add(new Pair<>("FARMER.cooldown-seconds", 30));

            // BUILDER
            fields.add(new Pair<>("BUILDER.threshold", 600.0));
            fields.add(new Pair<>("BUILDER.cooldown-seconds", 30));

            // MINER
            fields.add(new Pair<>("MINER.threshold", 600.0));
            fields.add(new Pair<>("MINER.cooldown-seconds", 30));

            // HEALER
            fields.add(new Pair<>("HEALER.threshold", 600.0));
            fields.add(new Pair<>("HEALER.cooldown-seconds", 30));

            // LIBRARIAN
            fields.add(new Pair<>("LIBRARIAN.threshold", 550.0));
            fields.add(new Pair<>("LIBRARIAN.cooldown-seconds", 30));

            // GUARDSMAN
            fields.add(new Pair<>("GUARDSMAN.threshold", 550.0));
            fields.add(new Pair<>("GUARDSMAN.cooldown-seconds", 30));

            // BLACKSMITH
            fields.add(new Pair<>("BLACKSMITH.threshold", 600.0));
            fields.add(new Pair<>("BLACKSMITH.cooldown-seconds", 30));
        });


    }//change

}

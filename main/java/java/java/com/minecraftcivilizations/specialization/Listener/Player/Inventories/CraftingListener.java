package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Classless;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.ItemUtils;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CraftingListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(CraftingListener.class.getName());
    private static final String PLUGIN_NAMESPACE = Specialization.getInstance().getName().toLowerCase();
    private final Plugin plugin;

    private static final Set<Material> COMPLEX_ITEMS = Arrays.stream(Material.values())
            .filter(material -> {
                String name = material.name();
                if (
                        (
                                name.startsWith("IRON_") || name.startsWith("GOLDEN_") ||
                                name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")
                        )
                                &&
                        (
                                name.endsWith("_PICKAXE") || name.endsWith("_AXE") ||
                                name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_SWORD")
                        )
                ) {
                    return true;
                }
                return (name.startsWith("CHAINMAIL_") || name.startsWith("IRON_") ||
                        name.startsWith("GOLDEN_") || name.startsWith("DIAMOND_") ||
                        name.startsWith("NETHERITE_")) &&
                        (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
                                name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"));
            })
            .collect(Collectors.collectingAndThen(
                    Collectors.toSet(),
                    set -> {
                        set.addAll(Set.of(
                                Material.ANVIL, Material.SMITHING_TABLE, Material.BLAST_FURNACE, Material.GRINDSTONE,
                                Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER, Material.DROPPER,
                                Material.OBSERVER, Material.HOPPER, Material.COMPARATOR, Material.REPEATER,
                                Material.DAYLIGHT_DETECTOR, Material.SCAFFOLDING, Material.JUKEBOX, Material.CAMPFIRE,
                                Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.LECTERN,
                                Material.BREWING_STAND, Material.GLISTERING_MELON_SLICE, Material.GOLDEN_CARROT, Material.GOLDEN_APPLE,
                                Material.BEACON, Material.ENDER_CHEST, Material.SHIELD, Material.CROSSBOW, Material.TNT, Material.TARGET,
                                Material.CAKE, Material.PUMPKIN_PIE, Material.RABBIT_STEW
                        ));
                        return Set.copyOf(set);
                    }
            ));

    public CraftingListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;

        if (!isCraftingActionValid(event)) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }

        ItemStack crafted = event.getCurrentItem();

        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        int craftedAmount = getCraftedAmount(event);

        if (COMPLEX_ITEMS.contains(crafted.getType())) {
            for(int i = 0; i < craftedAmount; i++) {
                customPlayer.getAnalyticPlayerData().incrementComplexItemsCrafted(crafted.getType().toString());
            }
            Debug.broadcast("analytics", player.getName() + " crafted complex item: " + crafted.getType() + " x" + craftedAmount);
        }

        Pair<SkillType, Double> xp_gain_pair = SpecializationConfig.getXpGainFromCraftingConfig()
                .get(crafted.getType(), new TypeToken<>() {});

        String amtstring = craftedAmount+"x ";
        if(craftedAmount==1)amtstring = "";




        int lvl = (int)Math.max((double)customPlayer.getSkillLevel(xp_gain_pair.firstValue()), (double)customPlayer.getSkillLevel(SkillType.BLACKSMITH)*1.5);
        if(lvl>5)lvl = 5;
        double skill_benefit = (5-((double)lvl)/1.5);
        double base_reduction = getFoodReduction(crafted.getType());
        // Reduction based on Skill Level and Amount Crafted
        double food_reduction_formula = base_reduction * (skill_benefit * craftedAmount);

        double divider = event.getRecipe().getResult().getAmount();

        int totalReduction = (int) Math.max(1.0, food_reduction_formula / divider); //Math.max(0, totalReduction - (int) (Math.random() * 3));

        // Paper costs exactly 2 hunger per recipe execution regardless of skill level
        if (crafted.getType() == Material.PAPER) {
            totalReduction = Math.max(1, 2 * (int)(craftedAmount / divider));
        }

        // Classless exemption: basic survival items cost no hunger when all skills are Novice (tier 0)
        boolean classlessExempt = Classless.isClassless(customPlayer) && Classless.isExemptItem(crafted.getType());
        if (classlessExempt) {
            totalReduction = 0;
        }

        int foodLevel = player.getFoodLevel();
        if(player.getGameMode()==GameMode.CREATIVE){
            foodLevel=220; //for testing etc
        }

        double anti_starvation_threshold = 2; //increase this to prevent causing a plyer to starve upon crafting

        if(!classlessExempt && foodLevel - totalReduction < anti_starvation_threshold){
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_CHORUS_FLOWER_GROW, 0.5f, 1.25f);


            String hungry_msg = "<red>You're too hungry to craft</red>";
            if(craftedAmount>1){
                hungry_msg = "<red>You're too hungry to craft that many</red>";
            }
            if(ThreadLocalRandom.current().nextDouble()<0.0125){
                // Fun Messages
                String item_name = ItemUtils.getFriendlyName(event.getRecipe().getResult().getType());
                switch(ThreadLocalRandom.current().nextInt(6)){
                    case 0:
                        hungry_msg = "<red>You're too craft to hungry</red>"; break;
                    case 1:
                        hungry_msg = "<red>Some food would be nice right about now</red>"; break;
                    case 2:
                        hungry_msg = "<red>"+item_name+" does sound nice, but so does food.</red>"; break;
                    case 3:
                        hungry_msg = "<red>You're hungry, go eat!</red>"; break;
                    case 4:
                        hungry_msg = "<red>You try to craft the "+ item_name+", but you're too hungry!</red>"; break;
                    case 5:
                        hungry_msg = "<red>"+item_name+" demands that you eat!</red>"; break;
                }
            }
            player.sendActionBar(MiniMessage.miniMessage().deserialize(hungry_msg));
            return;
        }


        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack testItem = crafted.clone();
            testItem.setAmount(craftedAmount * crafted.getAmount());
            if (!canFitInInventory(player, testItem)) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                return;
            }
        }

        Material mat = event.getCurrentItem().getType();
        String color = getItemNameFormat(mat);
        boolean rare = true;
        if(color==null){
            color = "gray";
            rare = false;
        }
        String colortag = "<"+color+">"+amtstring+mat.name()+"</"+color+">";

        Component debug_isolated = MiniMessage.miniMessage().deserialize("<gray>crafted</gray> "+colortag+" <red>🍖"+totalReduction+"</red>");
        Component debug_global = Component.text(player.getName()+" ").color(NamedTextColor.WHITE).append(debug_isolated);
        Component hover = MiniMessage.miniMessage().deserialize("<gray>🎬:"+event.getAction().name()+"\n"
                +"<green>Current Item: </green>"+event.getCurrentItem().getType().name()+"\n"
                +"<blue>Cursor Item: </blue>"+event.getCursor().getType().name()+"\n"
                +"<red>🍖 Type Base Reduction: </red>"+base_reduction+"\n"
                +"<red>🍖 Skill Benefit: </red>"+skill_benefit+"\n"
                +"<red>🍖 Food Level: </red>"+foodLevel+" <gold>🍖 Reduction:</gold> "+totalReduction);
        Debug.broadcast("craft", debug_global, hover);
        if(rare){
            Debug.broadcast("craftrare", debug_global, hover);
        }
        String isolated_debug_channel = "craft_"+player.getName().toLowerCase();
        if(Debug.isAnyoneListening(isolated_debug_channel, false)) {
            Debug.broadcast(isolated_debug_channel, debug_isolated
                    .append(Debug.formatLocationClickable(player.getLocation(), true)), hover);
        }
        SpecializationCraftItemEvent new_event = new SpecializationCraftItemEvent(event, player, craftedAmount, totalReduction, xp_gain_pair.firstValue(), lvl);
        Bukkit.getPluginManager().callEvent(new_event);

        // Skip vanilla XP for blueprint recipes (blueprint XP is handled in onBlueprintCraft)
        boolean isBlueprintRecipe = false;
        if (event.getRecipe() instanceof Keyed kr && kr.getKey().getNamespace().equals(PLUGIN_NAMESPACE)) {
            isBlueprintRecipe = kr.getKey().getKey().endsWith("_blueprint");
        }

        if (!isBlueprintRecipe && xp_gain_pair.firstValue() != null && xp_gain_pair.secondValue() != null) {
            double xpToGive = xp_gain_pair.secondValue() * craftedAmount;

            int finalReduction = classlessExempt ? 0 : Math.max(totalReduction, 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    if (finalReduction > 0) {
                        player.setFoodLevel(player.getFoodLevel() - finalReduction);
                    }
                    if(!new_event.isXpCancelled()) {
                        if (crafted.getType() == Material.PAPER) {
                            int paperExecs = (int)(craftedAmount / divider);
                            if (customPlayer.getSkillLevel(SkillType.LIBRARIAN) >= 1) {
                                customPlayer.addSkillXp(SkillType.LIBRARIAN, 6.0 * paperExecs);
                            } else {
                                customPlayer.addSkillXp(SkillType.HEALER, 6.0 * paperExecs);
                            }
                        } else {
                            customPlayer.addSkillXp(xp_gain_pair.firstValue(), xpToGive);
                        }
                    }
                }
            }, 1L);
        }

    }

    private double getFoodReduction(Material type) {
        switch (type) {
            case FERMENTED_SPIDER_EYE:
            case BEETROOT_SOUP:
                return 1.75;
            case STICK:
            case PUMPKIN_PIE:
            case MUSHROOM_STEW:
                return 0.35;
            case TORCH:
            case REDSTONE_TORCH:
            case SOUL_TORCH:
            case REDSTONE_LAMP:
            case BRICKS:
            case BRICK_SLAB:
            case BRICK_STAIRS:
            case BRICK_WALL:
            case BOOK:
            case BOOKSHELF:
            case DRIED_KELP_BLOCK:
            case BREAD: //Bread is a bit more expensive to craft due to it being lo lvl
            case CAKE:
                return 0.5;
            case COOKIE:
                return 0.125;
            case CRAFTING_TABLE:
                return 1.0;
            case FURNACE:
            case SMOKER:
            case BLAST_FURNACE:
            case CHEST:
            case BARREL:
            case ENCHANTING_TABLE:
            case ANVIL:
                return 1.25;
            case WRITABLE_BOOK:
            case FLINT_AND_STEEL:
            case BUCKET:
            case SHEARS:
                return 2.0;
            case CLAY:
            case BRICK:
            case PACKED_MUD:
            case SNOW_BLOCK:
            case GLASS_PANE:
                return 0.33;
            case SANDSTONE:
            case SANDSTONE_SLAB:
            case SANDSTONE_STAIRS:
            case SANDSTONE_WALL:
            case RESIN_BLOCK:
            case RESIN_BRICK:
            case RESIN_BRICK_SLAB:
            case RESIN_BRICK_STAIRS:
            case RESIN_BRICK_WALL:
            case RESIN_BRICKS:
            case RESIN_CLUMP:
            case CHISELED_RESIN_BRICKS:
                return 0.3;
            case TINTED_GLASS:
            case GLASS_BOTTLE:
                return 0.75;
        }
        String name = type.name();


        if(Tag.STAIRS.isTagged(type)
                || Tag.FENCES.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type)
                || Tag.SLABS.isTagged(type)
                || Tag.WALLS.isTagged(type)
                || Tag.BUTTONS.isTagged(type)
                || Tag.ALL_SIGNS.isTagged(type)
                || Tag.ALL_HANGING_SIGNS.isTagged(type)
                || Tag.TERRACOTTA.isTagged(type)
        ){
            return 0.5;
        }
        if (Tag.PLANKS.isTagged(type)){
            return 0.25;
        }
        if(Tag.TRAPDOORS.isTagged(type)
                || Tag.PRESSURE_PLATES.isTagged(type)
                || name.contains("_GLASS")){
            return 0.33;
        }

        if (name.equals("PAPER") || name.endsWith("_WOOL") || name.endsWith("_BANNER")) {
            return 0.5;
        }

        /**
         * Complex values for tools
         */
        double value = 1.0;
        if(name.contains("_HELMET") || name.contains("_BOOTS")){
            value += 0.5;
        }else if(name.contains("_LEGGINGS") || name.contains("_CHESTPLATE")){
            value += 1.5;
        }else if(name.contains("_AXE") || name.contains("_SWORD")){
            value += 1.0;
        }else if(name.contains("_PICKAXE") || name.contains("_SHOVEL") || name.contains("_HOE")){
            value += 1.0;
        }
        if(name.contains("WOODEN_")) {
            value *= 0.35;
        }else if(name.contains("LEATHER_")){
            value *= 0.5;
        }else if(name.contains("STONE_")){
            value *= 0.75;
        }else if(name.contains("IRON_")){
            value *= 1.25;
        }else if(name.contains("DIAMOND_")){
            value *= 2.0;
        }
        return value;
    }

    private String getItemNameFormat(Material type) {
        String color = null;
        if(type.name().contains("IRON_")){
            color = "green";
        }else if(type.name().contains("DIAMOND_")){
            color = "aqua";
        }else if(type.name().contains("NETHERITE_")) {
            color = "light_purple";
        }else{
            switch(type){
                case TNT:
                case RESPAWN_ANCHOR:
                case END_CRYSTAL:
                    color = "dark_red";
                    break;
                case BEACON:
                case ANVIL:
                case ENCHANTING_TABLE:
                    color = "yellow";
                    break;

            }
        }
        return color;
    }

    /**
     * Checks if the player's inventory has space for the given item stack
     */
    private boolean canFitInInventory(Player player, ItemStack item) {
        int amountToAdd = item.getAmount();
        int maxStackSize = item.getMaxStackSize();

        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (amountToAdd <= 0) break;

            if (invItem == null || invItem.getType().isAir()) {
                // Empty slot can fit a full stack
                amountToAdd -= maxStackSize;
            } else if (invItem.isSimilar(item)) {
                // Existing stack can fit more
                int spaceLeft = maxStackSize - invItem.getAmount();
                amountToAdd -= spaceLeft;
            }
        }

        return amountToAdd <= 0;
    }

    /**
     * Determines if the crafting action will actually consume ingredients
     * and produce items in the player's inventory.
     */
    private boolean isCraftingActionValid(CraftItemEvent event) {
        InventoryAction action = event.getAction();
        if(action==InventoryAction.MOVE_TO_OTHER_INVENTORY)return true;

        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME,
                 PLACE_ONE, SWAP_WITH_CURSOR, HOTBAR_SWAP, DROP_ALL_CURSOR, DROP_ALL_SLOT, DROP_ONE_CURSOR -> true;
            case DROP_ONE_SLOT -> (event.getCursor().getType().isAir());
                 default -> false;
        };
    }

    /**
     * Calculates how many items are actually being crafted based on the event action
     */
    private int getCraftedAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;
        if(event.getResult().equals(Event.Result.DENY)){
            return 0;
        }

        InventoryAction action = event.getAction();

        switch (action) {
            case PICKUP_HALF:
                return Math.max(1, result.getAmount() / 2);
            case PICKUP_SOME:
                ItemStack cursor = event.getCursor();
                if (cursor.isSimilar(result)) {
                    int maxStack = result.getMaxStackSize();
                    int canTake = maxStack - cursor.getAmount();
                    return Math.min(canTake, result.getAmount());
                }
                return result.getAmount();
            case MOVE_TO_OTHER_INVENTORY:
                return calculateBulkCraftAmount(event);
            case DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ALL_CURSOR, DROP_ONE_SLOT:
                if(!event.getWhoClicked().getItemOnCursor().getType().equals(Material.AIR)) return 0;
            default:
                return result.getAmount();
        }
    }

    /**
     * Calculates the actual number of crafting operations for bulk crafting (Shift+Click)
     */
    private int calculateBulkCraftAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;

        // Get the recipe and check ingredient availability
        Recipe recipe = event.getRecipe();
        if (recipe == null) return 0;

        // For bulk crafting, we need to determine how many times the recipe can be executed
        // based on available ingredients in the crafting matrix
        org.bukkit.inventory.CraftingInventory craftingInventory = event.getInventory();
        ItemStack[] matrix = craftingInventory.getMatrix();
        
        int maxCrafts = Integer.MAX_VALUE;
        
        // Check each ingredient slot to find the limiting factor
        for (ItemStack ingredient : matrix) {
            if (ingredient != null && ingredient.getAmount() > 0) {
                // Each crafting operation consumes 1 of this ingredient
                maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
            }
        }
        
        // If no ingredients found or unlimited, default to result amount divided by recipe yield
        if (maxCrafts == Integer.MAX_VALUE) {
            return result.getAmount();
        }
        
        return maxCrafts * event.getRecipe().getResult().getAmount();
    }/**
     * Returns the exact ItemStacks that will be added to the player's inventory
     * when doing a bulk craft (shift+click / MOVE_TO_OTHER_INVENTORY).
     * The returned stacks are clones (safe to mutate).
     */
    private List<ItemStack> getStacksAddedByBulkCraft(Player player, ItemStack result, int totalProduced) {
        List<ItemStack> added = new ArrayList<>();
        if (result == null || totalProduced <= 0) return added;

        PlayerInventory inv = player.getInventory();
        int maxStack = result.getMaxStackSize();
        int remaining = totalProduced;

        // First try to fill existing similar stacks
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) continue;
            if (!slot.isSimilar(result)) continue;

            int space = maxStack - slot.getAmount();
            if (space <= 0) continue;

            int toAdd = Math.min(space, remaining);
            ItemStack addedStack = result.clone();
            addedStack.setAmount(toAdd);
            added.add(addedStack);
            remaining -= toAdd;
        }

        // Then fill empty slots
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && !slot.getType().isAir()) continue;

            int toAdd = Math.min(maxStack, remaining);
            ItemStack addedStack = result.clone();
            addedStack.setAmount(toAdd);
            added.add(addedStack);
            remaining -= toAdd;
        }

        // remaining > 0 means not all produced items fit; those remain in grid.
        return added;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        if (event.getInventory().getViewers().isEmpty()) return;

        Player player = null;

        for (var viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player) {
                player = (Player) viewer;
                break;
            }
        }

        if (player == null) return;

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        if (!(recipe instanceof Keyed)) {
            return;
        }

        NamespacedKey recipeKey = ((Keyed) recipe).getKey();

        // ─── Block custom items from being used in vanilla recipes ───
        // Custom items share base materials with vanilla items (e.g. bronze → GOLD_INGOT)
        // and would be accepted by vanilla MaterialChoice recipes, allowing exploits
        // like converting bronze ingots/components into gold nuggets.
        // Exception: wrought_iron_ingot is allowed in non-gear/tool vanilla recipes
        // (e.g. bucket, cauldron, iron bars) since it is functionally equivalent to iron.
        if (!recipeKey.getNamespace().equals(PLUGIN_NAMESPACE)) {
            boolean hasNonWroughtIronCustomItem = false;
            boolean hasWroughtIronCustomItem = false;
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient == null || ingredient.getType().isAir()) continue;
                com.minecraftcivilizations.specialization.CustomItem.CustomItem custom =
                        CustomItemManager.getInstance().getCustomItem(ingredient);
                if (custom != null) {
                    if ("wrought_iron_ingot".equals(custom.getId())) {
                        hasWroughtIronCustomItem = true;
                    } else {
                        hasNonWroughtIronCustomItem = true;
                        break;
                    }
                }
            }
            if (hasNonWroughtIronCustomItem) {
                event.getInventory().setResult(null);
                return;
            }
            if (hasWroughtIronCustomItem && isGearOrTool(event.getInventory().getResult())) {
                event.getInventory().setResult(null);
                return;
            }
        }

        if (shouldBlockRecipe(player, recipeKey)) {
            LOGGER.info("Blocking recipe " + recipeKey + " for player " + player.getName() + " due to insufficient skill level");
            event.getInventory().setResult(null);
            player.undiscoverRecipe(recipeKey);
        } else {
            if (!player.hasDiscoveredRecipe(recipeKey)) {
                player.discoverRecipe(recipeKey);
                LOGGER.fine("Discovered recipe " + recipeKey + " for player " + player.getName() + " on-demand");
            }
        }
    }

    public static boolean shouldBlockRecipe(Player player, NamespacedKey recipeKey) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if(customPlayer.getAdditionUnlockedRecipes() != null && customPlayer.getAdditionUnlockedRecipes().contains(recipeKey)) return false;

        // Check if recipe is in any skill-specific unlocked recipes config.
        // A recipe may appear under multiple skills (e.g. crushed ore under both
        // BLACKSMITH_APPRENTICE and MINER_APPRENTICE). The player is allowed if
        // they meet ANY of the skill requirements (OR logic).
        boolean foundInAnyConfig = false;
        for (SkillType skillType : SkillType.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                String configKey = skillType + "_" + skillLevel;
                Set<NamespacedKey> skillRecipes = SpecializationConfig.getUnlockedRecipesConfig()
                        .get(configKey, new TypeToken<>() {
                        });

                if (skillRecipes != null && skillRecipes.contains(recipeKey)) {
                    // Player meets this requirement — allow immediately
                    if (customPlayer.getSkillLevel(skillType) >= skillLevel.ordinal()) {
                        return false;
                    }
                    foundInAnyConfig = true;
                }
            }
        }

        // If the recipe was found in at least one config but the player didn't
        // meet any of the requirements, block it.
        // If not found in any config, allow it (not skill-gated).
        return foundInAnyConfig;
    }

    private static boolean isGearOrTool(ItemStack result) {
        if (result == null) return false;
        String name = result.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || name.endsWith("_SWORD") || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE") || name.endsWith("_HOE")
                || name.endsWith("_SHOVEL");
    }

    // ─────────────────────────────────────────────────────────────
    //  Plate/plateset detection
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns true if the custom item ID is a plate or plate stack.
     * These items share base materials with vanilla ingots and must be
     * blocked from vanilla recipes to prevent crafting exploits.
     *
     * Matches: armor_plate_copper, armor_plate_iron, armor_plate_gold,
     *          armor_plate_bronze, armor_plate_chainmail,
     *          copper_armor_plateset, iron_armor_plateset, etc.
     */
    private static boolean isPlateOrPlateset(String customId) {
        return customId.startsWith("armor_plate") || customId.endsWith("_armor_plateset");
    }

    // ─────────────────────────────────────────────────────────────
    //  Blueprint crafting: preserve the component piece
    // ─────────────────────────────────────────────────────────────

    /**
     * Blueprint recipes (component + paper + dye → blueprint) should not
     * consume the component piece. Since ShapelessRecipe has no built-in
     * way to mark an ingredient as non-consumed, we intercept the craft
     * event and return the piece to the grid on the next tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlueprintCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) return;
        String namespace = keyed.getKey().getNamespace();
        String keyName = keyed.getKey().getKey();
        if (!namespace.equals(PLUGIN_NAMESPACE)) return;
        if (!keyName.endsWith("_blueprint")) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Find the component piece in the crafting matrix (the custom item, not paper/dye)
        ItemStack[] matrix = event.getInventory().getMatrix();
        ItemStack preserved = null;
        int pieceSlot = -1;
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) continue;
            if (CustomItemManager.getInstance().getCustomItem(item) != null) {
                // Clone the full stack as it exists BEFORE the craft consumes it
                preserved = item.clone();
                pieceSlot = i;
                break;
            }
        }
        if (preserved == null) return;

        final ItemStack pieceToReturn = preserved;
        final int slot = pieceSlot;
        final org.bukkit.inventory.CraftingInventory craftInv = event.getInventory();

        // Calculate how many crafts will happen (for the fallback case)
        final int craftCount;
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Shift-click: limited by the smallest stack in the matrix
            int min = Integer.MAX_VALUE;
            for (ItemStack m : matrix) {
                if (m != null && !m.getType().isAir()) {
                    min = Math.min(min, m.getAmount());
                }
            }
            craftCount = min == Integer.MAX_VALUE ? 1 : min;
        } else {
            craftCount = 1;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            // If the player still has the crafting grid open, restore the full stack
            if (player.getOpenInventory().getTopInventory() == craftInv) {
                // matrix index i → inventory slot i + 1 (slot 0 is the result)
                craftInv.setItem(slot + 1, pieceToReturn);
            } else {
                // Grid was closed between ticks — Bukkit already returned the
                // unconsumed remainder to the player. Only return the consumed portion.
                ItemStack consumed = pieceToReturn.clone();
                consumed.setAmount(craftCount);
                var leftover = player.getInventory().addItem(consumed);
                leftover.values().forEach(drop ->
                    player.getWorld().dropItemNaturally(player.getLocation(), drop));
            }
        });

        // ─── Blueprint XP: 3 LIBRARIAN for bronze, 5 LIBRARIAN for iron ───
        CustomPlayer bpPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (bpPlayer != null) {
            double bpXp = keyName.startsWith("iron_") ? 5.0 : keyName.startsWith("bronze_") ? 3.0 : 0.0;
            if (bpXp > 0) bpPlayer.addSkillXp(SkillType.LIBRARIAN, bpXp * craftCount);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Custom metalworking crafting XP
    // ─────────────────────────────────────────────────────────────

    /** XP amounts based on number of plates used in the recipe (2 XP per plate). */
    private static final java.util.Map<String, double[]> CUSTOM_CRAFT_XP = java.util.Map.ofEntries(
        // [blacksmithXp, minerXp]
        // Padded leather: 1 blacksmith
        java.util.Map.entry("padded_leather",          new double[]{1, 0}),
        // Leather armor from padded leather: 1xp per padded leather used
        java.util.Map.entry("padded_leather_helmet",    new double[]{5, 0}),
        java.util.Map.entry("padded_leather_chestplate",new double[]{8, 0}),
        java.util.Map.entry("padded_leather_leggings",  new double[]{7, 0}),
        java.util.Map.entry("padded_leather_boots",     new double[]{4, 0}),
        // Copper/bronze plates: 2 blacksmith
        java.util.Map.entry("armor_plate_copper",  new double[]{2, 0}),
        java.util.Map.entry("armor_plate_bronze",  new double[]{2, 0}),
        // Copper component recipes: 2xp per plate used
        java.util.Map.entry("copper_helm",         new double[]{10, 0}), // 5 plates
        java.util.Map.entry("copper_breastplate",  new double[]{16, 0}), // 8 plates
        java.util.Map.entry("copper_greaves",      new double[]{14, 0}), // 7 plates
        java.util.Map.entry("copper_sabaton",      new double[]{8, 0}),  // 4 plates
        java.util.Map.entry("copper_sword_head",   new double[]{4, 0}),  // 2 plates
        java.util.Map.entry("copper_axe_head",     new double[]{6, 0}),  // 3 plates
        java.util.Map.entry("copper_pickaxe_head", new double[]{6, 0}),  // 3 plates
        java.util.Map.entry("copper_hoe_head",     new double[]{4, 0}),  // 2 plates
        java.util.Map.entry("copper_shovel_head",  new double[]{2, 0}),  // 1 plate
        // Bronze component recipes: 2xp per plate used
        java.util.Map.entry("bronze_helm",         new double[]{10, 0}),
        java.util.Map.entry("bronze_breastplate",  new double[]{16, 0}),
        java.util.Map.entry("bronze_greaves",      new double[]{14, 0}),
        java.util.Map.entry("bronze_sabaton",      new double[]{8, 0}),
        java.util.Map.entry("bronze_sword_head",   new double[]{4, 0}),
        java.util.Map.entry("bronze_axe_head",     new double[]{6, 0}),
        java.util.Map.entry("bronze_pickaxe_head", new double[]{6, 0}),
        java.util.Map.entry("bronze_hoe_head",     new double[]{4, 0}),
        java.util.Map.entry("bronze_shovel_head",  new double[]{2, 0}),
        // Crushed ores + bronze blend: 5 miner XP (in addition to blacksmith)
        java.util.Map.entry("crushed_copper_ore",  new double[]{0, 5}),
        java.util.Map.entry("crushed_iron_ore",    new double[]{0, 5}),
        java.util.Map.entry("crushed_gold_ore",    new double[]{0, 5}),
        java.util.Map.entry("bronze_blend",        new double[]{0, 5})
    );

    /**
     * Grants custom metalworking XP based on recipe key.
     * Runs at MONITOR so it doesn't interfere with the main onCraft handler.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCustomMetalworkingCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed keyed)) return;
        if (!keyed.getKey().getNamespace().equals(PLUGIN_NAMESPACE)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String key = keyed.getKey().getKey();
        double[] xp = CUSTOM_CRAFT_XP.get(key);
        if (xp == null) return;

        CustomPlayer cp = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if (cp == null) return;

        int amount = getCraftedAmount(event);
        if (xp[0] > 0) cp.addSkillXp(SkillType.BLACKSMITH, xp[0] * amount);
        // Miner XP only if the player is at least Miner Apprentice (level 1)
        if (xp[1] > 0 && cp.getSkillLevel(SkillType.MINER) >= 1) cp.addSkillXp(SkillType.MINER, xp[1] * amount);
    }
}
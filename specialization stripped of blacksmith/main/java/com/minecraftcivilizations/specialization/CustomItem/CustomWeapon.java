package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Combat.CombatManager;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.awt.Color;

import static com.minecraftcivilizations.specialization.util.MathUtils.*;
import static net.md_5.bungee.api.ChatColor.*;

public class CustomWeapon extends CustomItem{

    public CustomWeapon(String id) {
        super(id);
    }

    @Override
    public void init() {
// remove existing sword recipess
//        for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
//            Recipe r = it.next();
//            if (r instanceof ShapedRecipe sr) {
//                ItemStack result = sr.getResult();
//                if (result.getType().name().contains("_SWORD")) {
//                    if(result.getType().equals(Material.NETHERITE_SWORD))continue;
//                    Bukkit.removeRecipe(sr.getKey());
//                }
//            }
//        }
//
//        // re-register sword recipes with custom items
//        Material[] swords = {
//                Material.WOODEN_SWORD, Material.STONE_SWORD,
//                Material.IRON_SWORD, Material.DIAMOND_SWORD,
//                Material.GOLDEN_SWORD
//        };
//
//        for (Material sword : swords) {
//            // assume createCustomItem(sword) returns an ItemStack
//            ItemStack custom = createItemStack(sword);
//
//            ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(Specialization.getInstance(), sword.name().toLowerCase()), custom);
//            recipe.shape(" I ", " I ", " S ");
//            RecipeChoice ingot = null;
//            switch(sword){
//                case WOODEN_SWORD:
//                    ingot = new RecipeChoice.MaterialChoice(
//                            Material.OAK_PLANKS,
//                            Material.ACACIA_PLANKS,
//                            Material.BIRCH_PLANKS,
//                            Material.SPRUCE_PLANKS,
//                            Material.JUNGLE_PLANKS,
//                            Material.DARK_OAK_PLANKS,
//                            Material.MANGROVE_PLANKS,
//                            Material.PALE_OAK_PLANKS,
//                            Material.BAMBOO_PLANKS
//                    );
//                    break;
//                case STONE_SWORD:
//                    ingot = new RecipeChoice.MaterialChoice(
//                            Material.STONE,
//                            Material.COBBLESTONE,
//                            Material.DEEPSLATE,
//                            Material.COBBLED_DEEPSLATE
//                    );
//                    break;
//                case IRON_SWORD:
//                    ingot = new RecipeChoice.MaterialChoice(Material.IRON_INGOT);
//                    break;
//                case GOLDEN_SWORD:
//                    ingot = new RecipeChoice.MaterialChoice(Material.GOLD_INGOT);
//                    break;
//                case DIAMOND_SWORD:
//                    ingot = new RecipeChoice.MaterialChoice(Material.DIAMOND);
//                    break;
//            }
//            if(ingot!=null) {
//                recipe.setIngredient('I', ingot);
//                recipe.setIngredient('S', Material.STICK);
//
//                Bukkit.addRecipe(recipe);
//            }
//        }
    }

    @Override
    public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
        if(player!=null){
            int lvl = CoreUtil.getPlayer(player.getUniqueId()).getSkillLevel(SkillType.BLACKSMITH);
            double craft_crit_chance = 0.0;

            boolean luck_enabled = player.hasPotionEffect(PotionEffectType.LUCK);

            switch(lvl){
                case 0:
                    craft_crit_chance = luck_enabled?0.01:0.0;
                    return;
                case 1:
                    craft_crit_chance = luck_enabled?0.025:0.01;
                    break;
                case 2:
                    craft_crit_chance = luck_enabled?0.05:0.025;
                    break;
                case 3:
                    craft_crit_chance = luck_enabled?0.75:0.05;
                    break;
                case 4:
                    craft_crit_chance = luck_enabled?0.125:0.75;
                    break;
                case 5:
                    craft_crit_chance = luck_enabled?0.175:0.125;
                    break;
            }

            if(craft_crit_chance > rollDouble()){ // Crit Bonus Rolled Successful, still chance of 0 though
                double crit_bonus = quantize((rollDouble()* 0.25 *((double)lvl+2)) + 0.25, 0.25);
                double crit_max = 2.0;


                if(crit_bonus >0.0){ // The weapon will have a crit
                    Sound craft_sound = Sound.BLOCK_SMITHING_TABLE_USE;
                    ChatColor c = BLUE;

                    // Determine Sword Type Modification
                    switch(itemStack.getType()){
                        case WOODEN_SWORD:
                            c = ChatColor.of(new Color(124, 62,44));
                            craft_sound = Sound.BLOCK_BAMBOO_WOOD_HANGING_SIGN_PLACE;
                            crit_max = 1.0;
                            break;
                        case STONE_SWORD:
                            c = DARK_GRAY;
                            craft_sound = Sound.UI_STONECUTTER_TAKE_RESULT;
                            crit_max = 1.5;
                            break;
                        case GOLDEN_SWORD:
                            c = GOLD;
                            crit_bonus *= 4;
                            crit_max = 5;
                            break;
                        case IRON_SWORD:
                            c = GRAY;
                            break;
                        case DIAMOND_SWORD:
                            c = AQUA;
                            break;
                    }

                    ChatColor crit_bonus_color = BLUE;
                    if(crit_bonus>=crit_max){
                        crit_bonus = Math.min(crit_max, crit_bonus);
                        crit_bonus_color = c;
                        player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.325f, 1.2f);
                        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.125f, 1.25f);
                        ItemStackUtils.setLoreLine(meta, 2, DARK_GRAY+"Crafted by "+GRAY+player.getName());
                        player.sendMessage(c+"You've crafted a perfect Masterwork Sword!");
                    }else if (crit_bonus>=1.0){
                        player.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.125f, 1.5f);
                    }
                    
                    ItemStackUtils.setLoreLine(meta, 0, crit_bonus_color+"+"+crit_bonus+" Opening Crit Bonus");
                    meta.getPersistentDataContainer().set(CombatManager.CRIT_BONUS_KEY, PersistentDataType.DOUBLE, crit_bonus);
                    player.playSound(player, craft_sound, 0.25f, 1.0f);
                }
            }
//            if(luck_enabled){
//                PotionEffect luck = player.getPotionEffect(PotionEffectType.LUCK);
//                PotionEffect updated = new PotionEffect(luck.getType(), Math.max(1, luck.getDuration() - ( 20 * luck_seconds_to_remove)), luck.getAmplifier(), luck.isAmbient(), luck.hasParticles(), luck.hasIcon());
////                        entity.addPotionEffect(updated);
//                player.removePotionEffect(PotionEffectType.LUCK);
//                player.addPotionEffect(luck);
//                Debug.broadcast("customitem", "luck removed");
//            }
        }
        itemStack.setItemMeta(meta);
    }

    @Override
    public void onItemSwitchTo(PlayerItemHeldEvent event, ItemStack oldItem, ItemStack newItem) {
        Player player = event.getPlayer();
        int lvl = CoreUtil.getPlayer(player).getSkillLevel(SkillType.GUARDSMAN);
        if(isOnCooldown(player))return;
        PlayerUtil playerUtil = PlayerUtil.getPlayerUtil(player);

        boolean metal = false;
        boolean scrap = false;
        float volume = 0.5f;
        int lvl_req = 0;

        Sound sound = null;
        switch(newItem.getType()){
            case WOODEN_SWORD:
                sound = Sound.ITEM_AXE_STRIP;
                volume = 0.25f;
                break;
            case STONE_SWORD:
                sound = Sound.BLOCK_DRIPSTONE_BLOCK_BREAK;
                volume = 0.6f;
                scrap = true;
                break;
            case GOLDEN_SWORD:
                sound = Sound.ITEM_ARMOR_EQUIP_GOLD;
                volume = 0.25f;
                metal = true;
                break;
            case IRON_SWORD:
                sound = Sound.ITEM_ARMOR_EQUIP_IRON;
                metal = true;
                break;
            case DIAMOND_SWORD:
                sound = Sound.ITEM_ARMOR_EQUIP_DIAMOND;
                metal = true;
                break;
            case NETHERITE_SWORD:
                sound = Sound.ITEM_ARMOR_EQUIP_NETHERITE;
                metal = true;
                scrap = true;
                volume = 0.75f;
                break;
        }

        if(sound!=null){
            player.getWorld().playSound(player.getLocation(), sound, SoundCategory.PLAYERS,volume, random(0.9f,1.05f));
            if (scrap) {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_AXE_SCRAPE, SoundCategory.PLAYERS, volume*1.2f, random(1.2f, 1.35f));
            }
//            if(lvl>=lvl_req) {
            if (metal) {
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, volume*0.35f, random(1.19f, 1.2f));


                if(lvl>=4) {
                    if (!playerUtil.isOnCooldown("unsheathe_sound")) {
                        player.getWorld().playSound(player.getLocation(), "unsheathe", 0.35f, random(0.98f, 1.05f));
                    }
                    playerUtil.setCooldown("unsheathe_sound", 400);
                }
            }
            applyCooldown(player, 10);
        }
        if(lvl>=1){
            if (!playerUtil.isOnCooldown("guardsman_feeling")) {
                String msg = null;
                switch(lvl){
                    case 1: //Apprentice
                        msg = "<green>You hold the sword awkwardly, but it feels right</green>";
                        break;
                    case 2: //Journeyman
                        msg = "<green>You hold the sword with determination</green>";
                        break;
                    case 3: //Expert
                        msg = "<green>The sword feels like an extension of you</green>";
                        break;
                    case 4: //Master
                        msg = "<green>The sword is yours to command</green>";
                        break;
                    case 5: //Grandmaster
                        msg = "<green>You are one with the sword</green>";
                        break;
                }

                if(msg!=null)
                    player.sendActionBar(MiniMessage.miniMessage().deserialize(msg));
            }
            // TODO add level up listener for guardsman to reset this cooldown
            playerUtil.setCooldown("guardsman_feeling", 1200*5);
        }
    }

    @Override
    public void onItemSwitchAway(PlayerItemHeldEvent event, ItemStack oldItem, ItemStack newItem) {
        CustomItem ci = CustomItemManager.getInstance().getCustomItem(newItem);
//        Debug.broadcast("customitem", "old item: "+ci.getId());
        if (ci != this || oldItem.getType()!=newItem.getType()) {
            Player player = event.getPlayer();
            if(isOnCooldown(player))return;
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_HORSE_ARMOR_UNEQUIP, SoundCategory.PLAYERS, 0.25f, random(1.4f, 1.5f));
        }
    }

}

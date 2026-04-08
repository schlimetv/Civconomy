package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Combat.PVPManager;
import com.minecraftcivilizations.specialization.Listener.Player.ReviveListener;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


/**
 * @author jfrogy, alectriciti
 */
public class Bandage extends CustomItem {
    private static final NamespacedKey IS_DOWNED = new NamespacedKey(Specialization.getInstance(), "is_downed");
    private final ReviveListener reviveListener;
    NamespacedKey RECIPE_KEY = new NamespacedKey(Specialization.getInstance(), "bandage_recipe");

    public Bandage(String id, String displayName) {
        super(id, displayName, org.bukkit.Material.PAPER, true);
        this.reviveListener = Specialization.getInstance().reviveListener;
    }

    /**
     * Called when loading/reloading
     */
    public void init() {
//        Bukkit.getRecipe()
        if (Bukkit.getRecipe(RECIPE_KEY) == null) {
        ShapelessRecipe bandage_recipe = new ShapelessRecipe(RECIPE_KEY, createItemStack(1));
        bandage_recipe.addIngredient(8, Material.PAPER);
        bandage_recipe.addIngredient(Material.SUGAR_CANE);
        Bukkit.addRecipe(bandage_recipe, true);
        }

//            Bukkit.removeRecipe(RECIPE_KEY);

    }


    @Override
    public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player_who_crafted) {
        meta.setEnchantmentGlintOverride(true);
        meta.lore(java.util.List.of(
                Component.text("As a Healer Shift + Right Click to heal yourself.").color(NamedTextColor.BLUE),
                Component.text("Right Click a player to heal them or revive.").color(NamedTextColor.BLUE),
                Component.empty(),
                Component.text("Amount Healed and XP gained scale with Healer level.").color(NamedTextColor.GRAY),
                Component.text("Crafted by " + (player_who_crafted != null ? player_who_crafted.getName() : "nobody")).color(NamedTextColor.GRAY)
        ));
        itemStack.setItemMeta(meta);
    }

    @Override
    public void onInteractEntity(PlayerInteractEntityEvent event, ItemStack itemStack) {
        if (itemStack == null) return;

        Player healer = event.getPlayer();
        CustomPlayer cHealer = CoreUtil.getPlayer(healer.getUniqueId());
        int lvl = cHealer.getSkillLevel(SkillType.HEALER);
        if (lvl == 0)
        {
            PlayerUtil.message(healer,"You need to be a healer to use a bandage", 5);
            return;
        }

        Entity clicked = event.getRightClicked();

        // --- PLAYER TARGET ---
        if (clicked instanceof Player pTarget) {

            Byte downed = pTarget.getPersistentDataContainer()
                    .get(IS_DOWNED, PersistentDataType.BYTE);

            if (downed != null && downed == 1) {

                if (isOnCooldown(healer)) return;
                applyHeal(healer, pTarget, itemStack);

                return;
            }

            if (isOnCooldown(healer)) return;
            applyHeal(healer, pTarget, itemStack);
            return;
        }

        // --- PASSIVE MOB TARGET ---
        if (clicked instanceof Mob mob) {
            if (mob.getSpawnCategory() == SpawnCategory.ANIMAL) {
                if (isOnCooldown(healer)) return;

                applyHeal(healer, mob, itemStack);
            }
        }

        // all other entity types ignored
    }


    // Handles self-heal if sneak + right click air/block
    @Override
    public void onInteract(PlayerInteractEvent event, ItemStack itemStack) {
        if (itemStack == null) return;

        Player healer = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (!healer.isSneaking()) return; // only handle sneak self-heal here

        if (isOnCooldown(healer)) return;

        applyHeal(healer, healer, itemStack);
    }


    private void applyHeal(Player healer, LivingEntity target, ItemStack bandage) {


        Debug.broadcast("customitem", "<green>applying heal");
        CustomPlayer cHealer = CoreUtil.getPlayer(healer.getUniqueId());
        int lvl = cHealer.getSkillLevel(SkillType.HEALER);

        if (lvl <= 0){
            PlayerUtil.message(healer,"You need to be a healer to use a bandage", 5);
            return;
        }
        //reviving player
        if (target instanceof Player pTarget) {
            Byte downed = pTarget.getPersistentDataContainer().get(
                    new NamespacedKey(Specialization.getInstance(), "is_downed"),
                    PersistentDataType.BYTE
            );

            boolean isDowned = downed != null && downed == 1;
            if (isDowned) {
                Entity vehicle = pTarget.getVehicle();
                if (!(vehicle instanceof Sheep) && !(vehicle instanceof Player)) {
                    reviveListener.startRevive(healer, pTarget, reviveListener.createReviveInventory(pTarget));
                    applyCooldown(healer, 500);
//                        healer.sendMessage("revive started");
                }
            }
//            pTarget.getPersistentDataContainer().set(new NamespacedKey(Specialization.getInstance(), "is_downed"), PersistentDataType.BYTE, (byte) 0);
        }

        double current_health = target.getHealth();
        double max_health = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (current_health >= max_health) {
            PlayerUtil.message(healer,target.getName()+" already has full health", 10);
            return;
        }

        if (healer.getFoodLevel() < 3) {
            PlayerUtil.message(healer,"You're too hungry to heal");
            return;
        }




        PVPManager pvpManager = Specialization.getInstance().getPvpManager();
        boolean healer_in_combat = pvpManager.isInCombat(healer);
        boolean self_heal = target.equals(healer);

        double base_heal = 4.0;
        if(healer_in_combat){
            base_heal --;
        }
        if(self_heal){
            base_heal --;
        }

        int level = Math.min(lvl, 5);
        // heal: 4 at level 1, 10 at level 5
        double heal_amount = base_heal + (level - 1) * 1.5;
        int xp = 15 + (int) ((level - 1) * (35.0 / 4.0));
        double new_health = Math.min(current_health + heal_amount, max_health);

        target.setHealth(new_health);
        healer.setFoodLevel(healer.getFoodLevel() - 3);

        //----- cooldowns-----//

        //healing themselves
        if (self_heal) {
            applyCooldown(healer, 500);
        } else {
            //healing another player
            applyCooldown(healer, 150);
        }
        //healing a friendly mob
        if (!(target instanceof Enemy)) {
            cHealer.addSkillXp(SkillType.HEALER, xp);
        }

        // Heart particles
        int particleCount = (int) Math.ceil(heal_amount / 2.0);
        World w = target.getWorld();
        w.spawnParticle(Particle.HEART, target.getLocation().add(0, 0.75, 0),
                particleCount, 0.3, 1, 0.3, 0.5);
        w.playSound(target.getEyeLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1);
        w.playSound(target.getEyeLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.85f, 1.2f);

        // Consume one Bandage
        bandage.setAmount(bandage.getAmount() - 1);
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp = new_health;

        // round down to nearest 0.5 hearts
        double roundedHP = Math.floor(hp * 2) / 2.0;

        // number of full hearts
        int fullHearts = (int) roundedHP / 2;

        // check for half heart
        boolean hasHalfHeart = (roundedHP % 2) >= 0.5;

        // total hearts
        int totalHearts = (int) Math.ceil(maxHealth / 2.0);

        // gray hearts
        int grayHearts = totalHearts - fullHearts - (hasHalfHeart ? 1 : 0);
        if (grayHearts < 0) grayHearts = 0;

        // build string
        StringBuilder sb = new StringBuilder();
        sb.append("<dark_red>❤</dark_red>".repeat(fullHearts));
        if (hasHalfHeart) sb.append("<#804040>❤</#804040>");
        sb.append("<dark_gray>❤</dark_gray>".repeat(grayHearts));

        PlayerUtil.message(healer, MiniMessage.miniMessage().deserialize(sb.toString()));






        //debug msg
        Debug.broadcast("customitem_" + healer.getName().toLowerCase(), "message of " + healer.getName());
        Debug.message(healer, "customitem", ("<green>Used " + getDisplayName() + " on " + target.getName() + " for " + heal_amount + " HP. " + sb));
    }


}

package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Listener.Player.Inventories.SpecializationCraftItemEvent;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BlessedFood extends CustomItem {

    private final List<Material> craftingIngredients; // null or empty = not craftable
    protected PotionEffectType effectType;
    protected int durationTicks;
    protected int amplifier;
    protected int xpReward;

    public BlessedFood(String id, String displayName, Material material,
                       PotionEffectType effectType, int durationTicks,
                       int amplifier, int xpReward, List<Material> craftingIngredients) {

        super(id, displayName, material, id, true);
        this.effectType = effectType;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.xpReward = xpReward;
        this.craftingIngredients = craftingIngredients;
    }

    @Override
    public void init() {
        // Register shapeless recipe if ingredients exist
        if (craftingIngredients != null && !craftingIngredients.isEmpty()) {
            NamespacedKey key = new NamespacedKey(Specialization.getInstance(), getId());
            ShapelessRecipe recipe = new ShapelessRecipe(key, createItemStack());
            craftingIngredients.forEach(recipe::addIngredient);
            Bukkit.addRecipe(recipe);
        }
    }

//    @Override
//    public boolean canPlayerCraft(Player player) {
//        CustomPlayer cPlayer = CoreUtil.getPlayer(player.getUniqueId());
//        return cPlayer.getSkillLevel(SkillType.HEALER) > 0;
//    }

    @Override
    public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
        meta.setLore(List.of(
                "§7A divine meal blessed by celestial hands.",
                "§fRestores both body and spirit."
        ));
        meta.setEnchantmentGlintOverride(true);
        itemStack.setItemMeta(meta);
    }

    @Override
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();

        if (isEnabled()) {
            if (isOnCooldown(player)) {
                event.setCancelled(true);
                player.sendMessage("§cYou're eating too quick fatty!");
                return;
            }

            player.addPotionEffect(new PotionEffect(effectType, durationTicks, amplifier));
            player.playSound(player, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.1f);

//            applyCooldown();
            //.setCooldownSeconds(xpReward*4);

            ItemStack itemstack = event.getItem();

//            event.setCancelled(true);

//            applyManualCooldownOverride(itemstack, 200);

//            applyCooldown();
//            itemstack.setAmount(itemstack.getAmount()-1);
            player.setCooldown(itemstack.getType(), 100);
//            event.setItem(itemstack);

//            event.setReplacement(null);
        }
    }

    @Override
    public void onCustomCraft(SpecializationCraftItemEvent event, ItemStack itemstack) {
        Player player = event.getPlayer();
        CustomPlayer.getCustomPlayer(player).addSkillXp(SkillType.HEALER, xpReward);
        event.setCancelXp(true); //we will handle custom xp ourselves
    }



}

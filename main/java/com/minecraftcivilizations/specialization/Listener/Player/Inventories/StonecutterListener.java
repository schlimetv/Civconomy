package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Logger;

public class StonecutterListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(StonecutterListener.class.getName());
    private final Plugin plugin;

    public StonecutterListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStonecut(InventoryClickEvent event) {
        if (event.getView().getType() != InventoryType.STONECUTTER) return;
        if (event.getSlot() != 1) return; // only result slot
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryAction action = event.getAction();
        if (!isValidAction(action)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        InventoryView view = event.getView();
        ItemStack inputBefore = cloneSafe(view.getItem(0)); // stonecutter input slot before craft

        int amount = getStonecutAmount(event);

        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromStonecuttingConfig()
                .get(result.getType(), new TypeToken<>() {});
        if (pair == null || pair.firstValue() == null || pair.secondValue() == null) return;

        double xpToGive = pair.secondValue() * amount;

        // One tick later, check if craft actually happened
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack inputAfter = cloneSafe(view.getItem(0));

            // Validate: input item count decreased => craft succeeded
            boolean craftOccurred = didConsumeIngredient(inputBefore, inputAfter);

            if (!craftOccurred) {
                LOGGER.fine("Blocked XP grant: no actual stonecutting detected for " + player.getName());
                return;
            }

            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                    .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
            customPlayer.addSkillXp(pair.firstValue(), xpToGive);
            LOGGER.fine("Gave " + xpToGive + " XP to " + player.getName()
                    + " for stonecutting " + amount + "x " + result.getType());
        }, 1L);
    }

    private boolean isValidAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                 MOVE_TO_OTHER_INVENTORY, DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, HOTBAR_SWAP -> true;
            default -> false;
        };
    }

    private int getStonecutAmount(InventoryClickEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;
        InventoryAction action = event.getAction();

        return switch (action) {
            case PICKUP_HALF -> Math.max(1, result.getAmount() / 2);
            case PICKUP_SOME -> {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.isSimilar(result)) {
                    int maxStack = result.getMaxStackSize();
                    int canTake = maxStack - cursor.getAmount();
                    yield Math.min(canTake, result.getAmount());
                }
                yield result.getAmount();
            }
            case MOVE_TO_OTHER_INVENTORY -> event.getView().getItem(0) != null
                    ? result.getAmount() * Objects.requireNonNull(event.getView().getItem(0)).getAmount()
                    : result.getAmount();
            default -> result.getAmount();
        };
    }

    private ItemStack cloneSafe(ItemStack item) {
        return (item == null) ? null : item.clone();
    }

    private boolean didConsumeIngredient(ItemStack before, ItemStack after) {
        if (before == null || before.getType() == Material.AIR) return false; // nothing to consume
        if (after == null || after.getType() == Material.AIR) return true; // input fully used
        if (!before.isSimilar(after)) return true; // input replaced (new recipe/material)
        return after.getAmount() < before.getAmount(); // input count went down
    }
}

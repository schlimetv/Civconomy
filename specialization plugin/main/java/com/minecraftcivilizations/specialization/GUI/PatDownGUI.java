package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUI;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUIItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class PatDownGUI extends GUI {

    private final Player target;
    private final Player inspector;

    public PatDownGUI(Player inspector, Player target) {
        super(Component.text("Pat Down: " + target.getName()).color(NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false), 54);
        this.target = target;
        this.inspector = inspector;
    }
    private boolean openingThisTick = false;

    @Override
    public void open(Player player) {



        this.getItems().clear();

        PlayerInventory targetInv = target.getInventory();


        for (int i = 0; i < 9; i++) {
            ItemStack item = targetInv.getItem(i);
            if (item != null) {
                this.getItems().put(i, new GUIItem(item.clone(), null));
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack item = targetInv.getItem(i);
            if (item != null) {
                this.getItems().put(i, new GUIItem(item.clone(), null));
            }
        }


        ItemStack helmet = targetInv.getHelmet();
        if (helmet != null) {
            this.getItems().put(36, new GUIItem(helmet.clone(), null));
        } else {
            this.getItems().put(36, new GUIItem(createPlaceholder("Helmet Slot"), null));
        }

        ItemStack chestplate = targetInv.getChestplate();
        if (chestplate != null) {
            this.getItems().put(37, new GUIItem(chestplate.clone(), null));
        } else {
            this.getItems().put(37, new GUIItem(createPlaceholder("Chestplate Slot"), null));
        }

        ItemStack leggings = targetInv.getLeggings();
        if (leggings != null) {
            this.getItems().put(38, new GUIItem(leggings.clone(), null));
        } else {
            this.getItems().put(38, new GUIItem(createPlaceholder("Leggings Slot"), null));
        }

        ItemStack boots = targetInv.getBoots();
        if (boots != null) {
            this.getItems().put(39, new GUIItem(boots.clone(), null));
        } else {
            this.getItems().put(39, new GUIItem(createPlaceholder("Boots Slot"), null));
        }

        // Offhand
        ItemStack offhand = targetInv.getItemInOffHand();
        this.getItems().put(40, new GUIItem(offhand.clone(), null));

        ItemStack barrier = createPlaceholder("Empty");
        for (int i = 41; i < 54; i++) {
            this.getItems().put(i, new GUIItem(barrier, null));
        }

        // Send messages
        PlayerUtil.message(inspector, "Inspecting " + target.getName() + "'s belongings...", 1);
        PlayerUtil.message(target, inspector.getName() + " is inspecting your belongings.", 1);

        super.open(player);

    }

    private ItemStack createPlaceholder(String name) {
        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            meta.displayName(Component.text(name).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("This slot is empty").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
            placeholder.setItemMeta(meta);
        }
        return placeholder;
    }

    @Override
    public void click(int slot) {
        super.click(slot); // runs any assigned onClick runnables

        Player inspector = this.inspector; // your stored field
        Player target = this.target;       // your stored field
        if (inspector.getOpenInventory().getTopInventory() != this.getInventory()) return;
        // PDC check: only allow if target has flag set to 1


        boolean isDowned = target.getPersistentDataContainer().has(new NamespacedKey(Specialization.getInstance(), "is_downed"), PersistentDataType.BYTE) &&
                target.getPersistentDataContainer().get(new NamespacedKey(Specialization.getInstance(), "is_downed"), PersistentDataType.BYTE) == 1;
        if (!isDowned) return;

        ItemStack realItem = null;
        if (slot <= 35) realItem = target.getInventory().getItem(slot);
        else if (slot >= 36 && slot <= 39) {
            switch (slot) {
                case 36 -> realItem = target.getInventory().getHelmet();
                case 37 -> realItem = target.getInventory().getChestplate();
                case 38 -> realItem = target.getInventory().getLeggings();
                case 39 -> realItem = target.getInventory().getBoots();
            }
        } else if (slot == 40) realItem = target.getInventory().getItemInOffHand();

        if (realItem == null || realItem.getType().isAir()) return;

        // Remove item from target
        if (slot <= 35) target.getInventory().setItem(slot, null);
        else if (slot >= 36 && slot <= 39) {
            switch (slot) {
                case 36 -> target.getInventory().setHelmet(null);
                case 37 -> target.getInventory().setChestplate(null);
                case 38 -> target.getInventory().setLeggings(null);
                case 39 -> target.getInventory().setBoots(null);
            }
        } else if (slot == 40) target.getInventory().setItemInOffHand(null);

        // Drop item
        target.getWorld().dropItemNaturally(target.getLocation(), realItem.clone());

        // Refresh GUI
//        this.closeGUI();
        PlayerUtil.message(inspector, "Dropping " + target.getName() + "'s belongings...", 5);
        PlayerUtil.message(target, inspector.getName() + " is dropping your belongings.", 5);
        this.open(inspector);
    }



}

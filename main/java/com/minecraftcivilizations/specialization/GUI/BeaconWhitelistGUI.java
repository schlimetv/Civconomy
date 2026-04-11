package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.Beacon.BeaconListener;
import com.minecraftcivilizations.specialization.Beacon.BeaconManager;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUI;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUIItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Beacon Whitelist control panel GUI.
 *
 * Layout (9-slot single row):
 *   [Save]  [Import]  [ ]  [ ]  [Info]  [ ]  [ ]  [Edit]  [Close]
 *    Slot0   Slot1                Slot4               Slot7   Slot8
 */
public class BeaconWhitelistGUI extends GUI {

    private final Specialization plugin;
    private final BeaconManager manager;
    private final BeaconListener listener;
    private final Block beaconBlock;
    private final Player owner;

    public BeaconWhitelistGUI(Specialization plugin, BeaconManager manager,
                               BeaconListener listener, Block beaconBlock, Player owner) {
        super(Component.text("Beacon Whitelist")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false), 9);
        this.plugin = plugin;
        this.manager = manager;
        this.listener = listener;
        this.beaconBlock = beaconBlock;
        this.owner = owner;
    }

    @Override
    public void open(Player player) {
        this.getItems().clear();
        List<String> currentWhitelist = manager.getWhitelist(beaconBlock);
        int radius = BeaconManager.getBeaconRadius(beaconBlock);

        this.getItems().put(0, createSaveButton(currentWhitelist));
        this.getItems().put(1, createImportButton());
        this.getItems().put(4, createInfoItem(currentWhitelist, radius));
        this.getItems().put(7, createEditButton());
        this.getItems().put(8, createCloseButton());

        ItemStack filler = createFiller();
        for (int i = 0; i < 9; i++) {
            if (!this.getItems().containsKey(i)) this.getItems().put(i, new GUIItem(filler, null));
        }

        super.open(player);
    }

    private GUIItem createSaveButton(List<String> currentWhitelist) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Save Whitelist")
                .color(NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.lore(buildLore("§7Click to save this beacon's whitelist", "§7as a reusable template.", "§8", "§7You can import it onto other beacons."));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return new GUIItem(item, () -> {
            if (currentWhitelist.isEmpty()) {
                PlayerUtil.message(owner, "§cThe whitelist is empty. Nothing to save.");
                owner.playSound(owner.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            manager.saveTemplate(owner.getUniqueId(), currentWhitelist);
            PlayerUtil.message(owner, "§aWhitelist saved! (" + currentWhitelist.size() + " names)");
            owner.playSound(owner.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
            this.open(owner);
        });
    }

    private GUIItem createImportButton() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Import Whitelist")
                .color(NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        List<String> loreParts = new ArrayList<>();
        loreParts.add("§7Click to import your saved");
        loreParts.add("§7whitelist onto this beacon.");
        loreParts.add("§8");
        if (manager.hasSavedTemplate(owner.getUniqueId())) {
            List<String> template = manager.getSavedTemplate(owner.getUniqueId());
            loreParts.add("§aSaved template (" + template.size() + " names):");
            for (int i = 0; i < Math.min(template.size(), 5); i++) loreParts.add("§7  • " + template.get(i));
            if (template.size() > 5) loreParts.add("§8  ...and " + (template.size() - 5) + " more");
        } else {
            loreParts.add("§cNo saved template found.");
            loreParts.add("§7Save a whitelist first!");
        }
        meta.lore(buildLore(loreParts.toArray(new String[0])));
        meta.addItemFlags(ItemFlag.values());
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);

        return new GUIItem(item, () -> {
            if (!manager.hasSavedTemplate(owner.getUniqueId())) {
                PlayerUtil.message(owner, "§cYou have no saved whitelist template. Save one first!");
                owner.playSound(owner.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
                return;
            }
            List<String> template = manager.getSavedTemplate(owner.getUniqueId());
            boolean ownerFound = template.stream().anyMatch(n -> n.equalsIgnoreCase(owner.getName()));
            if (!ownerFound) template.add(0, owner.getName());
            manager.setWhitelist(beaconBlock, template);
            PlayerUtil.message(owner, "§aWhitelist imported! " + template.size() + " player(s) whitelisted.");
            owner.playSound(owner.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.0f);
            this.open(owner);
        });
    }

    private GUIItem createInfoItem(List<String> currentWhitelist, int radius) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Beacon Whitelist")
                .color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));

        List<String> loreParts = new ArrayList<>();
        loreParts.add("§7Owner: §f" + owner.getName());
        loreParts.add("§7Radius: §f" + (radius > 0 ? radius + " blocks" : "§cNo pyramid"));
        loreParts.add("§8");
        if (currentWhitelist.isEmpty()) {
            loreParts.add("§7No players whitelisted.");
        } else {
            loreParts.add("§7Whitelisted players (" + currentWhitelist.size() + "):");
            for (int i = 0; i < Math.min(currentWhitelist.size(), 20); i++) {
                loreParts.add("§a  ✔ " + currentWhitelist.get(i));
            }
            if (currentWhitelist.size() > 20) loreParts.add("§8  ...and " + (currentWhitelist.size() - 20) + " more");
        }
        loreParts.add("§8");
        loreParts.add("§7Whitelisted players can place blocks");
        loreParts.add("§7within the beacon's radius.");
        loreParts.add("§7Others will be blocked.");

        meta.lore(buildLore(loreParts.toArray(new String[0])));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return new GUIItem(item, null);
    }

    private GUIItem createEditButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Edit Whitelist")
                .color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        meta.lore(buildLore("§7Click to open the whitelist editor.", "§7Type one player name per line.", "§7Sign the book when done to apply."));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return new GUIItem(item, () -> {
            owner.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> listener.openWhitelistBook(owner, beaconBlock), 2L);
        });
    }

    private GUIItem createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Close").color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return new GUIItem(item, () -> owner.closeInventory());
    }

    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> buildLore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        return lore;
    }
}

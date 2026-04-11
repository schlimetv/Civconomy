package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.Golem.IronGolemListener;
import com.minecraftcivilizations.specialization.Golem.IronGolemManager;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUI;
import minecraftcivilizations.com.minecraftCivilizationsCore.GUI.GUIItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Iron Golem Whitelist control panel GUI.
 *
 * Layout (9-slot single row):
 * <pre>
 *   [Save]  [Import]  [ ]  [ ]  [Info]  [ ]  [ ]  [Edit]  [Close]
 *    Slot0   Slot1                Slot4               Slot7   Slot8
 * </pre>
 *
 * - Save (Book & Quill icon): Saves the current golem whitelist as the builder's template
 * - Import (Enchanted Book icon): Imports the builder's saved template onto this golem
 * - Edit (Writable Book icon): Opens the Book & Quill UI for editing names
 * - Info (Iron Golem head / Iron Block): Shows current whitelist
 * - Close (Barrier): Closes the GUI
 *
 * @author Generated for CivLabs
 */
public class IronGolemWhitelistGUI extends GUI {

    private final Specialization plugin;
    private final IronGolemManager manager;
    private final IronGolemListener listener;
    private final IronGolem golem;
    private final Player owner;

    public IronGolemWhitelistGUI(Specialization plugin, IronGolemManager manager,
                                  IronGolemListener listener, IronGolem golem, Player owner) {
        super(Component.text("Iron Golem Whitelist")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false), 9);
        this.plugin = plugin;
        this.manager = manager;
        this.listener = listener;
        this.golem = golem;
        this.owner = owner;
    }

    @Override
    public void open(Player player) {
        this.getItems().clear();

        List<String> currentWhitelist = manager.getWhitelist(golem);

        // --- Slot 0: SAVE BUTTON (Book & Quill icon) ---
        this.getItems().put(0, createSaveButton(currentWhitelist));

        // --- Slot 1: IMPORT BUTTON (Enchanted Book icon) ---
        this.getItems().put(1, createImportButton());

        // --- Slot 4: INFO DISPLAY (Iron Block) ---
        this.getItems().put(4, createInfoItem(currentWhitelist));

        // --- Slot 7: EDIT WHITELIST (Writable Book) ---
        this.getItems().put(7, createEditButton());

        // --- Slot 8: CLOSE (Barrier) ---
        this.getItems().put(8, createCloseButton());

        // --- Fill empty slots with glass panes ---
        ItemStack filler = createFiller();
        for (int i = 0; i < 9; i++) {
            if (!this.getItems().containsKey(i)) {
                this.getItems().put(i, new GUIItem(filler, null));
            }
        }

        super.open(player);
    }

    // =========================================================================
    //  GUI Items
    // =========================================================================

    /**
     * Save button: saves the current golem whitelist as the builder's reusable template.
     * Uses Book & Quill icon as specified.
     */
    private GUIItem createSaveButton(List<String> currentWhitelist) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Save Whitelist")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(buildLore(
                "§7Click to save this golem's whitelist",
                "§7as a reusable template.",
                "§8",
                "§7You can import it onto other golems."
        ));
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
            owner.playSound(owner.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.5f);

            // Refresh the GUI to update the Import button lore
            this.open(owner);
        });
    }

    /**
     * Import button: imports the builder's saved template onto this golem.
     * Uses Enchanted Book icon as specified.
     */
    private GUIItem createImportButton() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Import Whitelist")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<String> loreParts = new ArrayList<>();
        loreParts.add("§7Click to import your saved");
        loreParts.add("§7whitelist onto this golem.");
        loreParts.add("§8");

        if (manager.hasSavedTemplate(owner.getUniqueId())) {
            List<String> template = manager.getSavedTemplate(owner.getUniqueId());
            loreParts.add("§aSaved template (" + template.size() + " names):");
            for (int i = 0; i < Math.min(template.size(), 5); i++) {
                loreParts.add("§7  • " + template.get(i));
            }
            if (template.size() > 5) {
                loreParts.add("§8  ...and " + (template.size() - 5) + " more");
            }
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

            // Ensure the owner is always included
            boolean ownerFound = false;
            for (String name : template) {
                if (name.equalsIgnoreCase(owner.getName())) {
                    ownerFound = true;
                    break;
                }
            }
            if (!ownerFound) {
                template.add(0, owner.getName());
            }

            manager.setWhitelist(golem, template);
            PlayerUtil.message(owner, "§aWhitelist imported! " + template.size() + " player(s) whitelisted.");
            owner.playSound(owner.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.0f);

            // Refresh the GUI to show updated whitelist
            this.open(owner);
        });
    }

    /**
     * Info display: shows the current whitelist and golem status.
     */
    private GUIItem createInfoItem(List<String> currentWhitelist) {
        ItemStack item = new ItemStack(Material.IRON_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Iron Golem Whitelist")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<String> loreParts = new ArrayList<>();
        loreParts.add("§7Owner: §f" + owner.getName());
        loreParts.add("§8");

        if (currentWhitelist.isEmpty()) {
            loreParts.add("§7No players whitelisted.");
        } else {
            loreParts.add("§7Whitelisted players (" + currentWhitelist.size() + "):");
            for (int i = 0; i < currentWhitelist.size(); i++) {
                String prefix = (i < 20) ? "§a  ✔ " : "§7  • ";
                if (i < 20) {
                    loreParts.add(prefix + currentWhitelist.get(i));
                }
            }
            if (currentWhitelist.size() > 20) {
                loreParts.add("§8  ...and " + (currentWhitelist.size() - 20) + " more");
            }
        }

        loreParts.add("§8");
        loreParts.add("§7Whitelisted players are protected.");
        loreParts.add("§7Attackers of whitelisted players");
        loreParts.add("§7will be targeted by this golem.");

        meta.lore(buildLore(loreParts.toArray(new String[0])));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return new GUIItem(item, null);
    }

    /**
     * Edit button: opens the Book & Quill for manual name editing.
     */
    private GUIItem createEditButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Edit Whitelist")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(buildLore(
                "§7Click to open the whitelist editor.",
                "§7Type one player name per line.",
                "§7Sign the book when done to apply."
        ));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return new GUIItem(item, () -> {
            // Close the chest GUI first
            owner.closeInventory();

            // Small delay to let the inventory close, then open the book
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                listener.openWhitelistBook(owner, golem);
            }, 2L);
        });
    }

    /**
     * Close button.
     */
    private GUIItem createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Close")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);

        return new GUIItem(item, () -> owner.closeInventory());
    }

    /**
     * Filler glass pane for empty slots.
     */
    private ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Helper to build a lore list from string lines.
     */
    private List<Component> buildLore(String... lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        return lore;
    }
}

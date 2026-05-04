package com.minecraftcivilizations.specialization.Beacon;

import com.minecraftcivilizations.specialization.GUI.BeaconWhitelistGUI;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handles all beacon whitelist events:
 * - Registers ownership when a beacon is placed
 * - Removes data when a beacon is broken
 * - Opens whitelist GUI on Shift + Right-Click (vanilla UI on normal right-click)
 * - Processes book edits for whitelist updates
 * - Blocks non-whitelisted players from placing blocks within beacon radius
 */
public class BeaconListener implements Listener {

    private final BeaconManager manager;
    private final Specialization plugin;
    private final NamespacedKey bookBeaconKey;

    // player UUID -> beacon location key for active book editors
    private final Map<UUID, String> activeEditors = new HashMap<>();
    // player UUID -> original held item before book swap
    private final Map<UUID, ItemStack> storedItems = new HashMap<>();

    public BeaconListener(Specialization plugin, BeaconManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.bookBeaconKey = new NamespacedKey(plugin, "beacon_whitelist_book");
    }

    // Only Builder Grandmaster (tier 5) can place or interact with beacons
    private static final int REQUIRED_BUILDER_LEVEL = 5;

    private boolean isBuilderGrandmaster(Player player) {
        CustomPlayer cp = CoreUtil.getPlayer(player);
        return cp != null && cp.getSkillLevel(SkillType.BUILDER) >= REQUIRED_BUILDER_LEVEL;
    }

    // =========================================================================
    //  Beacon Placement — only Builder Grandmaster, then register ownership
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.BEACON) return;

        Player player = event.getPlayer();

        if (!isBuilderGrandmaster(player)) {
            event.setCancelled(true);
            PlayerUtil.message(player, "§cYou must be a Builder Grandmaster to place beacons.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
            return;
        }

        manager.registerBeacon(event.getBlockPlaced(), player.getUniqueId());
        PlayerUtil.message(player, "§aBeacon placed! Shift + Right-Click it to manage its whitelist.");
        Debug.broadcast("beacon", "<green>" + player.getName() + " placed an owned beacon");
    }

    // =========================================================================
    //  Beacon Break — remove data
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBeaconBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.BEACON) return;
        if (!manager.isRegistered(event.getBlock())) return;

        manager.removeBeacon(event.getBlock());
        Debug.broadcast("beacon", "<red>" + event.getPlayer().getName() + " broke an owned beacon");
    }

    // =========================================================================
    //  Shift + Right-Click — open whitelist GUI (vanilla UI on normal click)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;

        Player player = event.getPlayer();

        // Only Builder Grandmaster can interact with beacons at all
        if (!isBuilderGrandmaster(player)) {
            event.setCancelled(true);
            PlayerUtil.message(player, "§cYou must be a Builder Grandmaster to use beacons.");
            return;
        }

        // Beacon not registered (placed before system existed) — let vanilla UI open
        if (!manager.isRegistered(block)) return;

        // Normal right-click → let vanilla beacon UI open
        if (!player.isSneaking()) return;

        // Shift + Right-Click → only the owner gets the whitelist GUI
        if (!manager.isOwner(block, player.getUniqueId())) return;

        event.setCancelled(true);

        BeaconWhitelistGUI gui = new BeaconWhitelistGUI(plugin, manager, this, block, player);
        gui.open(player);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    // =========================================================================
    //  Block Placement Protection — non-whitelisted players can't place blocks
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlaceInRadius(BlockPlaceEvent event) {
        // Skip beacon placement itself (handled by onBeaconPlace)
        if (event.getBlockPlaced().getType() == Material.BEACON) return;

        Player player = event.getPlayer();
        if (!manager.canPlaceAt(event.getBlockPlaced().getLocation(), player.getName())) {
            event.setCancelled(true);
            PlayerUtil.message(player, "§cYou are not whitelisted on a nearby beacon and cannot place blocks here.");
        }
    }

    // =========================================================================
    //  Book Editing — whitelist name entry
    // =========================================================================

    /**
     * Opens a Book & Quill for editing the whitelist of a beacon.
     * Called from the GUI "Edit" button.
     */
    public void openWhitelistBook(Player player, Block beaconBlock) {
        String locKey = beaconBlock.getWorld().getName() + ":" +
                beaconBlock.getX() + ":" + beaconBlock.getY() + ":" + beaconBlock.getZ();
        activeEditors.put(player.getUniqueId(), locKey);

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(bookBeaconKey, PersistentDataType.STRING, locKey);

        List<String> whitelist = manager.getWhitelist(beaconBlock);
        StringBuilder page1 = new StringBuilder("§l§5--- Whitelist ---§r\n§7One name per line:§r\n");
        StringBuilder page2 = new StringBuilder();
        int maxPage1 = 10;

        for (int i = 0; i < whitelist.size(); i++) {
            if (i < maxPage1) page1.append(whitelist.get(i)).append("\n");
            else page2.append(whitelist.get(i)).append("\n");
        }

        List<String> pages = new ArrayList<>();
        pages.add(page1.toString());
        pages.add(page2.length() > 0 ? page2.toString() : "§7(More names here)§r\n");
        meta.setPages(pages);
        book.setItemMeta(meta);

        storedItems.put(player.getUniqueId(), player.getInventory().getItemInMainHand().clone());
        player.getInventory().setItemInMainHand(book);
        player.updateInventory();

        PlayerUtil.message(player, "§eRight-click to open the whitelist book. Sign it when done to apply changes.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        ItemStack book = player.getInventory().getItemInMainHand();
        if (book.getType() != Material.WRITABLE_BOOK || !book.hasItemMeta()) return;

        BookMeta oldMeta = (BookMeta) book.getItemMeta();
        if (!oldMeta.getPersistentDataContainer().has(bookBeaconKey)) return;

        String locKey = oldMeta.getPersistentDataContainer().get(bookBeaconKey, PersistentDataType.STRING);
        if (locKey == null) return;

        if (!event.isSigning()) {
            // Preserve PDC tag on non-signing edits
            BookMeta newMeta = event.getNewBookMeta();
            newMeta.getPersistentDataContainer().set(bookBeaconKey, PersistentDataType.STRING, locKey);
            event.setNewBookMeta(newMeta);
            return;
        }

        event.setCancelled(true);

        // Parse the beacon location from key
        Block beaconBlock = parseBlock(locKey);
        if (beaconBlock == null || beaconBlock.getType() != Material.BEACON) {
            PlayerUtil.message(player, "§cThis beacon no longer exists.");
            restorePlayerItem(player);
            return;
        }

        if (!manager.isOwner(beaconBlock, player.getUniqueId())) {
            PlayerUtil.message(player, "§cYou are not the owner of this beacon.");
            restorePlayerItem(player);
            return;
        }

        List<String> parsedNames = parseNamesFromBook(event.getNewBookMeta());

        // Ensure owner is always on the whitelist
        boolean ownerFound = parsedNames.stream().anyMatch(n -> n.equalsIgnoreCase(player.getName()));
        if (!ownerFound) parsedNames.add(0, player.getName());

        manager.setWhitelist(beaconBlock, parsedNames);
        PlayerUtil.message(player, "§aWhitelist updated! " + parsedNames.size() + " player(s) whitelisted.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);

        Debug.broadcast("beacon", "<aqua>" + player.getName() + " updated beacon whitelist: " + parsedNames);

        restorePlayerItem(player);
        activeEditors.remove(player.getUniqueId());
    }

    // =========================================================================
    //  Cleanup
    // =========================================================================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activeEditors.remove(uuid);
        ItemStack original = storedItems.remove(uuid);
        if (original != null) {
            Player player = event.getPlayer();
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.WRITABLE_BOOK && inHand.hasItemMeta()
                    && inHand.getItemMeta().getPersistentDataContainer().has(bookBeaconKey)) {
                player.getInventory().setItemInMainHand(original);
            }
        }
    }

    // =========================================================================
    //  Utility
    // =========================================================================

    private void restorePlayerItem(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack original = storedItems.remove(player.getUniqueId());
            if (original != null) {
                player.getInventory().setItemInMainHand(original);
            } else {
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand.getType() == Material.WRITABLE_BOOK && inHand.hasItemMeta()
                        && inHand.getItemMeta().getPersistentDataContainer().has(bookBeaconKey)) {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
            }
            player.updateInventory();
        });
    }

    private List<String> parseNamesFromBook(BookMeta meta) {
        List<String> names = new ArrayList<>();
        for (String page : meta.getPages()) {
            String stripped = page.replaceAll("§.", "");
            for (String line : stripped.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("---") || trimmed.startsWith("(")) continue;
                if (trimmed.toLowerCase().contains("whitelist")) continue;
                if (trimmed.toLowerCase().contains("one name per line")) continue;
                if (trimmed.toLowerCase().contains("more names here")) continue;
                if (trimmed.matches("^[a-zA-Z0-9_]{3,16}$")) names.add(trimmed);
            }
        }
        return names;
    }

    private Block parseBlock(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        var world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return world.getBlockAt(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) { return null; }
    }
}

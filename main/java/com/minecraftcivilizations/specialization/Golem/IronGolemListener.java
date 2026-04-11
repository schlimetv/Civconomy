package com.minecraftcivilizations.specialization.Golem;

import com.minecraftcivilizations.specialization.GUI.IronGolemWhitelistGUI;
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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Handles all Iron Golem-related events:
 * <ul>
 *   <li>Only Builders (level >= 1) can create Iron Golems</li>
 *   <li>Ownership is assigned on creation</li>
 *   <li>Shift+Right-Click opens the whitelist GUI for the owner</li>
 *   <li>Whitelisted players are never targeted by the golem</li>
 *   <li>Players who attack whitelisted players become the golem's target</li>
 *   <li>Book editing events update the whitelist</li>
 * </ul>
 *
 * @author Generated for CivLabs
 */
public class IronGolemListener implements Listener {

    private final IronGolemManager manager;
    private final Specialization plugin;

    // PDC key placed on a virtual Book & Quill to identify it as a golem whitelist book
    private final NamespacedKey bookGolemKey;
    // Stores which golem UUID the player is currently editing (player UUID -> golem UUID)
    private final Map<UUID, UUID> activeEditors = new HashMap<>();

    // Minimum Builder level required to create golems
    private static final int MIN_BUILDER_LEVEL = 1;
    // Range within which a golem will defend whitelisted players
    private static final double DEFENSE_RANGE = 16.0;

    public IronGolemListener(Specialization plugin, IronGolemManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.bookGolemKey = new NamespacedKey(plugin, "golem_whitelist_book");
    }

    // =========================================================================
    //  GOLEM CREATION GATING — Only Builders can build golems
    // =========================================================================

    /**
     * Tracks when a player places a carved pumpkin. This is the "head" block
     * that completes the Iron Golem build pattern. We cache the player so we
     * can attribute ownership when CreatureSpawnEvent fires in the same tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPumpkinPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.CARVED_PUMPKIN) return;
        manager.recordPumpkinPlacer(event.getPlayer());
    }

    /**
     * Intercepts golem spawns caused by the build pattern (Iron Golem & Snow Golem).
     * If the placer is not a Builder (level >= 1), the spawn is cancelled.
     * If they ARE a Builder and it's an Iron Golem, we tag it with ownership.
     * Snow Golems are allowed for Builders but do not get the whitelist system.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGolemBuild(CreatureSpawnEvent event) {
        // Gate both Iron Golems and Snow Golems — only Builders can create any golem
        boolean isIronGolem = event.getEntityType() == EntityType.IRON_GOLEM
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM;
        boolean isSnowGolem = event.getEntityType() == EntityType.SNOW_GOLEM
                && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN;

        if (!isIronGolem && !isSnowGolem) return;

        Player builder = manager.findRecentPumpkinPlacer();

        if (builder == null) {
            // Could not determine who built it — cancel to be safe
            event.setCancelled(true);
            Debug.broadcast("golem", "<red>Golem spawn cancelled: could not determine builder");
            return;
        }

        // Check if the player has Builder level >= MIN_BUILDER_LEVEL
        CustomPlayer customPlayer = CoreUtil.getPlayer(builder);
        if (customPlayer == null || customPlayer.getSkillLevel(SkillType.BUILDER) < MIN_BUILDER_LEVEL) {
            event.setCancelled(true);
            String golemName = isIronGolem ? "Iron Golems" : "Snow Golems";
            PlayerUtil.message(builder,
                    "You must be at least a Builder level " + MIN_BUILDER_LEVEL + " to create " + golemName + ".");
            builder.playSound(builder.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
            manager.clearPumpkinPlacer(builder.getUniqueId());
            return;
        }

        manager.clearPumpkinPlacer(builder.getUniqueId());

        // Only Iron Golems get the ownership + whitelist system
        if (isIronGolem) {
            IronGolem golem = (IronGolem) event.getEntity();
            manager.setOwner(golem, builder.getUniqueId());

            // The owner is automatically whitelisted (never attacked by their own golem)
            List<String> initialWhitelist = new ArrayList<>();
            initialWhitelist.add(builder.getName());
            manager.setWhitelist(golem, initialWhitelist);

            PlayerUtil.message(builder,
                    "§aYou have created an Iron Golem! Shift + Right-Click it to manage its whitelist.");
            builder.playSound(builder.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);

            Debug.broadcast("golem",
                    "<green>" + builder.getName() + " created an owned Iron Golem at "
                            + golem.getLocation().getBlockX() + ", "
                            + golem.getLocation().getBlockY() + ", "
                            + golem.getLocation().getBlockZ());
        } else {
            // Snow Golem — allowed but no ownership/whitelist
            PlayerUtil.message(builder, "§aYou have created a Snow Golem!");
            builder.playSound(builder.getLocation(), Sound.BLOCK_SNOW_PLACE, 0.8f, 1.0f);

            Debug.broadcast("golem",
                    "<green>" + builder.getName() + " created a Snow Golem");
        }
    }

    // =========================================================================
    //  SHIFT + RIGHT-CLICK — Open Whitelist GUI
    // =========================================================================

    /**
     * When a player Shift+Right-Clicks an Iron Golem they own, open the whitelist GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractGolem(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof IronGolem golem)) return;

        Player player = event.getPlayer();

        // Must be sneaking (holding Left Shift)
        if (!player.isSneaking()) return;

        // Check if this golem is owned
        if (!manager.isOwned(golem)) return;

        // Only the owner can access the whitelist
        if (!manager.isOwner(golem, player)) {
            PlayerUtil.message(player, "§cYou are not the owner of this Iron Golem.");
            return;
        }

        event.setCancelled(true);

        // Open the whitelist GUI
        IronGolemWhitelistGUI gui = new IronGolemWhitelistGUI(plugin, manager, this, golem, player);
        gui.open(player);

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    // =========================================================================
    //  BOOK EDITING — Whitelist name entry via Book & Quill
    // =========================================================================

    /**
     * Opens a Book & Quill for editing the whitelist of a specific golem.
     * Called from the GUI when the player clicks "Edit Whitelist".
     */
    public void openWhitelistBook(Player player, IronGolem golem) {
        // Store the association: this player is editing this golem's whitelist
        activeEditors.put(player.getUniqueId(), golem.getUniqueId());

        // Create a virtual Book & Quill with the current whitelist pre-filled
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return;

        // Tag the book so we can identify it later
        meta.getPersistentDataContainer().set(bookGolemKey, PersistentDataType.STRING, golem.getUniqueId().toString());

        // Pre-fill pages with current whitelist names (one name per line)
        List<String> whitelist = manager.getWhitelist(golem);

        // Build page content: up to ~14 names per page (book line limit)
        // We'll use 2 pages as the user requested
        StringBuilder page1 = new StringBuilder();
        StringBuilder page2 = new StringBuilder();
        page1.append("§l§5--- Whitelist ---§r\n");
        page1.append("§7One name per line:§r\n");

        int maxNamesPerPage = 10; // Leave room for header text

        for (int i = 0; i < whitelist.size(); i++) {
            if (i < maxNamesPerPage) {
                page1.append(whitelist.get(i)).append("\n");
            } else {
                page2.append(whitelist.get(i)).append("\n");
            }
        }

        List<String> pages = new ArrayList<>();
        pages.add(page1.toString());
        if (page2.length() > 0 || whitelist.size() > maxNamesPerPage) {
            pages.add(page2.toString());
        } else {
            // Always provide 2 pages as the user requested
            pages.add("§7(More names here)§r\n");
        }
        meta.setPages(pages);
        book.setItemMeta(meta);

        // Save what the player was holding, give them the book
        // The player will right-click to open the editor, then sign when done
        ItemStack previousItem = player.getInventory().getItemInMainHand().clone();
        player.getInventory().setItemInMainHand(book);
        player.updateInventory();

        // Store original item for restoration after book editing
        storeOriginalItem(player.getUniqueId(), previousItem);

        PlayerUtil.message(player, "§eRight-click to open the whitelist book. Sign it when done to apply changes.");
    }

    // Temporary storage for the item the player was holding before we gave them the book
    private final Map<UUID, ItemStack> storedItems = new HashMap<>();

    private void storeOriginalItem(UUID playerUuid, ItemStack item) {
        storedItems.put(playerUuid, item);
    }

    private ItemStack retrieveOriginalItem(UUID playerUuid) {
        return storedItems.remove(playerUuid);
    }

    /**
     * Intercepts the PlayerEditBookEvent to capture whitelist changes.
     * When the player signs the golem whitelist book, we parse the names
     * and update the golem's whitelist.
     * When they edit without signing, we preserve the PDC tag.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBookEdit(PlayerEditBookEvent event) {
        Player player = event.getPlayer();
        ItemStack book = player.getInventory().getItemInMainHand();

        if (book.getType() != Material.WRITABLE_BOOK) return;
        if (!book.hasItemMeta()) return;

        BookMeta oldMeta = (BookMeta) book.getItemMeta();
        if (!oldMeta.getPersistentDataContainer().has(bookGolemKey)) return;

        // This is a golem whitelist book
        String golemUuidStr = oldMeta.getPersistentDataContainer().get(bookGolemKey, PersistentDataType.STRING);
        if (golemUuidStr == null) return;

        if (!event.isSigning()) {
            // Player edited pages but didn't sign — preserve the PDC tag
            // by re-applying it to the new meta so it survives the edit
            BookMeta newMeta = event.getNewBookMeta();
            newMeta.getPersistentDataContainer().set(bookGolemKey, PersistentDataType.STRING, golemUuidStr);
            event.setNewBookMeta(newMeta);
            return;
        }

        // Player signed the book — process the whitelist update
        event.setCancelled(true); // Prevent the book from becoming a written book

        UUID golemUuid;
        try {
            golemUuid = UUID.fromString(golemUuidStr);
        } catch (IllegalArgumentException e) {
            PlayerUtil.message(player, "§cError: Invalid golem reference.");
            restorePlayerItem(player);
            return;
        }

        // Find the golem entity in the world
        IronGolem golem = findGolemByUuid(golemUuid);
        if (golem == null || golem.isDead()) {
            PlayerUtil.message(player, "§cThis Iron Golem no longer exists.");
            restorePlayerItem(player);
            return;
        }

        // Verify the player is still the owner
        if (!manager.isOwner(golem, player)) {
            PlayerUtil.message(player, "§cYou are not the owner of this Iron Golem.");
            restorePlayerItem(player);
            return;
        }

        // Parse names from the book pages
        BookMeta newMeta = event.getNewBookMeta();
        List<String> parsedNames = parseNamesFromBook(newMeta);

        // Ensure the owner is always on the whitelist
        boolean ownerFound = false;
        for (String name : parsedNames) {
            if (name.equalsIgnoreCase(player.getName())) {
                ownerFound = true;
                break;
            }
        }
        if (!ownerFound) {
            parsedNames.add(0, player.getName());
        }

        // Apply the whitelist
        manager.setWhitelist(golem, parsedNames);

        PlayerUtil.message(player, "§aWhitelist updated! " + parsedNames.size() + " player(s) whitelisted.");
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);

        Debug.broadcast("golem", "<aqua>" + player.getName() + " updated golem whitelist: " + parsedNames);

        // Restore the player's original item
        restorePlayerItem(player);

        // Clean up editor tracking
        activeEditors.remove(player.getUniqueId());
    }

    /**
     * Parses player names from a book's pages.
     * Expects one name per line, ignores formatting codes and header text.
     */
    private List<String> parseNamesFromBook(BookMeta meta) {
        List<String> names = new ArrayList<>();
        for (String page : meta.getPages()) {
            // Strip all section sign formatting codes (§X)
            String stripped = page.replaceAll("§.", "");
            String[] lines = stripped.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                // Skip empty lines and header/instruction text
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("---") || trimmed.startsWith("(")) continue;
                if (trimmed.toLowerCase().contains("whitelist")) continue;
                if (trimmed.toLowerCase().contains("one name per line")) continue;
                if (trimmed.toLowerCase().contains("more names here")) continue;

                // Validate: Minecraft names are 3-16 chars, alphanumeric + underscore
                if (trimmed.matches("^[a-zA-Z0-9_]{3,16}$")) {
                    names.add(trimmed);
                }
            }
        }
        return names;
    }

    /**
     * Restores the item the player was holding before the book was opened.
     */
    private void restorePlayerItem(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack original = retrieveOriginalItem(player.getUniqueId());
            if (original != null) {
                player.getInventory().setItemInMainHand(original);
            } else {
                // If no stored item, just clear the book from their hand
                ItemStack inHand = player.getInventory().getItemInMainHand();
                if (inHand.getType() == Material.WRITABLE_BOOK && inHand.hasItemMeta()) {
                    if (inHand.getItemMeta().getPersistentDataContainer().has(bookGolemKey)) {
                        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                    }
                }
            }
            player.updateInventory();
        });
    }

    // =========================================================================
    //  GOLEM TARGETING AI — Whitelist protection
    // =========================================================================

    /**
     * Prevents an owned Iron Golem from targeting whitelisted players.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGolemTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof IronGolem golem)) return;
        if (!(event.getTarget() instanceof Player targetPlayer)) return;

        // Only apply to owned golems
        if (!manager.isOwned(golem)) return;

        // If the target player is whitelisted, cancel the targeting
        if (manager.isWhitelisted(golem, targetPlayer)) {
            event.setCancelled(true);
        }
    }

    /**
     * When a whitelisted player is attacked by another player, nearby owned
     * Iron Golems will target the attacker (if the attacker is not whitelisted).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWhitelistedPlayerAttacked(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // Determine the actual attacker (could be a projectile)
        Player attacker = resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;

        // Don't process self-damage
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        // Search for nearby owned Iron Golems that have the victim whitelisted
        for (Entity nearby : victim.getNearbyEntities(DEFENSE_RANGE, DEFENSE_RANGE, DEFENSE_RANGE)) {
            if (!(nearby instanceof IronGolem golem)) continue;
            if (!manager.isOwned(golem)) continue;

            // Victim must be on this golem's whitelist
            if (!manager.isWhitelisted(golem, victim)) continue;

            // Attacker must NOT be on the whitelist
            if (manager.isWhitelisted(golem, attacker)) continue;

            // Set the golem to target the attacker
            golem.setTarget(attacker);

            Debug.broadcast("golem",
                    "<red>" + attacker.getName() + " attacked whitelisted player "
                            + victim.getName() + " — golem is retaliating!");
        }
    }

    /**
     * Prevents owned Iron Golems from dealing damage to whitelisted players.
     * This is a safety net in case the targeting cancellation above is bypassed
     * (e.g., splash damage, sweeping edge, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGolemDamageWhitelisted(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof IronGolem golem)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        if (!manager.isOwned(golem)) return;

        if (manager.isWhitelisted(golem, victim)) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    //  CLEANUP
    // =========================================================================

    /**
     * Clean up editor state when a player logs out while editing.
     * Attempts to restore their original held item so the virtual book doesn't persist.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        activeEditors.remove(uuid);

        // If they were holding the virtual golem book, restore their original item
        ItemStack original = retrieveOriginalItem(uuid);
        if (original != null) {
            Player player = event.getPlayer();
            ItemStack inHand = player.getInventory().getItemInMainHand();
            if (inHand.getType() == Material.WRITABLE_BOOK && inHand.hasItemMeta()
                    && inHand.getItemMeta().getPersistentDataContainer().has(bookGolemKey)) {
                player.getInventory().setItemInMainHand(original);
            }
        }
    }

    // =========================================================================
    //  UTILITY
    // =========================================================================

    /**
     * Resolves the attacking player from a damage event.
     * Handles direct attacks and projectile attacks.
     */
    private Player resolvePlayerAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * Finds an Iron Golem entity by UUID across all loaded worlds.
     */
    private IronGolem findGolemByUuid(UUID uuid) {
        for (var world : Bukkit.getWorlds()) {
            for (IronGolem golem : world.getEntitiesByClass(IronGolem.class)) {
                if (golem.getUniqueId().equals(uuid)) {
                    return golem;
                }
            }
        }
        return null;
    }

    /**
     * Returns the golem UUID the player is currently editing, or null.
     */
    public UUID getEditingGolem(UUID playerUuid) {
        return activeEditors.get(playerUuid);
    }

    /**
     * Returns the manager instance.
     */
    public IronGolemManager getManager() {
        return manager;
    }
}

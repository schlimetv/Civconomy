package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles ambient sound looping for the Anvil (forge) and Smithing Table GUIs.
 *
 * Forge sounds   : play whenever any item is in the anvil ingredient slots (0 or 1).
 * Smithing sounds: play whenever any item is in the smithing ingredient slots (0, 1, or 2).
 * Both stop when all ingredient slots are cleared, or when the GUI is closed.
 *
 * Vanilla anvil-use and smithing-table-use sounds are suppressed via PacketListener.
 */
public class ForgeSmithingSoundListener implements Listener {

    // ─────────────────────────────────────────────────────────────
    //  Sound constants
    // ─────────────────────────────────────────────────────────────

    // Forge clips: play once per second = 20 ticks.
    private static final String FORGE_SOUND    = "steel:steel.forge";
    private static final int    FORGE_TICKS    = 20;

    // Smithing clips: play once per second = 20 ticks.
    private static final String SMITHING_SOUND = "steel:steel.smithing";
    private static final int    SMITHING_TICKS = 20;

    // Volume (30% quieter than full)
    private static final float  VOLUME         = 0.5f;

    // Craft burst: number of extra plays when a result is taken, spaced BURST_INTERVAL ticks apart
    private static final int    CRAFT_BURST    = 1;
    private static final int    BURST_INTERVAL = 10;

    // Anvil slot indices
    private static final int ANVIL_SLOT_LEFT   = 0;
    private static final int ANVIL_SLOT_RIGHT  = 1;
    private static final int ANVIL_OUTPUT_SLOT = 2;

    // Smithing table slot indices
    private static final int SMITHING_SLOT_TEMPLATE = 0;
    private static final int SMITHING_SLOT_BASE     = 1;
    private static final int SMITHING_SLOT_ADDITION = 2;
    private static final int SMITHING_OUTPUT_SLOT   = 3;

    // ─────────────────────────────────────────────────────────────
    //  State tracking
    // ─────────────────────────────────────────────────────────────

    private final Plugin plugin;

    /** Active looping task per player for the forge (anvil) sound. */
    private final Map<UUID, BukkitTask> activeForge    = new ConcurrentHashMap<>();

    /** Active looping task per player for the smithing sound. */
    private final Map<UUID, BukkitTask> activeSmithing = new ConcurrentHashMap<>();

    public ForgeSmithingSoundListener(Plugin plugin) {
        this.plugin = plugin;
    }

    // ═════════════════════════════════════════════════════════════
    //  ANVIL (forge)
    // ═════════════════════════════════════════════════════════════

    @EventHandler
    public void onAnvilOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        // Player may open an anvil that already has items in it
        scheduleAnvilCheck(player, (AnvilInventory) event.getInventory());
    }

    /**
     * Any click inside the anvil GUI — placing items into ingredient slots,
     * removing them, or taking the output — triggers a 1-tick delayed slot check.
     * The 1-tick delay lets Bukkit finish updating the inventory before we read it.
     */
    @EventHandler
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        // Craft burst: fire 3 rapid sounds when taking the output
        if (event.getRawSlot() == ANVIL_OUTPUT_SLOT
                && event.getCurrentItem() != null
                && !event.getCurrentItem().getType().isAir()) {
            playCraftBurst(player, FORGE_SOUND);
            // Suppress the auto-played vanilla anvil-use sound (1 tick delay matches server timing)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.stopSound(org.bukkit.Sound.BLOCK_ANVIL_USE, SoundCategory.BLOCKS);
            }, 1L);
        }
        scheduleAnvilCheck(player, (AnvilInventory) event.getInventory());
    }

    /** Drag events also place items into slots. */
    @EventHandler
    public void onAnvilDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        scheduleAnvilCheck(player, (AnvilInventory) event.getInventory());
    }

    @EventHandler
    public void onAnvilClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.ANVIL) return;
        UUID uid = player.getUniqueId();
        stopLoop(uid, activeForge);
        player.stopSound(FORGE_SOUND, SoundCategory.BLOCKS);
    }

    /**
     * Schedules a 1-tick delayed check on the anvil ingredient slots.
     * Starts the forge loop if any ingredient slot is occupied; stops it if both are empty.
     */
    private void scheduleAnvilCheck(Player player, AnvilInventory inv) {
        UUID uid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            boolean hasIngredient = hasItem(inv.getItem(ANVIL_SLOT_LEFT))
                                 || hasItem(inv.getItem(ANVIL_SLOT_RIGHT));
            if (hasIngredient) {
                if (!activeForge.containsKey(uid)) {
                    startForgeLoop(player);
                }
            } else {
                stopLoop(uid, activeForge);
                player.stopSound(FORGE_SOUND, SoundCategory.BLOCKS);
            }
        }, 1L);
    }

    // ═════════════════════════════════════════════════════════════
    //  SMITHING TABLE
    // ═════════════════════════════════════════════════════════════

    @EventHandler
    public void onSmithingOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.SMITHING) return;
        scheduleSmithingCheck(player, (SmithingInventory) event.getInventory());
    }

    @EventHandler
    public void onSmithingClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.SMITHING) return;
        // Craft burst: fire 3 rapid sounds when taking the output
        if (event.getRawSlot() == SMITHING_OUTPUT_SLOT
                && event.getCurrentItem() != null
                && !event.getCurrentItem().getType().isAir()) {
            playCraftBurst(player, SMITHING_SOUND);
        }
        scheduleSmithingCheck(player, (SmithingInventory) event.getInventory());
    }

    @EventHandler
    public void onSmithingDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.SMITHING) return;
        scheduleSmithingCheck(player, (SmithingInventory) event.getInventory());
    }

    @EventHandler
    public void onSmithingClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.SMITHING) return;
        UUID uid = player.getUniqueId();
        stopLoop(uid, activeSmithing);
        player.stopSound(SMITHING_SOUND, SoundCategory.BLOCKS);
    }

    /**
     * Schedules a 1-tick delayed check on the smithing ingredient slots.
     * Starts the smithing loop if any ingredient slot is occupied; stops it if all are empty.
     */
    private void scheduleSmithingCheck(Player player, SmithingInventory inv) {
        UUID uid = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            boolean hasIngredient = hasItem(inv.getItem(SMITHING_SLOT_TEMPLATE))
                                 || hasItem(inv.getItem(SMITHING_SLOT_BASE))
                                 || hasItem(inv.getItem(SMITHING_SLOT_ADDITION));
            if (hasIngredient) {
                if (!activeSmithing.containsKey(uid)) {
                    startSmithingLoop(player);
                }
            } else {
                stopLoop(uid, activeSmithing);
                player.stopSound(SMITHING_SOUND, SoundCategory.BLOCKS);
            }
        }, 1L);
    }

    // ═════════════════════════════════════════════════════════════
    //  Sound loop helpers
    // ═════════════════════════════════════════════════════════════

    /**
     * Starts a repeating forge-sound loop for the given player.
     * Each repetition fires exactly when the previous clip ends (FORGE_TICKS interval).
     * Minecraft randomly selects a variant from the steel.forge sound pool each play call.
     */
    private void startForgeLoop(Player player) {
        UUID uid = player.getUniqueId();
        stopLoop(uid, activeForge);

        // Play first clip immediately
        player.getWorld().playSound(player.getLocation(), FORGE_SOUND, SoundCategory.BLOCKS, VOLUME, 1.0f);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); activeForge.remove(uid); return; }
                if (!activeForge.containsKey(uid)) { cancel(); return; }
                player.getWorld().playSound(player.getLocation(), FORGE_SOUND, SoundCategory.BLOCKS, VOLUME, 1.0f);
            }
        }.runTaskTimer(plugin, FORGE_TICKS, FORGE_TICKS);

        activeForge.put(uid, task);
    }

    /**
     * Starts a repeating smithing-sound loop for the given player.
     */
    private void startSmithingLoop(Player player) {
        UUID uid = player.getUniqueId();
        stopLoop(uid, activeSmithing);

        player.getWorld().playSound(player.getLocation(), SMITHING_SOUND, SoundCategory.BLOCKS, VOLUME, 1.0f);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); activeSmithing.remove(uid); return; }
                if (!activeSmithing.containsKey(uid)) { cancel(); return; }
                player.getWorld().playSound(player.getLocation(), SMITHING_SOUND, SoundCategory.BLOCKS, VOLUME, 1.0f);
            }
        }.runTaskTimer(plugin, SMITHING_TICKS, SMITHING_TICKS);

        activeSmithing.put(uid, task);
    }

    /**
     * Plays the given sound CRAFT_BURST times, spaced BURST_INTERVAL ticks apart.
     * Gives tactile feedback that a recipe was completed.
     */
    private void playCraftBurst(Player player, String sound) {
        for (int i = 0; i < CRAFT_BURST; i++) {
            final int delay = i * BURST_INTERVAL;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    // Broadcast so nearby players hear the craft strike
                    player.getWorld().playSound(player.getLocation(), sound, SoundCategory.BLOCKS, VOLUME, 1.0f);
                }
            }, delay);
        }
    }

    private void stopLoop(UUID uid, Map<UUID, BukkitTask> map) {
        BukkitTask task = map.remove(uid);
        if (task != null) task.cancel();
    }

    /** Returns true if the slot contains a real item (not null and not air). */
    private boolean hasItem(ItemStack item) {
        return item != null && !item.getType().isAir();
    }
}

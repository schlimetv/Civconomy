package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles enhanced hoe tilling for the Civconomy metalworking progression.
 *
 * <p>Each hoe tier has two properties:
 * <ul>
 *   <li><b>Grid size</b> — how many blocks are tilled per click (1x1, 2x2, 3x3, etc.)</li>
 *   <li><b>Tilling mode</b> — "basic" advances one stage per click, "instant" jumps
 *       straight to farmland regardless of the starting block</li>
 * </ul>
 *
 * <p>Tilling chain: {@code COARSE_DIRT → DIRT → FARMLAND}
 *
 * <p>Grid orientation: the clicked block is always on the <b>bottom row</b>
 * of the grid relative to the player's facing direction.  For even-width
 * grids (2×2, 4×4) the clicked block sits at bottom-left; for odd-width
 * grids (3×3, 8×8) it sits at bottom-center.
 *
 * <p>Durability cost: 1 per block actually tilled (blocks that are already
 * farmland or non-tillable are skipped and cost nothing).
 *
 * @author CivLabs
 */
public class HoeTillingListener implements Listener {

    // ─── Blocks that can be tilled ───
    // "Tillable" means a hoe right-click can advance them toward farmland.
    // GRASS_BLOCK and DIRT_PATH are vanilla-tillable too (they go straight
    // to farmland in one click), so we include them for the area-of-effect
    // tilling — vanilla handles the clicked block, we handle the extras.
    private static final Set<Material> TILLABLE_BLOCKS = Set.of(
            Material.COARSE_DIRT,
            Material.DIRT,
            Material.GRASS_BLOCK,
            Material.DIRT_PATH,
            Material.ROOTED_DIRT,
            Material.MYCELIUM,
            Material.PODZOL
    );

    // ─── Tilling mode enum ───
    // BASIC  = advance one stage   (coarse_dirt→dirt, dirt→farmland)
    // INSTANT = skip straight to farmland from any tillable block
    private enum TillMode { BASIC, INSTANT }

    /**
     * Defines the tilling behavior of a hoe tier.
     *
     * @param gridWidth  number of columns (left-right relative to player)
     * @param gridDepth  number of rows (forward from clicked block)
     * @param mode       BASIC or INSTANT tilling
     */
    private record HoeTier(int gridWidth, int gridDepth, TillMode mode) {}

    // ─── Hoe tier definitions ───
    // Wooden and Stone hoes are not listed — they use default vanilla behavior.
    private static final HoeTier COPPER    = new HoeTier(1, 1, TillMode.INSTANT);
    private static final HoeTier BRONZE    = new HoeTier(2, 2, TillMode.BASIC);
    private static final HoeTier IRON      = new HoeTier(2, 2, TillMode.INSTANT);
    private static final HoeTier STEEL     = new HoeTier(3, 3, TillMode.BASIC);    // placeholder
    private static final HoeTier DIAMOND   = new HoeTier(4, 4, TillMode.INSTANT);
    private static final HoeTier NETHERITE = new HoeTier(8, 8, TillMode.INSTANT);

    // ─── Custom item ID constants ───
    // These must match the IDs registered in MetalworkingItems.
    private static final String CUSTOM_ID_COPPER_HOE = "copper_hoe";
    private static final String CUSTOM_ID_BRONZE_HOE = "bronze_hoe";
    // Steel hoe doesn't exist yet — this is a forward-compatible placeholder.
    private static final String CUSTOM_ID_STEEL_HOE  = "steel_hoe";

    // ─── PDC key used by the CustomItemManager to tag custom items ───
    private static final NamespacedKey CUSTOM_ITEM_KEY =
            new NamespacedKey("specialization", "custom_item_id");

    /**
     * Intercepts right-click on a block when the player is holding a hoe.
     *
     * <p>Runs at HIGH priority so it fires <i>after</i> most protection
     * plugins (which typically run at NORMAL or LOW) have had a chance to
     * cancel the event, but <i>before</i> MONITOR-priority listeners that
     * just observe.  If the event is already cancelled (e.g. by a region
     * protection plugin), we bail out immediately.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHoeTill(PlayerInteractEvent event) {
        // ── Gate checks (cheapest first) ──
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        if (!TILLABLE_BLOCKS.contains(clicked.getType())) return;

        Player player = event.getPlayer();
        ItemStack hoeItem = player.getInventory().getItemInMainHand();

        // Determine hoe tier from the item being held
        HoeTier tier = resolveHoeTier(hoeItem);
        if (tier == null) return; // not one of our enhanced hoes

        // ── Cancel the vanilla tilling event ──
        // We take over entirely so that instant-tilling and area-of-effect
        // work correctly.  Without this, vanilla would also till the clicked
        // block (causing a double-till on basic mode or fighting us on state).
        event.setCancelled(true);

        // ── Compute the grid of blocks to till ──
        BlockFace facing = getCardinalFacing(player);
        List<Block> targets = computeGrid(clicked, facing, tier);

        // ── Till each block and track durability cost ──
        int blocksTilled = 0;
        for (Block target : targets) {
            if (tillBlock(target, tier.mode)) {
                blocksTilled++;
            }
        }

        if (blocksTilled == 0) return;

        // ── Apply durability cost and feedback ──
        applyDurability(player, hoeItem, blocksTilled);
        player.swingHand(EquipmentSlot.HAND);

        // Play tilling sound at the clicked block (using block coords
        // directly to avoid allocating a new Location object)
        clicked.getWorld().playSound(
                clicked.getLocation().add(0.5, 0.5, 0.5),
                Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS,
                1.0f, 1.0f
        );

        // Award Farmer XP — 1 XP per block tilled
        CustomPlayer cp = CoreUtil.getPlayer(player);
        if (cp != null) {
            cp.addSkillXp(SkillType.FARMER, blocksTilled, clicked.getLocation());
        }

        Debug.broadcast("hoe",
                "<green>" + player.getName() + "</green> tilled <yellow>"
                        + blocksTilled + "</yellow> blocks with <gold>"
                        + getHoeName(hoeItem) + "</gold>");
    }


    // ═══════════════════════════════════════════════════════════════
    //  HOE TIER RESOLUTION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Determines which {@link HoeTier} the given item corresponds to.
     * Returns {@code null} if the item is not an enhanced hoe (wooden
     * and stone hoes are intentionally excluded — they keep vanilla behavior).
     *
     * <p>Custom items (copper, bronze, steel) are identified by their
     * PDC custom_item_id tag.  Vanilla items (iron, diamond, netherite)
     * are identified by their Material type.
     */
    private HoeTier resolveHoeTier(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        // ── Check for custom items first (copper, bronze, steel) ──
        // Custom items use vanilla base materials (e.g. copper_hoe uses STONE_HOE,
        // bronze_hoe uses IRON_HOE), so we must check the PDC tag before
        // falling through to vanilla material checks.
        String customId = getCustomItemId(item);
        if (customId != null) {
            return switch (customId) {
                case CUSTOM_ID_COPPER_HOE -> COPPER;
                case CUSTOM_ID_BRONZE_HOE -> BRONZE;
                case CUSTOM_ID_STEEL_HOE  -> STEEL;
                default -> null; // some other custom item that happens to look like a hoe
            };
        }

        // ── Vanilla hoe materials ──
        // Wooden and stone hoes are intentionally absent — they keep default behavior.
        return switch (item.getType()) {
            case IRON_HOE      -> IRON;
            case DIAMOND_HOE   -> DIAMOND;
            case NETHERITE_HOE -> NETHERITE;
            default -> null;
        };
    }

    /**
     * Reads the custom_item_id from an item's PDC, or returns null
     * if the item has no custom ID (i.e. it's a vanilla item).
     */
    private String getCustomItemId(ItemStack item) {
        if (!item.hasItemMeta()) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(CUSTOM_ITEM_KEY, PersistentDataType.STRING);
    }


    // ═══════════════════════════════════════════════════════════════
    //  GRID COMPUTATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Builds the list of blocks that should be tilled based on the
     * clicked block's position, the player's facing direction, and
     * the hoe tier's grid dimensions.
     *
     * <p><b>Layout rule:</b> The clicked block is always on the
     * bottom row of the grid (closest to the player).  For even-width
     * grids it sits at the left side; for odd-width grids it sits
     * at center.
     *
     * <p>"Forward" is the direction the player is facing (away from
     * them), and "right" is 90° clockwise from forward.
     *
     * <p>Think of it like placing a stamp on the ground: the clicked
     * block is your anchor point at the bottom edge, and the grid
     * extends forward (away from you) and to the right.
     */
    private List<Block> computeGrid(Block clicked, BlockFace facing, HoeTier tier) {
        int width = tier.gridWidth;
        int depth = tier.gridDepth;

        // For a 1x1 grid, just return the clicked block
        if (width == 1 && depth == 1) {
            return List.of(clicked);
        }

        // Determine the "right" direction from the player's facing
        BlockFace right = getRightFace(facing);

        // Calculate how many columns to the LEFT of the clicked block
        // the grid extends.  The clicked block sits at column [leftOffset]
        // within the grid.
        //
        // Layout per grid size (X = clicked block):
        //   2x2:  X □    → leftOffset = 0  (bottom-left, extends right)
        //         □ □
        //   3x3:  □ □ □  → leftOffset = 1  (bottom-center)
        //         □ □ □
        //         □ X □
        //   4x4:  □ □ □ □ → leftOffset = 1  (bottom row, 2nd from left)
        //         □ □ □ □
        //         □ □ □ □
        //         □ X □ □
        //   8x8:  ...     → leftOffset = 3  (bottom row, 4th from left)
        //
        // Rule: 2x2 anchors at bottom-left; everything larger centers
        // the clicked block (even widths use width/2 - 1, odd use width/2).
        int leftOffset;
        if (width <= 2) {
            leftOffset = 0;
        } else if (width % 2 == 0) {
            leftOffset = width / 2 - 1;
        } else {
            leftOffset = width / 2;
        }

        List<Block> blocks = new ArrayList<>(width * depth);

        for (int row = 0; row < depth; row++) {
            for (int col = 0; col < width; col++) {
                // "forward" steps = row (0 = bottom row where player clicked)
                // "right" steps   = col - leftOffset
                int forwardSteps = row;
                int rightSteps = col - leftOffset;

                Block target = clicked
                        .getRelative(facing, forwardSteps)
                        .getRelative(right, rightSteps);

                blocks.add(target);
            }
        }

        return blocks;
    }

    /**
     * Returns the cardinal direction the player is facing, snapped
     * to one of the four cardinal faces (NORTH, SOUTH, EAST, WEST).
     *
     * <p>We use the player's yaw, which is the horizontal rotation
     * angle.  Minecraft's yaw works like a compass:
     * <ul>
     *   <li>0° / 360° = South</li>
     *   <li>90° = West</li>
     *   <li>180° = North</li>
     *   <li>270° = East</li>
     * </ul>
     */
    private BlockFace getCardinalFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        // Normalize yaw to 0-360 range
        yaw = ((yaw % 360) + 360) % 360;

        if (yaw >= 315 || yaw < 45)   return BlockFace.SOUTH;
        if (yaw >= 45 && yaw < 135)   return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225)  return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    /**
     * Returns the BlockFace that is 90° clockwise (to the right)
     * of the given facing direction.
     *
     * <p>If you're facing North, your right is East.
     * If you're facing East, your right is South.  And so on.
     */
    private BlockFace getRightFace(BlockFace facing) {
        return switch (facing) {
            case NORTH -> BlockFace.EAST;
            case EAST  -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST  -> BlockFace.NORTH;
            default    -> BlockFace.EAST; // fallback, shouldn't happen
        };
    }


    // ═══════════════════════════════════════════════════════════════
    //  BLOCK TILLING LOGIC
    // ═══════════════════════════════════════════════════════════════

    /**
     * Attempts to till a single block.  Returns {@code true} if the
     * block was actually changed (and therefore should cost durability).
     *
     * <p>The tilling chain is:
     * <pre>
     *   COARSE_DIRT  →  DIRT  →  FARMLAND
     *   GRASS_BLOCK  →  FARMLAND  (single step, like vanilla)
     *   DIRT_PATH    →  FARMLAND  (single step, like vanilla)
     *   ROOTED_DIRT  →  DIRT  →  FARMLAND
     *   MYCELIUM     →  DIRT  →  FARMLAND
     *   PODZOL       →  DIRT  →  FARMLAND
     * </pre>
     *
     * <p>In BASIC mode, each click advances one step.
     * In INSTANT mode, the block jumps straight to FARMLAND.
     *
     * <p>Blocks that are already FARMLAND, or non-tillable blocks,
     * are skipped (return false).
     *
     * <p>A block can only become farmland if the block above it is
     * air (or passable) — otherwise crops couldn't be planted anyway,
     * and we'd create visual weirdness.
     */
    private boolean tillBlock(Block block, TillMode mode) {
        Material type = block.getType();

        // Skip blocks that aren't tillable
        if (!TILLABLE_BLOCKS.contains(type)) return false;

        // Can't till if there's a solid block on top
        // (farmland needs open air above for crop planting)
        if (block.getRelative(BlockFace.UP).getType().isSolid()) return false;

        if (mode == TillMode.INSTANT) {
            // Instant mode: everything goes straight to farmland
            block.setType(Material.FARMLAND);
            return true;
        }

        // Basic mode: advance one step in the chain
        Material nextState = getNextTillState(type);
        if (nextState != null) {
            block.setType(nextState);
            return true;
        }

        return false;
    }

    /**
     * Returns the next state in the tilling chain for BASIC mode,
     * or {@code null} if the block is already at the end of its chain.
     *
     * <p>Two-step blocks (coarse dirt, rooted dirt, mycelium, podzol)
     * go through dirt first.  Single-step blocks (dirt, grass, path)
     * go straight to farmland.
     */
    private Material getNextTillState(Material current) {
        return switch (current) {
            // Two-step blocks: first convert to dirt
            case COARSE_DIRT, ROOTED_DIRT, MYCELIUM, PODZOL -> Material.DIRT;
            // Single-step blocks: convert to farmland
            case DIRT, GRASS_BLOCK, DIRT_PATH -> Material.FARMLAND;
            default -> null;
        };
    }


    // ═══════════════════════════════════════════════════════════════
    //  DURABILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Reduces the hoe's durability by the given amount.  If the
     * durability reaches zero, the hoe breaks (removed from hand)
     * with the standard break sound.
     *
     * <p>This works for both vanilla items (which use the standard
     * Damageable interface) and custom items (which set maxDamage
     * via the same interface in MetalworkingItems).
     *
     * <p>Respects the Unbreaking enchantment — each point of
     * Unbreaking gives a chance to skip the durability loss
     * (vanilla formula: 1/(level+1) chance to consume durability).
     */
    private void applyDurability(Player player, ItemStack hoe, int amount) {
        if (!(hoe.getItemMeta() instanceof Damageable damageable)) return;

        // Read enchantment from the meta we already have, avoiding a
        // second getItemMeta() call that would clone the entire NBT tree
        int unbreakingLevel = damageable.getEnchantLevel(org.bukkit.enchantments.Enchantment.UNBREAKING);

        // Vanilla Unbreaking formula: each durability point has a
        // 1/(level+1) chance of actually being consumed.
        // For Unbreaking 0 the chance is 100%, so we skip the loop.
        int actualDamage;
        if (unbreakingLevel <= 0) {
            actualDamage = amount;
        } else {
            actualDamage = 0;
            double chance = 1.0 / (unbreakingLevel + 1);
            java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
            for (int i = 0; i < amount; i++) {
                if (rng.nextDouble() < chance) actualDamage++;
            }
        }

        if (actualDamage <= 0) return;

        int newDamage = damageable.getDamage() + actualDamage;
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage()
                : hoe.getType().getMaxDurability();

        if (newDamage >= maxDamage) {
            // Hoe breaks
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS,
                    1.0f, 1.0f
            );
        } else {
            damageable.setDamage(newDamage);
            hoe.setItemMeta((ItemMeta) damageable);
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //  UTILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a human-readable name for the hoe, used in debug messages.
     */
    private String getHoeName(ItemStack item) {
        String customId = getCustomItemId(item);
        if (customId != null) return customId;
        return item.getType().name().toLowerCase();
    }
}

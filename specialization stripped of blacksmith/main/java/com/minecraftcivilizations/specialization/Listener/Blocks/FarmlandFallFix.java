package com.minecraftcivilizations.specialization.Listener.Blocks;

import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Collection;

/**
 * Fixes a client-server desync caused by RealisticPlantGrowth (RPG).
 *
 * <p><b>The problem:</b><br>
 * RPG converts farmland to coarse dirt via a delayed {@code runTaskLater}
 * when a player harvests a crop.  Farmland has a 15/16-block hitbox (surface
 * at Y+0.9375) while dirt/coarse dirt is a full block (surface at Y+1.0).
 * Because the type change happens on a later tick, Paper does not push the
 * player up to the new surface.  The player ends up embedded inside the new
 * full block and falls through.</p>
 *
 * <p><b>The fix:</b><br>
 * Instead of reacting <i>after</i> RPG converts the block (a timing race we
 * can't reliably win), we act <b>preemptively on the same tick</b> as the
 * crop break.  Any player standing on the farmland is immediately teleported
 * up to the full-block surface height (a 0.0625-block / 1-pixel nudge that
 * is completely imperceptible).  When RPG converts the farmland on the next
 * tick, the player is already at the correct height and cannot clip through.
 * A delayed safety-net check runs 2 ticks later to catch any stragglers.</p>
 */
public class FarmlandFallFix implements Listener {

    /**
     * Listens at MONITOR priority so we run after every other plugin
     * (including RPG) has finished handling the break event itself.
     * We only read state here — we never cancel or modify the event.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();

        // Only care about crops / ageable plant blocks sitting on farmland.
        if (!(broken.getBlockData() instanceof Ageable)) return;

        Block below = broken.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.FARMLAND) return;

        final Location farmlandLoc = below.getLocation();
        final double fullBlockTopY = farmlandLoc.getBlockY() + 1.0;

        // --- Preemptive fix (same tick) ---
        // Bump the breaking player immediately if they're on this block.
        nudgePlayerUp(event.getPlayer(), farmlandLoc, fullBlockTopY);

        // Also catch any OTHER players standing on the same farmland block.
        Collection<Player> nearby = farmlandLoc.getNearbyPlayers(2.0);
        for (Player player : nearby) {
            if (player == event.getPlayer()) continue; // already handled
            nudgePlayerUp(player, farmlandLoc, fullBlockTopY);
        }

        // --- Delayed safety net (tick +2) ---
        // If somehow a player wasn't caught by the preemptive fix (e.g.
        // they landed on the block between the break event and RPG's
        // conversion), this backstop will catch them.
        Specialization.getInstance().getServer().getScheduler().runTaskLater(
                Specialization.getInstance(),
                () -> correctPlayerPositions(farmlandLoc, fullBlockTopY),
                2L
        );
    }

    /**
     * Nudge a single player up to the full-block surface if they are
     * standing on the farmland block column and in the vulnerable Y range.
     */
    private void nudgePlayerUp(Player player, Location farmlandLoc, double fullBlockTopY) {
        if (player.getGameMode() != GameMode.SURVIVAL
                && player.getGameMode() != GameMode.ADVENTURE) return;

        Location pLoc = player.getLocation();

        // Must be in the same block column.
        if (pLoc.getBlockX() != farmlandLoc.getBlockX()
                || pLoc.getBlockZ() != farmlandLoc.getBlockZ()) return;

        double feetY = pLoc.getY();

        // Farmland surface is at blockY + 0.9375.  We want to catch any
        // player whose feet are between that surface (minus a small gravity
        // tolerance) and the full-block top.  This is the 0.0625-block
        // danger zone where they'd become embedded after conversion.
        double farmlandSurface = farmlandLoc.getBlockY() + 0.9375;
        if (feetY >= farmlandSurface - 0.08 && feetY < fullBlockTopY) {
            Location corrected = pLoc.clone();
            corrected.setY(fullBlockTopY);
            player.teleport(corrected);
        }
    }

    /**
     * Delayed safety net: if the block was converted and any player is now
     * embedded, push them up.  Uses a wider Y tolerance than the preemptive
     * check since the player may have been sinking for 1-2 ticks.
     */
    private void correctPlayerPositions(Location farmlandLoc, double fullBlockTopY) {
        Block block = farmlandLoc.getBlock();

        // If it's still farmland, RPG didn't convert it — nothing to fix.
        if (block.getType() == Material.FARMLAND) return;
        // If it became air/fluid, don't interfere.
        if (!block.getType().isSolid()) return;

        Collection<Player> nearby = farmlandLoc.getNearbyPlayers(2.0);
        for (Player player : nearby) {
            if (player.getGameMode() != GameMode.SURVIVAL
                    && player.getGameMode() != GameMode.ADVENTURE) continue;

            Location pLoc = player.getLocation();

            if (pLoc.getBlockX() != farmlandLoc.getBlockX()
                    || pLoc.getBlockZ() != farmlandLoc.getBlockZ()) continue;

            double feetY = pLoc.getY();
            double blockBottomY = farmlandLoc.getBlockY();

            // Wider range: catch anyone whose feet are inside the block
            // (between blockBottomY and fullBlockTopY).  After 2 ticks of
            // falling, they could be well below the farmland surface.
            if (feetY >= blockBottomY && feetY < fullBlockTopY) {
                Location corrected = pLoc.clone();
                corrected.setY(fullBlockTopY);
                player.teleport(corrected);
            }
        }
    }
}

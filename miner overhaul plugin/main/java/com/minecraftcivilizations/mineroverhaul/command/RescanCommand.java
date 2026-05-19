package com.minecraftcivilizations.mineroverhaul.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Subcommand;
import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.scan.Cluster;
import com.minecraftcivilizations.mineroverhaul.scan.OreClusterCache;
import com.minecraftcivilizations.mineroverhaul.scan.WorldPreScanner;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import com.minecraftcivilizations.mineroverhaul.subclass.OreNaming;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

@CommandAlias("minerprospect")
@CommandPermission("mineroverhaul.admin")
public class RescanCommand extends BaseCommand {

    private final MinerOverhaul plugin;

    public RescanCommand(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onDefault(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/minerprospect <rescan|status|cancel|reload|where>", NamedTextColor.GRAY));
    }

    @Subcommand("status")
    public void onStatus(CommandSender sender) {
        WorldPreScanner scanner = plugin.getWorldPreScanner();
        int chunksScanned = scanner.progress().scannedCount();
        int radiusChunks = scanner.radiusChunks();
        int totalEstimate = (radiusChunks * 2 + 1) * (radiusChunks * 2 + 1);
        int clusters = scanner.clustersFound();
        sender.sendMessage(Component.text("Scan status: ", NamedTextColor.GRAY)
                .append(Component.text(scanner.isRunning() ? "running" : "idle",
                        scanner.isRunning() ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
                .append(Component.text(" — chunks: " + chunksScanned + " / ~" + totalEstimate
                        + " — clusters indexed (this session): " + clusters
                        + " — resident regions: " + plugin.getClusterCache().regionsResident(),
                        NamedTextColor.GRAY)));
    }

    @Subcommand("rescan")
    public void onRescan(CommandSender sender) {
        sender.sendMessage(Component.text("Wiping cluster cache and restarting scan...", NamedTextColor.YELLOW));
        plugin.getWorldPreScanner().resetAndRestart();
    }

    @Subcommand("cancel")
    public void onCancel(CommandSender sender) {
        plugin.getWorldPreScanner().stop();
        sender.sendMessage(Component.text("Scan stopped.", NamedTextColor.YELLOW));
    }

    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        plugin.getMinerConfig().reload();
        sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
    }

    @Subcommand("where")
    public void onWhere(Player player, String oreArg) {
        MinerSubclass sub = MinerSubclass.fromArg(oreArg);
        if (sub == null) {
            player.sendMessage(Component.text("Unknown subclass: " + oreArg, NamedTextColor.RED));
            return;
        }
        OreClusterCache cache = plugin.getClusterCache();
        List<Cluster> candidates = cache.findCandidates(sub,
                player.getLocation().getX(), player.getLocation().getZ(),
                plugin.getMinerConfig().prospectRadiusBlocks() * 4);

        if (candidates.isEmpty()) {
            player.sendMessage(Component.text("No clusters of " + sub.displayName()
                    + " indexed near you.", NamedTextColor.GRAY));
            return;
        }
        Cluster nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (Cluster c : candidates) {
            double sq = c.minSquaredDistanceTo(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ());
            if (sq < bestSq) { bestSq = sq; nearest = c; }
        }
        if (nearest == null) return;
        int[] coord = nearest.closestOutlineWorldCoord(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ());
        player.sendMessage(Component.text(
                "Nearest " + OreNaming.displayNameFor(nearest.primaryMaterial())
                        + " cluster: size=" + nearest.totalSize()
                        + " outline=" + nearest.outlinePacked().length
                        + " coord=(" + coord[0] + "," + coord[1] + "," + coord[2] + ")"
                        + " dist=" + Math.round(Math.sqrt(bestSq)),
                NamedTextColor.GRAY));
    }
}

package com.minecraftcivilizations.mineroverhaul.prospect;

import com.minecraftcivilizations.mineroverhaul.MinerConfig;
import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.scan.Cluster;
import com.minecraftcivilizations.mineroverhaul.scan.OreClusterCache;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import com.minecraftcivilizations.mineroverhaul.subclass.OreNaming;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

public class ProspectingService {

    private final MinerOverhaul plugin;

    public ProspectingService(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    public void scan(Player player, MinerSubclass subclass) {
        MinerConfig config = plugin.getMinerConfig();
        OreClusterCache cache = plugin.getClusterCache();
        Location loc = player.getLocation();
        int radius = config.prospectRadiusBlocks();

        List<Cluster> candidates = cache.findCandidates(subclass, loc.getX(), loc.getZ(), radius);

        Component prefix = Component.text("<" + subclass.displayName() + " Prospecting> ", subclass.chatColor());

        if (candidates.isEmpty()) {
            player.sendMessage(prefix.append(Component.text("Found nothing of interest.", NamedTextColor.GRAY)));
            return;
        }

        Cluster nearest = null;
        double nearestSq = Double.MAX_VALUE;
        for (Cluster c : candidates) {
            double sq = c.minSquaredDistanceTo(loc.getX(), loc.getY(), loc.getZ());
            if (sq < nearestSq) {
                nearestSq = sq;
                nearest = c;
            }
        }
        if (nearest == null) {
            player.sendMessage(prefix.append(Component.text("Found nothing of interest.", NamedTextColor.GRAY)));
            return;
        }

        double distance = Math.sqrt(nearestSq);
        String oreName = OreNaming.displayNameFor(nearest.primaryMaterial());

        Component body;
        if (distance > config.distTraces()) {
            body = Component.text("Found traces of " + oreName + ".", NamedTextColor.GRAY);
        } else if (distance > config.distSmallSample()) {
            body = Component.text("Found small sample of " + oreName + ".", NamedTextColor.GRAY);
        } else if (distance > config.distMediumSample()) {
            body = Component.text("Found medium sample of " + oreName + ".", NamedTextColor.GRAY);
        } else {
            String size = sizeLabel(nearest.totalSize(), config);
            body = Component.text(size + " " + oreName + " vein is detected close by.", NamedTextColor.GRAY);
        }

        player.sendMessage(prefix.append(body));
    }

    private static String sizeLabel(int totalSize, MinerConfig config) {
        if (totalSize < config.sizeTinyMax()) return "Tiny";
        if (totalSize < config.sizeSmallMax()) return "Small";
        if (totalSize < config.sizeAverageMax()) return "Average";
        if (totalSize < config.sizeLargeMax()) return "Large";
        return "Huge";
    }
}

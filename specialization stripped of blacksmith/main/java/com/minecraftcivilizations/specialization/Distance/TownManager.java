package com.minecraftcivilizations.specialization.Distance;

import com.minecraftcivilizations.specialization.Specialization;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class TownManager implements Listener {

    @Getter
    private static TownManager instance;

    private static final List<Town> towns = Collections.synchronizedList(new ArrayList<>());
    private static final Map<UUID, Location> playerSpawnLocations = new ConcurrentHashMap<>();

    private static final int TOWN_RADIUS = 150;
    private static final int MIN_BEDS = 5;

    public TownManager() {
        instance = this;
    }

    /*** EVENT: STORE PLAYER RESPAWN LOCATIONS ***/
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location spawnLocation = event.getRespawnLocation();
        if (spawnLocation != null) {
            playerSpawnLocations.put(event.getPlayer().getUniqueId(), spawnLocation);
        }

        // Async scan around respawn
        CompletableFuture.runAsync(() -> scanForTownsAroundLocationAsync(spawnLocation));
    }

    /*** ASYNC STARTUP SCAN: USE ALL OFFLINE PLAYER SPAWN LOCATIONS ***/
    public static void scanAllPlayersForTownsAsync() {
        Specialization.logger.info("Starting town scan...");
        long startTime = System.currentTimeMillis();

        CompletableFuture.runAsync(() -> {
            List<Location> allSpawnLocations = new ArrayList<>();

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                Location spawnLoc = player.getRespawnLocation(); // treat this as "bed"
                if (spawnLoc != null) {
                    allSpawnLocations.add(spawnLoc);
                    playerSpawnLocations.put(player.getUniqueId(), spawnLoc);
                }
            }

            List<Town> foundTowns = calculateTownsFromLocations(allSpawnLocations);

            // Push results to main thread
            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
                towns.clear();
                towns.addAll(foundTowns);

                long duration = System.currentTimeMillis() - startTime;
                Specialization.logger.info("Town scan complete! Found " + towns.size() + " towns in " + (duration / 1000.0) + "s");

                for (int i = 0; i < towns.size(); i++) {
                    Town t = towns.get(i);
                    Specialization.logger.info("Town " + (i + 1) + ": " + t.getBedCount() +
                            " beds at " + formatLocation(t.getCenterLocation()));
                }
            });
        });
    }

    /*** ASYNC SCAN AROUND A SPECIFIC LOCATION ***/
    private static void scanForTownsAroundLocationAsync(Location centerLocation) {
        if (centerLocation == null) return;

        CompletableFuture.runAsync(() -> {
            List<Location> nearbySpawns = new ArrayList<>();
            for (Location loc : playerSpawnLocations.values()) {
                if (loc.getWorld().equals(centerLocation.getWorld()) &&
                        loc.distanceSquared(centerLocation) <= TOWN_RADIUS * TOWN_RADIUS) {
                    nearbySpawns.add(loc);
                }
            }

            if (nearbySpawns.size() >= MIN_BEDS) {
                synchronized (towns) {
                    boolean townExists = false;
                    for (Town existingTown : towns) {
                        if (existingTown.getCenterLocation().getWorld().equals(centerLocation.getWorld()) &&
                                existingTown.getCenterLocation().distanceSquared(centerLocation) <= TOWN_RADIUS * TOWN_RADIUS) {
                            if (nearbySpawns.size() > existingTown.getBedCount()) {
                                existingTown.updateBeds(nearbySpawns);
                            }
                            townExists = true;
                            break;
                        }
                    }
                    if (!townExists) {
                        towns.add(new Town(calculateTownCenter(nearbySpawns), nearbySpawns));
                    }
                }
            }
        });
    }

    /*** MERGE CLOSE TOWNS ***/
    private static List<Town> calculateTownsFromLocations(List<Location> locations) {
        List<Town> townList = new ArrayList<>();
        Set<Location> unprocessed = new HashSet<>(locations);

        while (!unprocessed.isEmpty()) {
            Location current = unprocessed.iterator().next();
            List<Location> cluster = new ArrayList<>();

            Iterator<Location> iter = unprocessed.iterator();
            while (iter.hasNext()) {
                Location loc = iter.next();
                if (loc.getWorld().equals(current.getWorld()) &&
                        loc.distanceSquared(current) <= TOWN_RADIUS * TOWN_RADIUS) {
                    cluster.add(loc);
                    iter.remove();
                }
            }

            if (cluster.size() >= MIN_BEDS) {
                Location center = calculateTownCenter(cluster);
                townList.add(new Town(center, cluster));
            }
        }

        return townList;
    }

    /*** CALCULATE TOWN CENTER ***/
    private static Location calculateTownCenter(List<Location> locations) {
        if (locations.isEmpty()) return null;

        double totalX = 0, totalY = 0, totalZ = 0;
        World world = locations.get(0).getWorld();

        for (Location loc : locations) {
            totalX += loc.getX();
            totalY += loc.getY();
            totalZ += loc.getZ();
        }

        return new Location(world,
                totalX / locations.size(),
                totalY / locations.size(),
                totalZ / locations.size());
    }

    public static List<Town> getTowns() {
        return new ArrayList<>(towns);
    }

    public static Map<UUID, Location> getPlayerSpawnLocations() {
        return new HashMap<>(playerSpawnLocations);
    }

    public static String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f in %s",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }
}

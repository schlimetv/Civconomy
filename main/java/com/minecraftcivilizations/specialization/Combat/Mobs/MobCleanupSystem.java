package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class MobCleanupSystem extends BukkitRunnable {

    @Getter
    MobManager mobManager;

    private final World world;

    public MobCleanupSystem(MobManager mobManager) {
        this.mobManager = mobManager;
        this.world = Bukkit.getWorlds().getFirst();
    }


    @Override
    public void run() {
        cleanup();
    }

    private void cleanup() {if (world == null) return;

        // get all loaded chunks
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) return;

        // shuffle and pick up to 8 chunks
        List<Chunk> chunks = new ArrayList<>(Arrays.asList(loadedChunks));
        Collections.shuffle(chunks);
        chunks = chunks.subList(0, Math.min(16, chunks.size()));

        // collect all LivingEntities from selected chunks
        List<Mob> entities = new ArrayList<>();
        for (Chunk chunk : chunks) {
            for (Entity e : chunk.getEntities()) {
                if (e instanceof Mob le) entities.add(le);
            }
        }

        // shuffle and limit to 20
        Collections.shuffle(entities);
        int limit = Math.min(16, entities.size());

        int cleanedup = 0;
        for (int i = 0; i < limit; i++) {
            Mob entity = entities.get(i);
            MobVariation variation = getMobManager().getMobVariation(entity);
            if (variation == null || !variation.isDespawnFaraway()) continue;


            if(entity.getTarget()==null){
                entity.remove();
                cleanedup++;
//                Debug.broadcast("mob", "<red>mob despawned:</red> " + variation.getId());
            }

//            double cutoff_sq = variation.followRange * variation.followRange;
//            boolean playerNear = world.getPlayers().stream()
//                    .anyMatch(p -> p.getLocation().distanceSquared(entity.getLocation()) <= cutoff_sq);

//            if (!playerNear) {
//            }
        }

//        Debug.broadcast("mob", "<red>Cleaned up<white> " + cleanedup + "</white> variations");
    }

    public BukkitRunnable start() {
        int interval_ticks = 40;
        runTaskTimer(Specialization.getInstance(), interval_ticks, interval_ticks);
        return this;
    }
}

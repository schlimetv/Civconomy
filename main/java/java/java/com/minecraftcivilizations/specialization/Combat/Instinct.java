package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.LocatorBarManager;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class Instinct {

    private final Plugin plugin;
    private static final Map<UUID, Long> lastDetectionTime = new HashMap<>();

    public Instinct(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Called by HuntPlayerMobGoal when a mob starts breaking a block.
     * Alerts nearby Guardsman players by granting temporary visibility
     * of the mob through the locator bar system.
     */
    public static void onMobStartBreakingBlock(Monster mob) {
        if (!SpecializationConfig.getInstinctConfig().get("INSTINCT_ENABLED", Boolean.class)) {
            return;
        }

        // Prevent unbounded growth if scheduled cleanup tasks fail
        // (e.g. mob dies in an unloaded chunk before the task fires)
        if (lastDetectionTime.size() > 200) {
            long now = System.currentTimeMillis();
            lastDetectionTime.entrySet().removeIf(e -> now - e.getValue() > 10000);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip players in a different world before doing
            // any expensive skill lookups
            if (!player.getWorld().equals(mob.getWorld())) continue;

            // CustomPlayer can be null during the brief window between
            // a player joining and their data finishing loading.
            // This method runs every mob tick, so we must skip silently
            // rather than throwing — an NPE here spams the log and
            // wastes CPU building stack traces.
            CustomPlayer customPlayer = CoreUtil.getPlayer(player);
            if (customPlayer == null) continue;

            int guardsmanLevel = customPlayer.getSkillLevel(SkillType.GUARDSMAN);

            if (guardsmanLevel < 1) {
                continue;
            }

            double detectionRadius = getDetectionRadius(guardsmanLevel);

            // Use distanceSquared to avoid a sqrt calculation every
            // tick — just compare squared values instead
            if (player.getLocation().distanceSquared(mob.getLocation()) 
                    <= detectionRadius * detectionRadius) {
                applyInstinctDetection(mob, player);
            }
        }
    }

    private static double getDetectionRadius(int guardsmanLevel) {
        if (guardsmanLevel >= 3) {
            return SpecializationConfig.getInstinctConfig().get("INSTINCT_DETECTION_RADIUS_LEVEL_3", Double.class);
        } else if (guardsmanLevel >= 2) {
            return SpecializationConfig.getInstinctConfig().get("INSTINCT_DETECTION_RADIUS_LEVEL_2", Double.class);
        } else {
            return SpecializationConfig.getInstinctConfig().get("INSTINCT_DETECTION_RADIUS_LEVEL_1", Double.class);
        }
    }

    private static void applyInstinctDetection(LivingEntity mob, Player guardsman) {
        UUID mobId = mob.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        if (lastDetectionTime.containsKey(mobId) && 
            currentTime - lastDetectionTime.get(mobId) < 5000) {
            return;
        }

        lastDetectionTime.put(mobId, currentTime);

        int detectionDuration = SpecializationConfig.getInstinctConfig().get("INSTINCT_GLOW_DURATION_TICKS", Integer.class);

        LocatorBarManager locatorBarManager = LocatorBarManager.getInstance();
        if (locatorBarManager != null) {
            locatorBarManager.grantTemporaryVisibility(guardsman, mob, detectionDuration);
        }
        
        Bukkit.getScheduler().runTaskLater(Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("Specialization")), () -> {
            lastDetectionTime.remove(mobId);
        }, detectionDuration + 20L);
    }
}

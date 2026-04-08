package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Command.EmoteManager;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HungerSystem implements Listener {

    private final Specialization plugin;
    private final Map<UUID, Long> lastMoveTime = new HashMap<>();
    private final Map<UUID, PlayerActivity> playerActivity = new HashMap<>();
    private final Map<UUID, Double> playerHungerBuffer = new HashMap<>();

    private static final double  SPRINTING_DRAIN = SpecializationConfig.getHungerConfig().get("SPRINTING_DRAIN", Double.class);
    private static final double WALKING_DRAIN = SpecializationConfig.getHungerConfig().get("WALKING_DRAIN", Double.class);
    private static final double SWIMMING_DRAIN = SpecializationConfig.getHungerConfig().get("SWIMMING_DRAIN", Double.class);
    private static final double CROUCHING_DRAIN = SpecializationConfig.getHungerConfig().get("CROUCHING_DRAIN", Double.class);
    private static final double IDLE_DRAIN = SpecializationConfig.getHungerConfig().get("IDLE_DRAIN", Double.class);

    private static final long DRAIN_INTERVAL = SpecializationConfig.getHungerConfig().get("DRAIN_INTERVAL_IN_TICKS", Long.class);
    private static final long IDLE_CHECK_TIME = SpecializationConfig.getHungerConfig().get("IDLE_CHECK_TIME_IN_TICKS", Long.class);
    private final EmoteManager emoteCommand;

    public HungerSystem(Specialization plugin, EmoteManager emoteCommand) {
        this.plugin = plugin;
        this.emoteCommand = emoteCommand;
        startHungerDrainTask();
    }

    private enum PlayerActivity {
        SPRINTING,
        WALKING,
        CROUCHING,
        SWIMMING,
        IDLE
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        lastMoveTime.put(playerId, System.currentTimeMillis());

        PlayerActivity activity;
        if (player.isSprinting()) {
            activity = PlayerActivity.SPRINTING;
        } else if (player.isSneaking()) {
            activity = PlayerActivity.CROUCHING;
        } else if (player.isSwimming()) {
            activity = PlayerActivity.SWIMMING;
        }
        else {
            activity = PlayerActivity.WALKING;
        }

        playerActivity.put(playerId, activity);
    }

    @EventHandler
    public void onPlayerToggleSprint(PlayerToggleSprintEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (event.isSprinting()) {
            playerActivity.put(playerId, PlayerActivity.SPRINTING);
        } else {
            if (playerActivity.get(playerId) == PlayerActivity.SPRINTING) {
                playerActivity.put(playerId, PlayerActivity.WALKING);
            }
        }
        lastMoveTime.put(playerId, System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (event.isSneaking()) {
            playerActivity.put(playerId, PlayerActivity.CROUCHING);
        }
        lastMoveTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Clean up player data on quit to prevent memory leaks.
     * Without this, every player who ever joins leaves behind
     * three map entries (UUID → Long/Activity/Double) that never
     * get removed — on a server with high player turnover, these
     * accumulate indefinitely.
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastMoveTime.remove(playerId);
        playerActivity.remove(playerId);
        playerHungerBuffer.remove(playerId);
    }

    private void startHungerDrainTask() {
        // Runs SYNCHRONOUSLY — setFoodLevel() must be called on the
        // main thread.  The work is trivial (iterate online players,
        // do arithmetic) so there's no performance concern running
        // this on the main thread.
        new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();

                    // Determine if player is idle
                    Long lastMove = lastMoveTime.get(playerId);
                    boolean isIdle = lastMove == null ||
                            (currentTime - lastMove) > (IDLE_CHECK_TIME * 50);

                    PlayerActivity activity;
                    if (isIdle) {
                        activity = PlayerActivity.IDLE;
                    } else {
                        activity = playerActivity.getOrDefault(playerId, PlayerActivity.IDLE);
                    }

                    drainHunger(player, activity);
                }
            }
        }.runTaskTimer(plugin, 0, DRAIN_INTERVAL * 2);
    }

    private void drainHunger(Player player, PlayerActivity activity) {
        if(player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR){
            return;
        }
        // Prevent hunger drain while sleeping
        if (player.isSleeping()){
            return;
        }
        if (emoteCommand.isPlayerSitting(player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        double currentFoodLevel = player.getFoodLevel();
        double drainAmount = getDrainAmount(activity);

        // Get or initialize the player's hunger buffer
        double hungerBuffer = playerHungerBuffer.getOrDefault(playerId, 0.0);

        // Add the drain amount to the buffer
        hungerBuffer += drainAmount;

        // Check if we have accumulated enough to drain at least 1 hunger point
        if (hungerBuffer >= 1.0) {
            int hungerPointsToDrain = (int) hungerBuffer;
            double newFoodLevel = Math.max(0, currentFoodLevel - hungerPointsToDrain);
            player.setFoodLevel((int) newFoodLevel);

            // Subtract the drained amount from buffer, keeping the remainder
            hungerBuffer -= hungerPointsToDrain;
        }

        // Store the updated buffer
        playerHungerBuffer.put(playerId, hungerBuffer);
    }


    private double getDrainAmount(PlayerActivity activity) {
        switch (activity) {
            case SPRINTING:
                return SPRINTING_DRAIN;
            case WALKING:
                return WALKING_DRAIN;
            case CROUCHING:
                return CROUCHING_DRAIN;
            case SWIMMING:
                return SWIMMING_DRAIN;
            default:
                return IDLE_DRAIN;
        }
    }


    /**
     * force feed players as guardsmen
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClickPlayer(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target)) return;

        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!handItem.getType().isEdible()) return;

        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        // Guardsman/healer check
        if ((cPlayer.getSkillLevel(SkillType.GUARDSMAN) <= 0) && (cPlayer.getSkillLevel(SkillType.HEALER) <= 2)) return;

        // Force feed: add 1 hunger
        // Only feed if target is not full
        if (!(target.getFoodLevel() < 20)) {
            PlayerUtil.message(player, target.getName() + " can't handle more food", 1);
            return;
        }

        if(PlayerUtil.isOnCooldown(player, "feedother")){
            return;
        }
        int nutrition = ItemStackUtils.getFoodNutrition(handItem.getType());
        int increase = Math.max(1, ((nutrition / 2)));

        target.setFoodLevel(Math.min(target.getFoodLevel() + increase, 20));
        // Play swing animation
        ItemStack held = player.getInventory().getItemInMainHand();

        player.swingHand(EquipmentSlot.HAND);

        float pitch = 0.8f + (float) (Math.random() * 0.4f); // 0.8–1.2
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 1f, pitch);
        target.getWorld().spawnParticle(
                Particle.ITEM,
                target.getEyeLocation(),
                8,
                0.2, 0, 0.5,
                0,
                held
        );
        // Consume one item from hand
        handItem.setAmount(handItem.getAmount() - 1);
        PlayerUtil.setCooldown(player, "feedother", 4);
//        PlayerUtil.message(player, "Force fed <gold>" + target.getName(), 1);
//        PlayerUtil.message(target, "<gold>" + target.getName() + "</gold>force fed you", 1);
    }


}
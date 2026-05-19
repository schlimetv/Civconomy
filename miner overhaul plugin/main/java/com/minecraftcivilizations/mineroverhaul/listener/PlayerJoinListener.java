package com.minecraftcivilizations.mineroverhaul.listener;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.data.SubclassData;
import com.minecraftcivilizations.mineroverhaul.subclass.SubclassPromptBuilder;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {

    private final MinerOverhaul plugin;

    public PlayerJoinListener(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        pdm.onJoin(player);

        // Re-deliver missed level-up prompt if the player advanced miner level while offline
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            SubclassData data = pdm.getCached(player.getUniqueId());
            if (data == null || data.hasSubclass()) return;
            CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
            if (cp == null) return;
            int minerLevel = cp.getSkillLevel(SkillType.MINER);
            if (minerLevel >= 1 && minerLevel > data.getLastPromptedLevel()) {
                data.setLastPromptedLevel(minerLevel);
                pdm.save(player.getUniqueId());
                player.sendMessage(SubclassPromptBuilder.buildLevelUpPrompt(plugin.getMinerConfig(), minerLevel));
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPlayerDataManager().onQuit(event.getPlayer());
    }
}

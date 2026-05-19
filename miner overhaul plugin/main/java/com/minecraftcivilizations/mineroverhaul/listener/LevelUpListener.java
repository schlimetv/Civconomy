package com.minecraftcivilizations.mineroverhaul.listener;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.data.SubclassData;
import com.minecraftcivilizations.mineroverhaul.subclass.SubclassPromptBuilder;
import com.minecraftcivilizations.specialization.Events.SkillLevelChangeEvent;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LevelUpListener implements Listener {

    private final MinerOverhaul plugin;

    public LevelUpListener(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSkillLevelChange(SkillLevelChangeEvent event) {
        if (event.getSkillType() != SkillType.MINER) return;
        if (!event.isLevelUp()) return;

        Player player = event.getPlayer();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        SubclassData data = pdm.getOrCreate(player.getUniqueId());
        if (data.hasSubclass()) return;

        int newLevel = event.getNewLevel();
        data.setLastPromptedLevel(newLevel);
        pdm.save(player.getUniqueId());

        // Defer to next tick so prompt appears after the spec plugin's cheer message
        Bukkit.getScheduler().runTask(plugin,
                () -> player.sendMessage(SubclassPromptBuilder.buildLevelUpPrompt(plugin.getMinerConfig(), newLevel)));
    }
}

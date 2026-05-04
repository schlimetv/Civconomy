package com.minecraftcivilizations.specialization.Player;

import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class TeamManager {

    private static final Map<SkillType, Team> skillTeams = new HashMap<>();

    private static final Map<SkillType, ChatColor> SKILL_COLORS = Map.of(
            SkillType.FARMER, ChatColor.GREEN,
            SkillType.BUILDER, ChatColor.GOLD,
            SkillType.MINER, ChatColor.GRAY,
            SkillType.HEALER, ChatColor.LIGHT_PURPLE,
            SkillType.LIBRARIAN, ChatColor.BLUE,
            SkillType.GUARDSMAN, ChatColor.RED,
            SkillType.BLACKSMITH, ChatColor.DARK_GRAY
    );

    public static void initializeTeams() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for(SkillType skillType : SkillType.values()) {
            Team team;
            try {
                team = scoreboard.registerNewTeam(skillType.name());
                team.setColor(SKILL_COLORS.get(skillType));
            }catch (IllegalArgumentException ignored) {
                team = scoreboard.getTeam(skillType.name());
            }
            skillTeams.put(skillType, team);
        }
    }

    public static void setTeam(Player player){
        Skill bestSkill = CoreUtil.getPlayer(player).getSkills().stream().max(Comparator.comparingDouble(Skill::getXp)).orElse(null);
        if(bestSkill == null) return;
        SkillType skill = bestSkill.getSkillType();
        if(!skillTeams.get(skill).hasPlayer(player)) {
            Bukkit.getScheduler().runTask(Specialization.getInstance(), ()-> {
                skillTeams.values().forEach(team -> {
                    team.removeEntry(player.getName());
                });
                skillTeams.get(skill).addPlayer(player);
            });
        }
    }

}

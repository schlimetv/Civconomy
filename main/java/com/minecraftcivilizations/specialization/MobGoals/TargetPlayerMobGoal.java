package com.minecraftcivilizations.specialization.MobGoals;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Piglin;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class TargetPlayerMobGoal implements Goal<Monster> {
    public static final GoalKey<Monster> KEY = GoalKey.of(Monster.class, new NamespacedKey(Specialization.getInstance(),"monster_target_player"));
    private Monster monster;

    public TargetPlayerMobGoal(Monster monster){
        this.monster = monster;
    }

    @Override
    public boolean shouldActivate() {
        return monster.getTarget() == null && !monster.getWorld().isDayTime() && !(monster instanceof Enderman) && !(monster instanceof Piglin);
    }

    @Override
    public void start() {
        int targetRange = SpecializationConfig.getMobConfig().get("MOB_RULE_TARGET_RANGE", Integer.class);
        monster.getLocation().getNearbyPlayers(targetRange).stream()
                .min((p1, p2) -> {
                    CustomPlayer player1 = CoreUtil.getPlayer(p1);
                    CustomPlayer player2 = CoreUtil.getPlayer(p2);
                    // First, check the priority
                    if (player1.getSkillLevel(SkillType.GUARDSMAN) > player2.getSkillLevel(SkillType.GUARDSMAN)) {
                        return -1; // p1 has priority, so it comes first
                    }
                    if (player1.getSkillLevel(SkillType.GUARDSMAN) < player2.getSkillLevel(SkillType.GUARDSMAN)) {
                        return 1;  // p2 has priority, so it comes first
                    }
                    // If priorities are the same, compare by distance
                    if(monster.getLocation().getWorld().equals(p1.getLocation().getWorld()) && monster.getLocation().getWorld().equals(p2.getLocation().getWorld())){
                        return Double.compare(p1.getLocation().distanceSquared(monster.getLocation()), p2.getLocation().distanceSquared(monster.getLocation()));
                    }
                    return 0;
                })
                .ifPresent(player -> monster.setTarget(player));
    }

    @Override
    public GoalKey<@NotNull Monster> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.TARGET, GoalType.MOVE);
    }
}
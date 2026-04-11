package com.minecraftcivilizations.specialization.Data;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DataManager {

    @Getter
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void startSaver(Specialization plugin) {
        long initialDelay = getInitialDelayUntilNext10Min();
        long period = 10 * 60; // seconds
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, save_runnable, initialDelay*20, period*20); //Thread safe scheduler
        //old scheduler:
        //scheduler.scheduleAtFixedRate(runnable, initialDelay, period, TimeUnit.SECONDS);
    }

    private static final Runnable save_runnable = () -> {

        String timestamp = java.time.ZonedDateTime.now().toLocalTime().toString().substring(0, 8);
        for(Player player : Bukkit.getOnlinePlayers()){
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
            String msg = "Skills being saved for "+player.getName()+":";
            for(SkillType type : SkillType.values()){
                double xp = customPlayer.getSkill(type).getXp();
                String m = (xp<=0?"gray":"blue");
                msg += "\n<gray>"+type.name()+" <dark_gray>=</dark_gray> <"+m+">"+xp+"</"+m+">";
            }
            msg +="</gray>\n" +
                    "<gold>If you are suspicious of any xp loss,\nplease compare this to <bold>/class</bold></gold>\n"
            + timestamp;
            Component hover_msg = MiniMessage.miniMessage().deserialize(msg);
            Component final_msg = MiniMessage.miniMessage().deserialize("<underlined>[Saved Data]...</underlined>")
                    .hoverEvent(HoverEvent.showText(hover_msg)).clickEvent(ClickEvent.suggestCommand("/class"));
            PlayerUtil.message(player, final_msg);
        }

        // TODO Future note for CivCore : Make sure you handle exceptions with e.printStackTrace(); so we can figure out when things go wrong.
        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().saveAll();
    };

    // this is done so analytics are exactly every 10 min, so its clean
    private static long getInitialDelayUntilNext10Min() {
        LocalDateTime now = LocalDateTime.now();
        int minute = now.getMinute();
        int nextInterval = ((minute / 10) + 1) * 10;

        LocalDateTime nextTime = now.withMinute(0).withSecond(0).withNano(0).plusMinutes(nextInterval);

        Duration duration = Duration.between(now, nextTime);
        return duration.getSeconds() + 1;
    }
}

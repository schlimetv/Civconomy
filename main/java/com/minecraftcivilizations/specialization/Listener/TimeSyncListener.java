package com.minecraftcivilizations.specialization.Listener;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public class TimeSyncListener implements PluginMessageListener {
    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] bytes) {
        if(channel.equals("civlabs:weathersync")){
            int time = ByteBuffer.wrap(bytes).getInt();
            player.getServer().getWorlds().forEach(world -> world.setTime(time));
        }
    }
}

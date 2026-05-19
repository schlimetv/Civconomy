package com.minecraftcivilizations.mineroverhaul.listener;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkLoadListener implements Listener {

    private final MinerOverhaul plugin;

    public ChunkLoadListener(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        if (plugin.getWorldPreScanner() == null) return;
        plugin.getWorldPreScanner().scanFreshChunk(event.getChunk());
    }
}

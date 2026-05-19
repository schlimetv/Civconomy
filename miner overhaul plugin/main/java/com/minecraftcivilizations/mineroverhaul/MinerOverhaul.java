package com.minecraftcivilizations.mineroverhaul;

import co.aikar.commands.PaperCommandManager;
import com.minecraftcivilizations.mineroverhaul.command.RescanCommand;
import com.minecraftcivilizations.mineroverhaul.command.SubclassCommand;
import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.listener.ChunkLoadListener;
import com.minecraftcivilizations.mineroverhaul.listener.LevelUpListener;
import com.minecraftcivilizations.mineroverhaul.listener.PlayerJoinListener;
import com.minecraftcivilizations.mineroverhaul.listener.ProspectListener;
import com.minecraftcivilizations.mineroverhaul.prospect.ProspectingService;
import com.minecraftcivilizations.mineroverhaul.scan.ChunkScanner;
import com.minecraftcivilizations.mineroverhaul.scan.OpenClusterTable;
import com.minecraftcivilizations.mineroverhaul.scan.OreClusterCache;
import com.minecraftcivilizations.mineroverhaul.scan.ScanProgress;
import com.minecraftcivilizations.mineroverhaul.scan.WorldPreScanner;
import org.bukkit.plugin.java.JavaPlugin;

public class MinerOverhaul extends JavaPlugin {

    private static MinerOverhaul instance;

    private MinerConfig minerConfig;
    private PlayerDataManager playerDataManager;
    private OreClusterCache clusterCache;
    private ProspectingService prospectingService;
    private ScanProgress scanProgress;
    private ChunkScanner chunkScanner;
    private OpenClusterTable openClusterTable;
    private WorldPreScanner worldPreScanner;
    private PaperCommandManager commandManager;

    public static MinerOverhaul getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        minerConfig = new MinerConfig(this);
        minerConfig.reload();

        playerDataManager = new PlayerDataManager(this);
        clusterCache = new OreClusterCache(this, minerConfig.scanRegionCacheSize());
        prospectingService = new ProspectingService(this);

        scanProgress = new ScanProgress(this);
        chunkScanner = new ChunkScanner(this);
        openClusterTable = new OpenClusterTable(this);
        worldPreScanner = new WorldPreScanner(this, chunkScanner, scanProgress, openClusterTable);

        getServer().getPluginManager().registerEvents(new LevelUpListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ProspectListener(this), this);
        getServer().getPluginManager().registerEvents(new ChunkLoadListener(this), this);

        commandManager = new PaperCommandManager(this);
        commandManager.registerCommand(new SubclassCommand(this));
        commandManager.registerCommand(new RescanCommand(this));

        if (minerConfig.scanRunOnEnable()) {
            // Defer to next tick so the worlds are ready
            getServer().getScheduler().runTask(this, () -> worldPreScanner.start());
        }

        getLogger().info("MinerOverhaul enabled.");
    }

    @Override
    public void onDisable() {
        if (worldPreScanner != null) worldPreScanner.stop();
        if (playerDataManager != null) playerDataManager.flushAll();
        if (clusterCache != null) clusterCache.flushAll();
        if (scanProgress != null) scanProgress.save();
        if (commandManager != null) commandManager.unregisterCommands();
        getLogger().info("MinerOverhaul disabled.");
    }

    public MinerConfig getMinerConfig() { return minerConfig; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public OreClusterCache getClusterCache() { return clusterCache; }
    public ProspectingService getProspectingService() { return prospectingService; }
    public ScanProgress getScanProgress() { return scanProgress; }
    public WorldPreScanner getWorldPreScanner() { return worldPreScanner; }
}

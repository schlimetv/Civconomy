package com.minecraftcivilizations.specialization;

import co.aikar.commands.PaperCommandManager;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.destroystokyo.paper.profile.CraftPlayerProfile;
import com.minecraftcivilizations.specialization.Combat.*;
import com.minecraftcivilizations.specialization.Command.*;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.CustomItem.IronBloomSystem;
import com.minecraftcivilizations.specialization.Data.DataManager;
import com.minecraftcivilizations.specialization.Distance.TownManager;
import com.minecraftcivilizations.specialization.Golem.IronGolemListener;
import com.minecraftcivilizations.specialization.Golem.IronGolemManager;
import com.minecraftcivilizations.specialization.Beacon.BeaconListener;
import com.minecraftcivilizations.specialization.Beacon.BeaconManager;
import com.minecraftcivilizations.specialization.Listener.Blocks.AutoCrafterListener;
import com.minecraftcivilizations.specialization.Listener.Blocks.FarmlandFallFix;
import com.minecraftcivilizations.specialization.Listener.Blocks.ReinforcementProtectionListener;
import com.minecraftcivilizations.specialization.Listener.BurnListener;
import com.minecraftcivilizations.specialization.Listener.ItemFloatListener;
import com.minecraftcivilizations.specialization.Listener.Player.*;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.BreakBlockListener;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.PlayerMineListener;
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.PlaceBlockListener;
import com.minecraftcivilizations.specialization.Listener.Player.Interactions.*;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.CraftingListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.ForgeSmithingSoundListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.FurnaceListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.FurnaceSoundListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.SmithingAssemblyListener;
import com.minecraftcivilizations.specialization.Listener.Player.Inventories.StonecutterListener;
import com.minecraftcivilizations.specialization.Listener.RepairingListener;
import com.minecraftcivilizations.specialization.Listener.TimeSyncListener;
import com.minecraftcivilizations.specialization.Listener.ClassXpBookListener;
import com.minecraftcivilizations.specialization.Listener.XpTransferBookListener;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Player.LocalNameGenerator;
import com.minecraftcivilizations.specialization.Player.PreJoinEventListener;
import com.minecraftcivilizations.specialization.Recipe.Blueprints;
import com.minecraftcivilizations.specialization.Recipe.RecipeBlocker;
import com.minecraftcivilizations.specialization.Recipe.Recipes;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.SmartEntity.SmartEntityManager;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.StaffTools.DebugListenCommand;
import com.minecraftcivilizations.specialization.util.LocatorBarManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.mojang.authlib.GameProfile;
import lombok.Getter;
import minecraftcivilizations.com.minecraftCivilizationsCore.Component.ComponentUtils;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Difficulty;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class Specialization extends JavaPlugin {

    public final static String TITLE = "<#334422>[<#445533>CivLabs</#445533>]";
    public static Logger logger;
    public ReviveListener reviveListener;
    //Holder for transient player data such as cooldowns
    static Map<UUID, PlayerUtil> playerUtilMap = new HashMap<>();
    PaperCommandManager commandManager;
    @Getter
    private LocalNameGenerator localNameGenerator;
    private Debug debug;
    private PhantomRideListener phantomRideListener;
    //    private EmoteListener emoteListener;
    @Getter
    public LocalChat localChat;
    //follow this pattern from now on
    @Getter
    private SmartEntityManager smart_entity_manager;
    @Getter
    private CustomItemManager customItemManager;
    @Getter
    private CombatManager combatManager;
    @Getter
    private BlacksmithArmorTrim armorTrimSystem;
    @Getter
    private PVPManager pvpManager;
    private XPMonitoringCommand xpMonitoringCommand;

    @Getter
    private PlayerDownedListener playerDownedListener;
    private RecipeBlocker recipeBlocker;
    private EmoteManager emoteManager;
    @Getter
    private IronGolemManager ironGolemManager;
    @Getter
    private BeaconManager beaconManager;

    public static void notify(Player player, String msg) {
        message(player, msg);
    }

    public static void message(Player player, String msg) {
        PlayerUtil.message(player, msg, 0);
    }

    public static void message(Player player, Component msg) {
        PlayerUtil.message(player, msg, 0);
    }


    public static Specialization getInstance() {
        return getPlugin(Specialization.class);
    }

    @Override
    public void onEnable() {
        logger = getLogger();

        Skill.InitCacheXPLevelFormula();
        debug = new Debug(this);
        saveResource("first_names.txt", true);
        saveResource("last_names.txt", true);
        SpecializationConfig.initialize();
        // TODO PDC-xp-hotfix
        //  Skill.InitializeSkillKeys(this);


        localChat = new LocalChat();
        playerDownedListener = new PlayerDownedListener(this);
        reviveListener = new ReviveListener(playerDownedListener);
        smart_entity_manager = new SmartEntityManager(this);
        customItemManager = new CustomItemManager(this);
        customItemManager.initializeCustomItems();
        phantomRideListener = new PhantomRideListener(this);
        xpMonitoringCommand = new XPMonitoringCommand();
        emoteManager = new EmoteManager(customItemManager, this);
        pvpManager = new PVPManager(playerDownedListener, this);
        recipeBlocker = new RecipeBlocker();
        armorTrimSystem = new BlacksmithArmorTrim();
//      emoteListener = new EmoteListener(this);

        getServer().getMessenger().registerIncomingPluginChannel(this, "civlabs:weathersync", new TimeSyncListener());

        //commands registered here
        setupCommands();

        getServer().getPluginManager().registerEvents(new PlayerMineListener(), this);
        getServer().getPluginManager().registerEvents(new BreakBlockListener(), this);
        getServer().getPluginManager().registerEvents(new PlaceBlockListener(), this);
        getServer().getPluginManager().registerEvents(new RightClickListener(), this);
        getServer().getPluginManager().registerEvents(new BurnListener(), this);
        getServer().getPluginManager().registerEvents(new ExplodeListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractListener(), this);
        getServer().getPluginManager().registerEvents(new HoeTillingListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerInteractEntityListener(), this);
        getServer().getPluginManager().registerEvents(new FishingListener(), this);
        getServer().getPluginManager().registerEvents(new FurnaceListener(), this);
        getServer().getPluginManager().registerEvents(new IronBloomSystem(), this);
        getServer().getPluginManager().registerEvents(new FurnaceSoundListener(this), this);
        getServer().getPluginManager().registerEvents(new ForgeSmithingSoundListener(this), this);
        getServer().getPluginManager().registerEvents(new SmithingAssemblyListener(), this);
        combatManager = new CombatManager(this); // Guardsman Damage Output
        new FoodInteractionListener(this);
        new ItemFloatListener(this);
        getServer().getPluginManager().registerEvents(new HungerSystem(this, emoteManager), this);
        getServer().getPluginManager().registerEvents(new LeashListener(), this);
        getServer().getPluginManager().registerEvents(new BedListener(), this);
        getServer().getPluginManager().registerEvents(new LocatorBarManager(this), this);
        getServer().getPluginManager().registerEvents(new ReinforcementProtectionListener(), this);
        getServer().getPluginManager().registerEvents(new FarmlandFallFix(), this);
        getServer().getPluginManager().registerEvents(new PreJoinEventListener(), this);
        getServer().getPluginManager().registerEvents(new StonecutterListener(this), this);
        getServer().getPluginManager().registerEvents(new CraftingListener(this), this);
        getServer().getPluginManager().registerEvents(new AutoCrafterListener(), this);

        new TownManager();
        getServer().getPluginManager().registerEvents(new MoveListener(), this);
        getServer().getPluginManager().registerEvents(new CrossBowListener(), this);
        getServer().getPluginManager().registerEvents(new LocalChat(), this);
        getServer().getPluginManager().registerEvents(new PatDown(), this);
        getServer().getPluginManager().registerEvents(new XpTransferBookListener(), this);

        // Iron Golem whitelist system
        ironGolemManager = new IronGolemManager(this);
        IronGolemListener ironGolemListener = new IronGolemListener(this, ironGolemManager);
        getServer().getPluginManager().registerEvents(ironGolemListener, this);

        // Beacon whitelist system
        beaconManager = new BeaconManager(this);
        BeaconListener beaconListener = new BeaconListener(this, beaconManager);
        getServer().getPluginManager().registerEvents(beaconListener, this);

        // Class XP Book system
        ClassXpBookListener.init();
        getServer().getPluginManager().registerEvents(new ClassXpBookListener(), this);
        getServer().getPluginManager().registerEvents(new RepairingListener(), this);

        getServer().getPluginManager().registerEvents(phantomRideListener, this);
        getServer().getPluginManager().registerEvents(playerDownedListener, this);
        getServer().getPluginManager().registerEvents(reviveListener, this);
        //getServer().getPluginManager().registerEvents(recipeBlocker, this);

        //town data does not need to wait anymore
        TownManager.scanAllPlayersForTownsAsync();


        for(World world : Bukkit.getWorlds()) {
            world.setGameRule(GameRule.SPAWN_RADIUS, 350);
            world.setDifficulty(Difficulty.HARD);
            world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRule.NATURAL_REGENERATION, false);
            world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRule.LOCATOR_BAR, true);
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            world.setGameRule(GameRule.MINECART_MAX_SPEED, 24);
        }

        //global game rules
        Bukkit.getWorlds().forEach(w -> w.setGameRule(GameRule.NATURAL_REGENERATION, false));
        Bukkit.getWorlds().forEach(w -> w.setGameRule(GameRule.DO_TRADER_SPAWNING, false));

        Recipes.init();
        Blueprints.init();
        XpGainMonitor.init();

        Bukkit.updateRecipes();


        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().setCustomPlayerClass(CustomPlayer.class);

        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().setOnPrePlayerJoin(playerJoinEvent -> {
            try {
                CustomPlayer load = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().load(playerJoinEvent.getUniqueId());
                Component localName;
                String real_name = playerJoinEvent.getName();
                if (load != null) {
                    MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().addCustomPlayer(load);
                    localName = load.getName();
                } else {
                    MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().addCustomPlayer(new CustomPlayer(playerJoinEvent.getUniqueId()));
                    CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(playerJoinEvent.getUniqueId());
                    customPlayer.setName(Component.text(localNameGenerator.nextName()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
                    double height = Skill.mapValue(Math.random(), 0.0, 1.0, .85, 1.0);
                    customPlayer.setHeight(height);
                    localName = customPlayer.getName();
                }


                CraftPlayerProfile profile = (CraftPlayerProfile) playerJoinEvent.getPlayerProfile();
                GameProfile gameProfile = profile.getGameProfile();
                Field ff = gameProfile.getClass().getDeclaredField("name");
                ff.setAccessible(true);
                ff.set(gameProfile, ComponentUtils.serializeComponentAsString(localName));

                UUID uniqueId = playerJoinEvent.getUniqueId();
                if (!playerUtilMap.containsKey(uniqueId)) {
                    playerUtilMap.put(uniqueId, new PlayerUtil(uniqueId));
                }

                Debug.broadcast("login", ChatColor.YELLOW + real_name + " has joined the server (" + localName + ")");

            } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.severe("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
                e.printStackTrace();
            }
        });

        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().setOnPlayerJoin(playerJoinEvent -> {
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(playerJoinEvent.getUniqueId());
            applyCustomName(playerJoinEvent.getPlayer(), customPlayer.getName());


            // TODO PDC-xp-hotfix for later if we need it
            //  customPlayer.reloadSkillsXp(playerJoinEvent.getPlayer());

            // Assign player to team based on their highest skill
//            TeamManager.setTeam(playerJoinEvent.getPlayer());

        });

        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().setOnPlayerQuit(playerQuitEvent -> {
            // Save downed state to restore on rejoin, then clean up current session state
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(playerQuitEvent.getPlayer().getUniqueId());
            if (customPlayer != null) {
                if (customPlayer.isDowned()) {
                    // Save that they were downed when they logged out
                    customPlayer.setWasDownedOnLogout(true);
                    // Clean up current session state to prevent infinite death loop
                    customPlayer.setDowned(false);
//                    playerDeathListener.setDowned(playerQuitEvent.getPlayer(), true);
                } else {
                    // They weren't downed, so clear the flag
                    customPlayer.setWasDownedOnLogout(false);
                }
            }
            MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().removeCustomPlayer(playerQuitEvent.getPlayer().getUniqueId());
        });

        for (Player player : Bukkit.getOnlinePlayers()) {
            CustomPlayer loadedPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().load(player.getUniqueId());
            if (loadedPlayer != null) {
                MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().addCustomPlayer(loadedPlayer);
            }
        }

        combatManager.initialize();

        DataManager.startSaver(this);
        ReinforcementManager.startReinforcement();

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        for (Player p : Bukkit.getOnlinePlayers()) {
            phantomRideListener.PhantomStateSave(p);
        }
        emoteManager.shutdown();
        smart_entity_manager.shutdown();
        ironGolemManager.shutdown();
        beaconManager.shutdown();
        DataManager.getScheduler().shutdown();
        MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().saveAll();
        XpGainMonitor.saveConfigToDisk();
    }

    private void setupCommands() {

        try {
            localNameGenerator = new LocalNameGenerator(this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Cleans up optional names held in temp reserves if a name was chosen
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            localNameGenerator.cleanupExpiredTemps();
        }, 0L, 1 * 60 * 20L); // every 1 minute


        commandManager = new PaperCommandManager(this);

        // --- TAB COMPLETIONS ---
        commandManager.getCommandCompletions().registerCompletion("classes", c ->
                Arrays.stream(SkillType.values())
                        .map(Enum::name)
                        .collect(Collectors.toList())
        );

        // somewhere during plugin init
        commandManager.getCommandCompletions().registerCompletion("monitorTypes", c ->
                Arrays.asList("threshold", "cooldown")
        );


        // Register tab completion for all custom items
        commandManager.getCommandCompletions().registerCompletion("customitems", c ->
                new ArrayList<>(customItemManager.getCustomItemIds())
        );

        commandManager.registerCommand(new ClassCommand());
        commandManager.registerCommand(new SetXpCommand());
        commandManager.registerCommand(new SetLoreCommand());
        commandManager.registerCommand(new TownsCommand());
        commandManager.registerCommand(new SuicideCommand(playerDownedListener, pvpManager, this));
        commandManager.registerCommand(new RestoreHealthCommand());
        commandManager.registerCommand(new NotifyRestartCommand());
        commandManager.registerCommand(new RecipesCommand());
        commandManager.registerCommand(new PurgeGoldenApplesCommand());
        commandManager.registerCommand(new RandomNameBulkTestCommand());
        commandManager.registerCommand(new RerollNameCommand(localNameGenerator));
        commandManager.registerCommand(new NameChoiceCommand(localNameGenerator));
//        commandManager.registerCommand(new XPLeaderboardCommand()); // (Loads player configs and causes XP Loss) TODO DO NOT ENABLE UNTIL FIXED
        commandManager.registerCommand(new CustomItemCommand(customItemManager));
        commandManager.registerCommand(new SudoChatCommand(localChat));
        commandManager.registerCommand(new XPMonitoringCommand());
        commandManager.registerCommand(new RecipeRefreshCommand());
        commandManager.registerCommand(emoteManager);
        new DebugListenCommand(commandManager);


    }


    public void applyCustomName(Player player, Component name) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore
                .getInstance()
                .getCustomPlayerManager()
                .getCustomPlayer(player.getUniqueId());

        // Update the CustomPlayer's stored name
        customPlayer.setName(name);

        // Send the packet to all online players to update the display name
        PacketContainer packet = createChangeNamePacket(player.getUniqueId(), name);
        for (Player p : Bukkit.getOnlinePlayers()) {
            ProtocolLibrary.getProtocolManager().sendServerPacket(p, packet);
            // Also send each other player's custom name to the target player
            CustomPlayer otherPlayer = (CustomPlayer) MinecraftCivilizationsCore
                    .getInstance()
                    .getCustomPlayerManager()
                    .getCustomPlayer(p.getUniqueId());
            if (otherPlayer != null) {
                ProtocolLibrary.getProtocolManager().sendServerPacket(player,
                        createChangeNamePacket(p.getUniqueId(), otherPlayer.getName()));
            }
        }

        // Update the internal GameProfile to ensure name persists correctly
        try {
            ServerPlayer profile = ((CraftPlayer) player).getHandle();
            GameProfile gameProfile = profile.getGameProfile();
            Field nameField = gameProfile.getClass().getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(gameProfile, ComponentUtils.serializeComponentAsString(name));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        // Force client to refresh player to avoid caching issues
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            for (Player all : Bukkit.getOnlinePlayers()) {
                all.hidePlayer(Specialization.getInstance(), player);
                all.showPlayer(Specialization.getInstance(), player);
            }
        });
    }


    private PacketContainer createChangeNamePacket(UUID uuid, Component name) {
        PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.PLAYER_INFO);

        // Update the display name only
        packet.getPlayerInfoActions().write(0,
                Collections.singleton(EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME));

        WrappedGameProfile profile = new WrappedGameProfile(uuid, ComponentUtils.serializeComponentAsString(name));
        WrappedChatComponent nameComponent = WrappedChatComponent.fromJson(
                JSONComponentSerializer.json().serialize(name)
        );

        List<PlayerInfoData> playerInfoData = List.of(
                new PlayerInfoData(profile, 0, EnumWrappers.NativeGameMode.SURVIVAL, nameComponent)
        );
        packet.getPlayerInfoDataLists().write(1, playerInfoData);

        return packet;
    }


    public Debug getDebugUtils() {
        return debug;
    }

    public static PlayerUtil getPlayerUtil(UUID uniqueId) {
        return playerUtilMap.get(uniqueId);
    }


}

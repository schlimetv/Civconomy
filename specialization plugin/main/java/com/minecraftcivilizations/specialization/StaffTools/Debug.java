package com.minecraftcivilizations.specialization.StaffTools;

import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Utilies that aim to help with server development and debugging
 * Any debug channel can be created on the fly
 * These channels can be listened to with /debug on channel_name
 * see: DebugListenCommand.java for commands
 * @author alectriciti
 */
public class Debug implements Listener {

    public static String TITLE = ChatColor.DARK_GRAY + "[debug]";
    /**
     * intended use:
     * /debug add <debug_channel>
     * /debug remove <debug_channel>
     */

    // debug_channel -> List of Players registered to that channel
    private Map<String, Set<UUID>> debug_listening = new HashMap<String, Set<UUID>>();
    private Map<UUID, List<String>> listening_channels = new HashMap<UUID, List<String>>(); //used specifically for tab completion
    private List<String> debug_channels = new ArrayList<String>(); //used by command suggestions

    // Add this field to the class (non-static)
    private final NamespacedKey debug_channels_key;


    public static boolean DEBUG_ENABLED = false;


    public Debug(Specialization plugin){
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.debug_channels_key = new NamespacedKey(plugin, "DebugChannels");
        setupDefaultChannels();
    }

    public static String formatBoolean(boolean b) {
        if(b){
            return "<green>true</green>";
        }else{
            return "<red>false</red>";
        }
    }


    @EventHandler
    public void onLogout(PlayerQuitEvent event){
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Persist whatever channels this player is currently listening to into their PDC
        List<String> channels_to_save = listening_channels.get(uuid);
        if (channels_to_save != null && !channels_to_save.isEmpty()) {
            String serialized = String.join(",", channels_to_save);
            player.getPersistentDataContainer().set(debug_channels_key, PersistentDataType.STRING, serialized);
        } else {
            // Remove the key if there is nothing to save
            player.getPersistentDataContainer().remove(debug_channels_key);
        }

        // Unregister player from all debug channel listener sets and remove from in-memory map
        unregisterPlayerToAllChannels(player);
        listening_channels.remove(uuid);
    }


    @EventHandler
    public void onLogin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Load persisted channels from PDC (if any) and restore them directly (no permission check).
        String serialized = player.getPersistentDataContainer().get(debug_channels_key, PersistentDataType.STRING);
        if (serialized != null && !serialized.isEmpty()) {
            String[] channels = serialized.split(",");
            for (String ch : channels) {
                if (ch == null) continue;
                ch = ch.trim().toLowerCase();
                if (ch.isEmpty()) continue;
                restorePlayerChannelNoPerm(player, ch);
            }
        }

        // If there exists an in-memory remembered list (e.g. server didn't clear for this player),
        // prefer it: ensure it's applied to the channel sets as well.
        List<String> remembered = listening_channels.get(uuid);
        if (remembered != null && !remembered.isEmpty()) {
            // Make a copy to avoid concurrent modification while we register
            for (String ch : new ArrayList<>(remembered)) {
                restorePlayerChannelNoPerm(player, ch);
            }
        }
    }
    private void restorePlayerChannelNoPerm(Player player, String debug_channel) {
        debug_channel = debug_channel.toLowerCase();
        // ensure the channel set exists and add the player
        Set<UUID> player_set = getOrCreateChannelPlayerSet(debug_channel, false);
        player_set.add(player.getUniqueId());

        // ensure player's list exists and avoid duplicates
        listening_channels.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>());
        List<String> p_channels = listening_channels.get(player.getUniqueId());
        if (!p_channels.contains(debug_channel)) {
            p_channels.add(debug_channel);
        }
    }

    /**
     * This automatically registers channels in tab completion for systems which use Debug frequently.
     * If adding a new permanent channel, specify it here.
     */
    private void setupDefaultChannels() {
        getOrCreateChannelPlayerSet("xp", true);
        getOrCreateChannelPlayerSet("xp_player_name", true);
        getOrCreateChannelPlayerSet("levelup", true);
        getOrCreateChannelPlayerSet("craft", true);
        getOrCreateChannelPlayerSet("craftrare", true);
        getOrCreateChannelPlayerSet("craft_player_name", true);
        getOrCreateChannelPlayerSet("recipe", true);
        getOrCreateChannelPlayerSet("customitem", true);
        getOrCreateChannelPlayerSet("damage", true);
//        getOrCreateChannelPlayerSet("damage_player_name", true);
        getOrCreateChannelPlayerSet("armor", true);
        getOrCreateChannelPlayerSet("death", true);
        getOrCreateChannelPlayerSet("down", true);
        getOrCreateChannelPlayerSet("revive", true);
        getOrCreateChannelPlayerSet("combat", true);
        getOrCreateChannelPlayerSet("combatlog", true);
        getOrCreateChannelPlayerSet("weight", true);
        getOrCreateChannelPlayerSet("globalchat", true);
        getOrCreateChannelPlayerSet("mob", true);
        getOrCreateChannelPlayerSet("debug", true);
    }

    /**
     * returns if a player is listening to a specific debug channel
     * useful for quickly determining if a debug message should even be built
     * to prevent complex debug messages for being sent
     * see CustomPlayer.java for an example of why this is optimal
     */
    public static boolean isListeningToChannel(Player player, String debug_channel) {
        Debug debug = getInstance();
        if(debug.listening_channels.containsKey(player.getUniqueId())){
            if(debug.listening_channels.get(player.getUniqueId()).contains(debug_channel)){
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Determines if anyone is listening to this channel before sending a message to it
     * Good to use if sending a detailed debug message
     */
    public static boolean isAnyoneListening(String debug_channel, boolean create_channel_regardless) {
        Debug debug = getInstance();
        if(!debug.debug_listening.containsKey(debug_channel)) {
            if(create_channel_regardless){
                debug.debug_listening.put(debug_channel, new HashSet<>());
            }
            return false;
        }
        return !debug.debug_listening.get(debug_channel).isEmpty();
    }

    static void resetAllValues(CommandSender commander) {
        Debug debug = getInstance();

        Debug.broadcast("debug", "debug_listening: "+debug.debug_listening.size());
        Debug.broadcast("debug", "listening_channels: "+debug.listening_channels.size());
        Debug.broadcast("debug", "debug_channels: "+debug.debug_channels.size());
        Debug.broadcast("debug", "Resetting all values");

        debug.debug_listening = new HashMap<String, Set<UUID>>();
        debug.listening_channels = new HashMap<UUID, List<String>>();
        debug.debug_channels = new ArrayList<String>();
        debug.setupDefaultChannels();
        Specialization.getInstance().getLogger().info("Debug Cache Globally Reset by "+commander.getName());
    }

    /**
     * This registers a player to a debug channel to listen to it
     */
    public void registerPlayerChannel(Player player, String debug_channel){
        if (!player.hasPermission("specialization.debug")) {
            return;
        }
        debug_channel = debug_channel.toLowerCase();
        Set<UUID> player_set = getOrCreateChannelPlayerSet(debug_channel, false);
        player_set.add(player.getUniqueId());
        listening_channels.computeIfAbsent(player.getUniqueId(), p -> new ArrayList<String>()).add(debug_channel);
//        if(!debug_channels.contains(debug_channel)){
//            debug_channels.add(debug_channel);
//        }
    }

    /**
     * This unregisters a player fromm the channel they're listening to
     */
    public void unregisterPlayerChannel(Player player, String debug_channel){
        debug_channel = debug_channel.toLowerCase();
        Set<UUID> player_set = getOrCreateChannelPlayerSet(debug_channel, false);
        player_set.remove(player.getUniqueId());
        listening_channels.computeIfAbsent(player.getUniqueId(), p -> new ArrayList<String>()).remove(debug_channel);
    }

    public void registerPlayerToAllChannels(Player player) {
        for(String channel : debug_channels){
            registerPlayerChannel(player, channel);
        }
    }

    public void unregisterPlayerToAllChannels(Player player) {
        List<String> strings = listening_channels.get(player.getUniqueId());
        if (strings == null || strings.isEmpty()) {
            listening_channels.put(player.getUniqueId(), new ArrayList<String>());
            return;
        }
        // iterate a copy to avoid ConcurrentModificationException
        for (String channel : new ArrayList<>(strings)) {
            unregisterPlayerChannel(player, channel);
        }
        listening_channels.put(player.getUniqueId(), new ArrayList<String>());
    }

    private Set<UUID> getOrCreateChannelPlayerSet(String debug_channel, boolean add_to_suggestions){
        debug_channel = debug_channel.toLowerCase();
        Set<UUID> player_set;

        //retrieve debug channel list
        if(debug_listening.containsKey(debug_channel)) {
            player_set = debug_listening.get(debug_channel);
            if(add_to_suggestions && !debug_channels.contains(debug_channel)) debug_channels.add(debug_channel); //add for command-suggest quick lookup
        }else{
            player_set = new HashSet<UUID>();
            debug_listening.put(debug_channel, player_set); //add for registry
            if(add_to_suggestions) debug_channels.add(debug_channel); //add for command-suggest quick lookup
        }
        return player_set;
    }

    /**
     * Sends a debug broadcast to ALL players within a specific debug channnel
     * /debug add <debug_channel>
     * /debug remove <debug_channel>
     */
    public static void broadcast(String debug_channel, String msg, String hover_event){
        if(!DEBUG_ENABLED)return;
        Component comp = getInstance().formatDebugMessageDefault(debug_channel, msg, hover_event);
        broadcastFinalize(debug_channel, comp, false);
    }

    /**
     *
     * @param debug_channel The channel to send this debug message to
     * @param msg The base debug message
     * @param hover_event A sub message to hover for the debug message
     * @param register_channel whether or not this channel gets added to the TabCompleter for the command
     */
    public static void broadcast(String debug_channel, String msg, String hover_event, boolean register_channel){
        if(!DEBUG_ENABLED)return;
        Component comp = getInstance().formatDebugMessageDefault(debug_channel, msg, hover_event);
        broadcastFinalize(debug_channel, comp, register_channel);
    }

    /**
     * IMPORTANT: Use Debug.isEnabled() before using component based calls
     */
    public static void broadcast(String debug_channel, Component msg){
        if(!DEBUG_ENABLED)return;
        broadcastFinalize(debug_channel, getPrefix(debug_channel).append(msg), false);
    }

    /**
     * IMPORTANT: Use Debug.isEnabled() before using component based calls
     */
    public static void broadcast(String debug_channel, Component msg, Component hover){
        if(!DEBUG_ENABLED)return;
        Component comp;
        if(hover!=null){
            comp = msg.hoverEvent(HoverEvent.showText(hover));
        }else{
            comp = msg;
        }
        broadcastFinalize(debug_channel, getPrefix(debug_channel).append(comp), false);
    }

    /**
     * IMPORTANT: Use Debug.isEnabled() before using component based calls
     */
    public static void broadcast(String debug_channel, Component msg, Component hover, boolean register_channel){
        if(!DEBUG_ENABLED)return;
        Component comp = msg.hoverEvent(HoverEvent.showText(hover));
        broadcastFinalize(debug_channel, getPrefix(debug_channel).append(comp), register_channel);
    }

    /**
     * All other broadcasts channel here
     * Use this if you need quick response debugging
     */
    private static void broadcastFinalize(String debug_channel, Component comp, boolean register_channel) {
        debug_channel = debug_channel.toLowerCase();
        for(UUID playeruuid : getInstance().getOrCreateChannelPlayerSet(debug_channel, register_channel)){
            Player player = Bukkit.getPlayer(playeruuid);
            if(player!=null && player.isOnline()) {
                player.sendMessage(comp);
            }
        }
    }

    public static void broadcast(String debug_channel, String msg){
        if(!DEBUG_ENABLED)return;
        broadcast(debug_channel, msg, null);
    }


    public static void message(Player player, String debug_channel, String msg){
        message(player, debug_channel, msg, null);
    }

    /**
     * Sends a debug to a player who is listening to a debug channel
     */
    public static void message(Player player, String debug_channel, String msg, String hover_details){
        Debug debug = getInstance();
        Component comp = debug.formatDebugMessageDefault(debug_channel, msg, hover_details);
        if(debug.getOrCreateChannelPlayerSet(debug_channel, false).contains(player.getUniqueId())){
            player.sendMessage(comp);
        }
    }

    /**
     * Sends a debug to a player who is listening to a debug channel
     * IMPORTANT: Use Debug.isEnabled() before using component based calls
     */
    public static void message(Player player, String debug_channel, Component msg, Component hover) {
        if (!player.hasPermission("specialization.debug")) {
            return;
        }
        Debug debug = getInstance();
        if(hover!=null){
            msg = msg.hoverEvent(HoverEvent.showText(hover));
        }
        if(debug.getOrCreateChannelPlayerSet(debug_channel, false).contains(player.getUniqueId())){
            player.sendMessage(getPrefix(debug_channel).append(msg));
        }
    }

    /**
     * The Default Formatting for using Strings in debugMessage or debugBroadcast
     * @param debug_channel
     * @param msg
     * @param hover_details
     * @return
     */
    Component formatDebugMessageDefault(String debug_channel, String msg, String hover_details){
        Component comp = getPrefix(debug_channel).append(MiniMessage.miniMessage().deserialize(net.md_5.bungee.api.ChatColor.stripColor(msg)));//+msg);
        if(hover_details!=null) {
            Component hover = MiniMessage.miniMessage().deserialize(hover_details);
            return comp.hoverEvent(HoverEvent.showText(hover));
        }else{
            return comp;
        }
//        return comp;
    }

    private static final Map<String, Component> prefix_cache = new HashMap<>();

    private static final TextComponent bracket_l = Component.text("[", NamedTextColor.DARK_GRAY);
    private static final TextComponent bracket_r = Component.text("] ", NamedTextColor.DARK_GRAY);


    private static Component getPrefix(String debug_channel) {
        return prefix_cache.computeIfAbsent(debug_channel.toLowerCase(), ch ->
                bracket_l.append(Component.text(ch, NamedTextColor.DARK_GRAY)).append(bracket_r)
        );
    }

//    private static Component getPrefix(String debug_channel) {
//        return MiniMessage.miniMessage().deserialize("<dark_gray>[" + debug_channel.toLowerCase() + "]:</dark_gray> ");
//    }

    /**
     * Used for Tab Completion with the command
     */
    public static List<String> getChannelList(){
        return getInstance().debug_channels;
    }

    public static List<String> getPlayerChannels(Player player){
        Debug debug = getInstance();
        if(debug.listening_channels.containsKey(player.getUniqueId())){
            return debug.listening_channels.get(player.getUniqueId());
        }
        return new ArrayList<String>();
    }

    public static Debug getInstance(){
        return Specialization.getInstance().getDebugUtils();
    }



    private static TextColor red = TextColor.color(1, 0.7f, 0.7f);
    private static TextColor green = TextColor.color(0.7f, 1, 0.7f);
    private static TextColor blue = TextColor.color(0.7f, 0.7f, 1);

    /**
     *
     */
    public static String formatLocation(Location location){
        return "<gray>"+location.getWorld().getName()+"</gray>, "
                +"<red>"+(int)location.getX()+"</red>, "
                +"<green>"+(int)location.getY()+"</green>, "
                +"<blue>"+(int)location.getZ()+"</blue>";
    }
    /**
     *
     */
    public static Component formatLocationColored(Location location){
        return MiniMessage.miniMessage().deserialize("<gray>"+location.getWorld().getName()+"[</gray>"
                +"<red>"+(int)location.getX()+"</red>, "
                +"<green>"+(int)location.getY()+"</green>, "
                +"<blue>"+(int)location.getZ()+"</blue><gray>]</gray>");
    }

    /**
     * Creates a clickable location
     */
    public static Component formatLocationClickable(Location location, boolean compact){
        Component c;
        if(compact) {
            c = MiniMessage.miniMessage().deserialize("<blue>[loc]</blue>");
            c = c.hoverEvent(formatLocationColored(location));
        }else{
            c = formatLocationColored(location);
        }
        return c.clickEvent(ClickEvent.runCommand("/tp "+location.getBlockX()+" "+location.getBlockY()+" "+location.getBlockZ()));
    }


    private static DecimalFormat decimal_format = new DecimalFormat("#.##");

    /**
     * Helper for formatting decimals
     */
    public static String formatDecimal(double d){
        return new DecimalFormat("0.00").format(d);
    }


}
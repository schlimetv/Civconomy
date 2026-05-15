package com.minecraftcivilizations.specialization.CustomItem;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.minecraftcivilizations.specialization.Command.EmoteManager;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * @author Jfrogy & Alectriciti
 */

public class PacketListener extends PacketAdapter {

    EmoteManager emoteCommand;

    // ── Vanilla sounds suppressed in favour of custom resource-pack sounds ──────
    private static final Set<String> BLOCKED_SOUNDS = Set.of(
        "minecraft:block.furnace.fire_crackle",
        "minecraft:block.blastfurnace.fire_crackle",
        "minecraft:block.anvil.use",
        "minecraft:block.smithing_table.use"
    );

    public PacketListener(EmoteManager command) {
        super(Specialization.getInstance(),
                PacketType.Play.Server.NAMED_SOUND_EFFECT,
                PacketType.Play.Server.SYSTEM_CHAT
        );

        this.emoteCommand = command;
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();
        manager.addPacketListener(this);
    }

    @Override
    public void onPacketSending(PacketEvent event) {

        // --- Block sleep messages / filter localized broadcasts ---
        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT) {
            var comp = event.getPacket().getChatComponents().read(0);
            if (comp != null) {
                String json = comp.getJson();
                if (json != null) {
                    if (json.contains("sleep") || json.contains("Sleeping")) {
                        event.setCancelled(true);
                        return;
                    }
                    filterShipMessage(event, json);
                }
            }
        }

        if (event.getPacketType() != PacketType.Play.Server.NAMED_SOUND_EFFECT) return;

        // ── Resolve sound name early so both the block-list and the emote/silence
        //    checks can share it without duplicating the reflection work. ──────────
        String soundName = "";
        try {
            Object packet = event.getPacket().getHandle();
            java.lang.reflect.Field soundField = packet.getClass().getDeclaredField("sound");
            soundField.setAccessible(true);
            Object holder = soundField.get(packet);
            if (holder != null) {
                java.lang.reflect.Method unwrapKey = holder.getClass().getMethod("unwrapKey");
                Object key = unwrapKey.invoke(holder);
                if (key != null) soundName = key.toString();
            }
        } catch (NoSuchFieldException | IllegalAccessException | NoSuchMethodException
                 | java.lang.reflect.InvocationTargetException e) {
            e.printStackTrace();
        }

        // ── Block vanilla furnace / anvil / smithing sounds ──────────────────────
        if (BLOCKED_SOUNDS.contains(soundName)) {
            event.setCancelled(true);
            return;
        }

        Set<Player> silenced_players = emoteCommand.getSilencedPlayers();
        if(silenced_players.isEmpty()){
            Debug.broadcast("packet", "<gold>No Silenced Players</gold>");
            return;
        }

//        Debug.broadcast("packet", "<green>packet send to " + event.getPlayer().getName() + ":</green> " + event.getPacketType().name());

        Player player = event.getPlayer();
//        ItemStack item = player.getInventory().getItemInMainHand();
//        if (!item.getType().equals(Material.CROSSBOW)) return;

        try {
            Object packet = event.getPacket().getHandle(); // NMS packet

            if (!soundName.contains("crossbow")) {
                return;
            }

            // Location
            java.lang.reflect.Field xField = packet.getClass().getDeclaredField("x");
            java.lang.reflect.Field yField = packet.getClass().getDeclaredField("y");
            java.lang.reflect.Field zField = packet.getClass().getDeclaredField("z");
            xField.setAccessible(true);
            yField.setAccessible(true);
            zField.setAccessible(true);

            int xRaw = xField.getInt(packet);
            int yRaw = yField.getInt(packet);
            int zRaw = zField.getInt(packet);

            double x = xRaw / 8.0;
            double y = yRaw / 8.0;
            double z = zRaw / 8.0;

            for (Player silent_player : silenced_players) {
                if(silent_player.getLocation().distance(new Location(player.getWorld(), x, y, z)) < 1){
                    //player nearby is silenced
                    Debug.broadcast("packet", "<dark_red>cancelled sound to " + event.getPlayer().getName() + "</dark_red> " + " <red>sound:</red> " + soundName);
                    event.setCancelled(true);
                    return;
                }
            }

//            player.sendMessage("Sound packet: " + soundName + " | Location: " + x + ", " + y + ", " + z);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }

        // Cancel if holding an EmoteItem
//        CustomItem custom_item = Specialization.getInstance().getCustomItemManager().getCustomItem(item);
//        if (custom_item instanceof EmoteItem) {
//            event.setCancelled(true);
//        }
    }

    /**
     * Cancels a SYSTEM_CHAT packet for a recipient who is beyond ASSEMBLY_RADIUS blocks
     * from the pilot of the ship that triggered the message. Pilot identity is inferred
     * by finding the first online player whose name appears in the packet's JSON.
     * If no matching pilot is found, the packet is cancelled for all recipients.
     * Patterns are read from chatConfig; an empty pattern string disables that check.
     */
    private void filterShipMessage(PacketEvent event, String json) {
        String successPat = SpecializationConfig.getChatConfig().get("ASSEMBLY_SUCCESS_PATTERN", String.class);
        String failPat    = SpecializationConfig.getChatConfig().get("ASSEMBLY_FAIL_PATTERN", String.class);

        boolean matches = (!successPat.isEmpty() && json.contains(successPat))
                       || (!failPat.isEmpty()    && json.contains(failPat));
        if (!matches) return;

        double radius    = SpecializationConfig.getChatConfig().get("ASSEMBLY_RADIUS", Double.class);
        Player recipient = event.getPlayer();

        for (Player pilot : Bukkit.getOnlinePlayers()) {
            if (!json.contains(pilot.getName())) continue;
            // Guard against cross-world distance() call which throws IllegalArgumentException
            if (!recipient.getWorld().equals(pilot.getWorld())
                    || recipient.getLocation().distance(pilot.getLocation()) > radius) {
                event.setCancelled(true);
            }
            return; // stop after first name match
        }
        // Pilot not online — cancel for everyone
        event.setCancelled(true);
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        // Only process SOUND_EFFECT packets

        Debug.broadcast("packet", "<blue>packet receive:</blue> "+event.getPacketType().name() + event.getPlayer());

        if (event.getPacketType() != PacketType.Play.Server.NAMED_SOUND_EFFECT) return;

        Player player = event.getPlayer();

        if (player.getInventory().getItemInMainHand().getType() != Material.CROSSBOW) return;


        // Here you can filter only your emote crossbows
        // For example, check if player is holding an EmoteItem of type CLAP
        ItemStack item = player.getInventory().getItemInMainHand();
        CustomItem custom_item = Specialization.getInstance().getCustomItemManager().getCustomItem(item);
        if(custom_item instanceof EmoteItem emote_item) {
            event.setCancelled(true); // suppress the sound for this packet
        }
    }
}

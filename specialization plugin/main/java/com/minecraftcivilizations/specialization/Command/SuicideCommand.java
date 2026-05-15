package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.Combat.PVPManager;
import com.minecraftcivilizations.specialization.Listener.Player.PlayerDownedListener;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;


@CommandAlias("giveup|suicide|die|rip|gg|d")
public class SuicideCommand extends BaseCommand {
    private final PlayerDownedListener downedListener;
    private final PVPManager pvpManager;
    private final NamespacedKey downedByPlayerKey;
    private final JavaPlugin plugin;

    public SuicideCommand(PlayerDownedListener downedListener, PVPManager pvpManager, JavaPlugin plugin) {
        this.plugin = plugin;
        this.downedListener = downedListener;
        this.pvpManager = pvpManager;
        this.downedByPlayerKey = new NamespacedKey(plugin, "playerdowned");

        // Registering the command class elsewhere via ACF
    }

    @Default
    public void onSuicide(@NotNull Player player) {

        // Check if player is riding anything
        if (player.getVehicle() != null) {
            // Riding another player
            if (player.getVehicle() instanceof Player) {
                PlayerUtil.message(player, Component.text(
                        "You may not perform this action while being carried"));
                return;
            }

            // Riding a sheep leash proxy
            if (player.getVehicle() instanceof org.bukkit.entity.Sheep sheepLeashProxy) {
                NamespacedKey leashKey = new NamespacedKey(Specialization.getInstance(), "leash_proxy");

                if (sheepLeashProxy.getPersistentDataContainer().has(leashKey, PersistentDataType.BYTE)) {
                    Byte isLeashed = sheepLeashProxy.getPersistentDataContainer().get(leashKey, PersistentDataType.BYTE);
                    if (isLeashed != null && isLeashed == 1) {
                        PlayerUtil.message(player, Component.text(
                                "You may not perform this action while leashed"));
                        return;
                    }
                }
            }
        }

        UUID id = player.getUniqueId();
        boolean inCombat = pvpManager.combatMap.containsKey(id);

        if (player.getPersistentDataContainer().has(downedByPlayerKey, PersistentDataType.BYTE) && player.getPersistentDataContainer().get(downedByPlayerKey, PersistentDataType.BYTE) == 1 || inCombat) {
            PlayerUtil.message(player, Component.text("You may not perform this action due to PVP").color(NamedTextColor.RED), 1);
            return;
        }

        Byte downed = player.getPersistentDataContainer()
                .get(new NamespacedKey(Specialization.getInstance(), "is_downed"), PersistentDataType.BYTE);

        if (downed == null || downed == 0) {
            PlayerUtil.message(player, Component.text("You can only use this command while downed.").color(NamedTextColor.RED));
            return;
        }

        player.setHealth(0);
    }


    // -------------------------
    // /down — manually down yourself
    // -------------------------
    @CommandAlias("down|downed")
    @CommandPermission("civlabs.selfdown")
    public void downSelf(@NotNull Player player) {
        if (downedListener.isDowned(player)) {
            PlayerUtil.message(player, Component.text("You were knocked out by a magical force").color(NamedTextColor.RED));
            return;
        }

        // Down the player using PlayerDownedListener
        downedListener.setDowned(player, true, 10); // 10 = default health when downed
//        player.sendMessage(Component.text("You are now downed.").color(NamedTextColor.YELLOW));
    }

    // -------------------------
    // /up — manually revive yourself
    // -------------------------
    @CommandAlias("revive|getup|selfrevive")
    @CommandPermission("civlabs.selfrevive")
    public void upSelf(@NotNull Player player) {
        if (!downedListener.isDowned(player)) {
            PlayerUtil.message(player, Component.text("You are not downed.").color(NamedTextColor.RED));
            return;
        }

        downedListener.setDowned(player, false, player.getHealth());
        PlayerUtil.message(player, Component.text("You were revived by a magical force").color(NamedTextColor.GREEN));
    }
}


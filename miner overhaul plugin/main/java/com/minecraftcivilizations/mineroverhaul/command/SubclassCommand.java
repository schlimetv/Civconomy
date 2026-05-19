package com.minecraftcivilizations.mineroverhaul.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.data.SubclassData;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import com.minecraftcivilizations.mineroverhaul.subclass.SubclassPromptBuilder;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CommandAlias("subclass")
@CommandPermission("mineroverhaul.subclass")
public class SubclassCommand extends BaseCommand {

    private final MinerOverhaul plugin;
    private final ConcurrentHashMap<UUID, MinerSubclass> pendingConfirm = new ConcurrentHashMap<>();

    public SubclassCommand(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onDefault(Player player) {
        SubclassData data = plugin.getPlayerDataManager().getOrCreate(player.getUniqueId());
        if (data.hasSubclass()) {
            player.sendMessage(Component.text("Your subclass is ", NamedTextColor.GRAY)
                    .append(Component.text(data.getSubclass().displayName() + " Prospecting",
                            data.getSubclass().chatColor()))
                    .append(Component.text(".", NamedTextColor.GRAY)));
            return;
        }
        player.sendMessage(Component.text("Usage: /subclass miner <ore>", NamedTextColor.GRAY));
    }

    @Subcommand("miner")
    public void onPickMiner(Player player, @Optional String oreArg) {
        if (oreArg == null || oreArg.isBlank()) {
            player.sendMessage(Component.text("Usage: /subclass miner <ore>", NamedTextColor.GRAY));
            return;
        }
        MinerSubclass sub = MinerSubclass.fromArg(oreArg);
        if (sub == null) {
            player.sendMessage(Component.text("Unknown subclass: " + oreArg, NamedTextColor.RED));
            return;
        }

        PlayerDataManager pdm = plugin.getPlayerDataManager();
        SubclassData data = pdm.getOrCreate(player.getUniqueId());
        if (data.hasSubclass()) {
            player.sendMessage(Component.text("You already have a subclass: ", NamedTextColor.RED)
                    .append(Component.text(data.getSubclass().displayName() + " Prospecting",
                            data.getSubclass().chatColor()))
                    .append(Component.text(". Subclasses cannot be changed.", NamedTextColor.RED)));
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player.getUniqueId());
        int minerLevel = cp == null ? 0 : cp.getSkillLevel(SkillType.MINER);
        if (minerLevel < sub.requiredLevel()) {
            player.sendMessage(Component.text(
                    "You must be Miner level " + sub.requiredLevel() + " to select "
                            + sub.displayName() + " Prospecting.", NamedTextColor.RED));
            return;
        }

        pendingConfirm.put(player.getUniqueId(), sub);
        player.sendMessage(SubclassPromptBuilder.buildConfirmPrompt(sub));
    }

    @Subcommand("reset")
    @CommandPermission("mineroverhaul.admin")
    public void onReset(CommandSender sender, String target) {
        UUID uuid;
        String displayName = target;
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            uuid = online.getUniqueId();
            displayName = online.getName();
        } else {
            try {
                uuid = UUID.fromString(target);
            } catch (IllegalArgumentException e) {
                sender.sendMessage(Component.text(
                        "Player not online. Provide a UUID for offline reset.", NamedTextColor.RED));
                return;
            }
        }

        pendingConfirm.remove(uuid);
        plugin.getPlayerDataManager().reset(uuid);
        sender.sendMessage(Component.text(
                "Reset subclass data for " + displayName + ".", NamedTextColor.GREEN));

        // If they're online, re-deliver the level-up prompt right away.
        if (online != null) {
            CustomPlayer cp = CoreUtil.getPlayer(uuid);
            int minerLevel = cp == null ? 0 : cp.getSkillLevel(SkillType.MINER);
            if (minerLevel >= 1) {
                SubclassData data = plugin.getPlayerDataManager().getOrCreate(uuid);
                data.setLastPromptedLevel(minerLevel);
                plugin.getPlayerDataManager().save(uuid);
                online.sendMessage(SubclassPromptBuilder.buildLevelUpPrompt(
                        plugin.getMinerConfig(), minerLevel));
            }
        }
    }

    @Subcommand("confirm")
    public void onConfirm(Player player, String yn) {
        MinerSubclass pending = pendingConfirm.remove(player.getUniqueId());
        if (pending == null) {
            player.sendMessage(Component.text("Nothing to confirm.", NamedTextColor.GRAY));
            return;
        }
        if ("yes".equalsIgnoreCase(yn)) {
            PlayerDataManager pdm = plugin.getPlayerDataManager();
            SubclassData data = pdm.getOrCreate(player.getUniqueId());
            if (data.hasSubclass()) {
                player.sendMessage(Component.text("You already have a subclass.", NamedTextColor.RED));
                return;
            }
            data.setSubclass(pending);
            data.setSelectedAt(System.currentTimeMillis());
            pdm.save(player.getUniqueId());
            player.sendMessage(SubclassPromptBuilder.buildSelectedMsg(pending));
        } else {
            player.sendMessage(SubclassPromptBuilder.buildCancelMsg());
        }
    }
}

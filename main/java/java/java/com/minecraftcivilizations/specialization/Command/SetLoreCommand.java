package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@CommandAlias("setlore")
public class SetLoreCommand extends BaseCommand {

    @Default
    public void onSetLore(@NotNull CommandSender sender, String lore) {
        if (sender instanceof Player player && player.isOp()) {
            if(player.isOp()) {
                CustomItem from = CustomItem.from(player.getInventory().getItemInMainHand());
                from.addLore(Specialization.getInstance(), List.of(Component.text(lore).color(NamedTextColor.WHITE)));
            }
        }
    }

}

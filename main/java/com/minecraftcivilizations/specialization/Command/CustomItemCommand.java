package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CommandAlias("customitem|ci")
@Description("Custom item management commands")
@RequiredArgsConstructor
public class CustomItemCommand extends BaseCommand {

    private final CustomItemManager customItemManager;

    // --- GIVE ---
    @Subcommand("give|g")
    @Syntax("/customitem give <id> <amount>")
    @Description("Gives the player a custom item by ID")
    @CommandPermission("civlabs.customitem")
    @CommandCompletion("@customitems")
    public void onGive(Player sender, String id, int amount) {
        CustomItem item = customItemManager.getCustomItem(id);

        if (item == null) {
            sender.sendMessage("§cNo custom item found with ID: " + id);
            return;
        }
        if (!item.isEnabled()) {
            sender.sendMessage("§cThis item is disabled: " + id);
            return;
        }

        ItemStack stack = item.createItemStack(amount, sender);
        sender.getInventory().addItem(stack);
        sender.sendMessage("§aGave you custom item: §f" + item.getDisplayName());
    }

    // --- ENABLE ---
    @Subcommand("enable|e")
    @Syntax("/customitem enable <id>")
    @Description("Enable a custom item at runtime")
    @CommandPermission("civlabs.customitem")
    @CommandCompletion("@customitems")
    public void onEnable(Player sender, String id) {
        boolean success = customItemManager.enableItem(id);
        sender.sendMessage(!success
                ? "§aEnabled custom item: §f" + id
                : "§cCustom item not found or already enabled: §f" + id);
    }

    // --- DISABLE ---
    @Subcommand("disable|d")
    @Syntax("/customitem disable <id>")
    @CommandPermission("civlabs.customitem")
    @CommandCompletion("@customitems")
    public void onDisable(Player sender, String id) {
        boolean success = customItemManager.disableItem(id);
        sender.sendMessage(success
                ? "§cDisabled custom item: §f" + id
                : "§cCustom item not found or already disabled: §f" + id);
    }

    // --- REFRESH ---
    @Subcommand("refresh|r")
    @Syntax("/customitem refresh <id>")
    @Description("Refresh a custom item (rebuilds ItemStack, reloads data)")
    @CommandPermission("civlabs.customitem")
    @CommandCompletion("@customitems")
    public void onRefresh(Player sender, String id) {
        if(id == null ){
            customItemManager.initializeCustomItems(); //this should run when spelling /customitem refresh (without arguments)
        }
        CustomItem item = customItemManager.getCustomItem(id);
        if (item == null) {
            sender.sendMessage("§cCustom item not found: " + id);
            return;
        }
        customItemManager.reloadItem(id);
        sender.sendMessage("§aRefreshed custom item: §f" + id);
    }

    // --- LIST ---
    @Subcommand("list|l")
    @Syntax("/customitem list")
    @Description("Lists all custom items with their status")
    @CommandPermission("civlabs.customitem")
    public void onList(Player sender) {
        sender.sendMessage("§7==== §eCustom Items §7====");

        for (CustomItem item : customItemManager.getCustomItems()) {
            boolean enabled = item.isEnabled();
            // Use a colored bullet/emoji to indicate status
            String icon = enabled ? "§a●" : "§8●"; // green for enabled, gray for disabled
            sender.sendMessage(icon + " §f" + item.getId() + " §7(" + item.getDisplayName() + ")");
        }
    }

    // --- DEFAULT / HELP ---
    @Default
    @Syntax("/customitem help")
    @CommandCompletion("@customitems")
    @CommandPermission("civlabs.customitem")
    public void onDefault(Player sender) {
        sender.sendMessage("§7==== §eCustomItem Commands §7====");
        sender.sendMessage("§e/ci g <id> <amount> §7- Give a custom item");
        sender.sendMessage("§e/ci e <id> §7- Enable a custom item");
        sender.sendMessage("§e/ci d <id> §7- Disable a custom item");
        sender.sendMessage("§e/ci r <id> §7- Refresh a custom item");
        sender.sendMessage("§e/ci l §7- Shows a list of all custom items");
    }
}

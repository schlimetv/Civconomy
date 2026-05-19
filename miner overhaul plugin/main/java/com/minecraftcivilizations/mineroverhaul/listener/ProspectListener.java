package com.minecraftcivilizations.mineroverhaul.listener;

import com.minecraftcivilizations.mineroverhaul.MinerOverhaul;
import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.data.SubclassData;
import com.minecraftcivilizations.mineroverhaul.prospect.ProspectingService;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProspectListener implements Listener {

    private final MinerOverhaul plugin;
    private final ConcurrentHashMap<UUID, Long> lastUse = new ConcurrentHashMap<>();

    public ProspectListener(MinerOverhaul plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;
        if (!Tag.ITEMS_PICKAXES.isTagged(item.getType())) return;

        PlayerDataManager pdm = plugin.getPlayerDataManager();
        SubclassData data = pdm.getCached(player.getUniqueId());
        if (data == null || !data.hasSubclass()) return;

        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getMinerConfig().prospectCooldownSeconds() * 1000L;
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastUse.put(player.getUniqueId(), now);

        ProspectingService service = plugin.getProspectingService();
        service.scan(player, data.getSubclass());
    }
}

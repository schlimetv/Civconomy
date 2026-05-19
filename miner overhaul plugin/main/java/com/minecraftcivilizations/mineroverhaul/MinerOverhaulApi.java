package com.minecraftcivilizations.mineroverhaul;

import com.minecraftcivilizations.mineroverhaul.data.PlayerDataManager;
import com.minecraftcivilizations.mineroverhaul.data.SubclassData;
import com.minecraftcivilizations.mineroverhaul.subclass.MinerSubclass;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public final class MinerOverhaulApi {

    private MinerOverhaulApi() {}

    public static Optional<MinerSubclass> getSubclass(Player player) {
        if (player == null) return Optional.empty();
        return getSubclass(player.getUniqueId());
    }

    public static Optional<MinerSubclass> getSubclass(UUID uuid) {
        MinerOverhaul plugin = MinerOverhaul.getInstance();
        if (plugin == null) return Optional.empty();
        PlayerDataManager pdm = plugin.getPlayerDataManager();
        if (pdm == null) return Optional.empty();
        SubclassData data = pdm.getCached(uuid);
        if (data == null || !data.hasSubclass()) return Optional.empty();
        return Optional.of(data.getSubclass());
    }
}

package com.minecraftcivilizations.mineroverhaul.subclass;

import org.bukkit.Material;

public final class OreNaming {

    private OreNaming() {}

    public static String displayNameFor(Material m) {
        if (m == null) return "ore";
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> "coal";
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> "copper";
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> "gold";
            case IRON_ORE, DEEPSLATE_IRON_ORE -> "iron";
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> "redstone";
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> "lapis lazuli";
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> "emerald";
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> "diamond";
            default -> m.name().toLowerCase().replace('_', ' ');
        };
    }
}

package com.minecraftcivilizations.mineroverhaul.subclass;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum MinerSubclass {

    COAL(1, "Coal", NamedTextColor.DARK_GRAY,
            EnumSet.of(Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE)),
    COPPER(2, "Copper", NamedTextColor.GOLD,
            EnumSet.of(Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE)),
    GOLD(3, "Gold", NamedTextColor.YELLOW,
            EnumSet.of(Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE)),
    GEM(3, "Gem", NamedTextColor.LIGHT_PURPLE,
            EnumSet.of(
                    Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
                    Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
                    Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE)),
    IRON(4, "Iron", NamedTextColor.WHITE,
            EnumSet.of(Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE)),
    DIAMOND(5, "Diamond", NamedTextColor.AQUA,
            EnumSet.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE));

    private final int requiredLevel;
    private final String displayName;
    private final NamedTextColor chatColor;
    private final Set<Material> oreMaterials;

    MinerSubclass(int requiredLevel, String displayName, NamedTextColor chatColor, Set<Material> oreMaterials) {
        this.requiredLevel = requiredLevel;
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.oreMaterials = oreMaterials;
    }

    public int requiredLevel() { return requiredLevel; }
    public String displayName() { return displayName; }
    public NamedTextColor chatColor() { return chatColor; }
    public Set<Material> oreMaterials() { return oreMaterials; }

    public String lowerName() { return name().toLowerCase(); }

    public static List<MinerSubclass> availableFor(int minerLevel) {
        return Arrays.stream(values())
                .filter(s -> s.requiredLevel <= minerLevel)
                .collect(Collectors.toList());
    }

    public static MinerSubclass fromArg(String arg) {
        if (arg == null) return null;
        for (MinerSubclass s : values()) {
            if (s.name().equalsIgnoreCase(arg) || s.displayName.equalsIgnoreCase(arg)) {
                return s;
            }
        }
        return null;
    }

    public boolean matches(Material m) {
        return oreMaterials.contains(m);
    }
}

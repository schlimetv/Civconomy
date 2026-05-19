package com.animalbreeding.plugin.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Reflective bridge to the Specialization (Civconomy) plugin so AnimalBreeding
 * can read a player's Farmer skill level without a compile-time dependency.
 * Returns 0 if Specialization is not installed or the API has shifted.
 */
public final class FarmerLevelLookup {

    private static volatile boolean initialized;
    private static volatile boolean available;
    private static Method getCustomPlayerMethod;
    private static Method getSkillLevelMethod;
    private static Object farmerSkillType;

    private FarmerLevelLookup() {}

    public static int getFarmerLevel(Player player) {
        if (!initialized) init();
        if (!available) return 0;
        try {
            Object customPlayer = getCustomPlayerMethod.invoke(null, player);
            if (customPlayer == null) return 0;
            Object result = getSkillLevelMethod.invoke(customPlayer, farmerSkillType);
            return result instanceof Integer i ? i : 0;
        } catch (ReflectiveOperationException ex) {
            return 0;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (Bukkit.getPluginManager().getPlugin("Specialization") == null) return;
        try {
            Class<?> customPlayerClass = Class.forName(
                "com.minecraftcivilizations.specialization.Player.CustomPlayer");
            Class<?> skillTypeClass = Class.forName(
                "com.minecraftcivilizations.specialization.Skill.SkillType");
            getCustomPlayerMethod = customPlayerClass.getMethod("getCustomPlayer", Player.class);
            getSkillLevelMethod = customPlayerClass.getMethod("getSkillLevel", skillTypeClass);
            farmerSkillType = Enum.valueOf((Class<Enum>) skillTypeClass, "FARMER");
            available = true;
        } catch (ReflectiveOperationException ignored) {
            available = false;
        }
    }
}

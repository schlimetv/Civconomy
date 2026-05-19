package com.animalbreeding.plugin.utils;

import net.md_5.bungee.api.ChatColor;

public final class MessageUtil {

    private MessageUtil() {}

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}

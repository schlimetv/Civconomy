package com.minecraftcivilizations.specialization.util;

import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utilities related to player management
 * Instances of this class can hold transient player data
 * Player Cooldowns
 */
public class PlayerUtil {
//    public static Component LOGO = buildLogo();
    private static Map<String, Long> messageCooldowns = new HashMap<>();

     public static Component buildLogo() {
        // Using a smooth gradient across the logo text
        String logoGradient = "<#747ab6>[<gradient:#708EFA:#5E4F9F>CivLabs</gradient>]";
        return MiniMessage.miniMessage().deserialize(logoGradient);
    }

    /**
     * Sends a message with an optional cooldown in seconds.
     * If cooldownSeconds <= 0, no cooldown is applied.
     */

    public static void message(Player player, Object msg, double cooldownSeconds) {
        String key = "msg_" + player.getUniqueId();

        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            if (messageCooldowns.containsKey(key) && now < messageCooldowns.get(key)) {
                return; // still on cooldown
            }
            messageCooldowns.put(key, now + (long) (cooldownSeconds * 1000));
        }

        Component prefix = MiniMessage.miniMessage().deserialize("<dark_gray> » ");
        Component messageComp;

        if (msg instanceof Component comp) {
            messageComp = comp;
        } else if (msg instanceof String str) {
            str = legacyToMini("<gray>" + str);
            messageComp = MiniMessage.miniMessage().deserialize(str);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + msg.getClass());
        }

        player.sendMessage(buildLogo().append(prefix).append(messageComp));
    }


    public static void message(Player player, Object msg) {
        message(player, msg, 0);
    }

    /**
     * Convert legacy Minecraft formatting codes (§c, §7, §a, etc.) to MiniMessage syntax.
     */
    private static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) return "";
        return input
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");
    }




    public static void notify(Player player, String msg) {
        message(player, msg);
    }



    public PlayerUtil(UUID player){

    }

//    /**
//     * Color each character in the input string with a separate hex color using MiniMessage syntax.
//     * Example MiniMessage per char: "<#RRGGBB>c</#RRGGBB>"
//     *
//     * @param input the string to color (e.g. "civLabs")
//     * @param hexColors hex color strings, with or without leading '#', e.g. "ff0000" or "#00ff00"
//     * @return a single MiniMessage-formatted string where each character is wrapped in its color tag
//     */
//    public static String colorEachLetterMiniMsg(String input, String... hexColors) {
//        if (input == null || input.isEmpty()) return "";
//        if (hexColors == null || hexColors.length == 0) {
//            // default to white if no colors supplied
//            hexColors = new String[] { "ffffff" };
//        }
//
//        StringBuilder sb = new StringBuilder(input.length() * 12); // rough capacity
//        int colors = hexColors.length;
//        for (int i = 0; i < input.length(); i++) {
//            char ch = input.charAt(i);
//            String raw = hexColors[i % colors];
//            // normalize to RRGGBB (strip leading '#' if present)
//            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
//            // defensive: if invalid length, fall back to white
//            if (hex.length() != 6) hex = "ffffff";
//            sb.append('<').append('#').append(hex).append('>')
//                    .append(ch)
//                    .append("</").append('#').append(hex).append('>');
//        }
//        return sb.toString();
//    }
//
//    String out = colorEachLetterMiniMsg(
//            "civLabs",
//            "#ff0000", "#ff7f00", "#ffff00", "#00ff00", "#0000ff", "#4b0082", "#8f00ff"
//    );
// out -> "<#ff0000>c</#ff0000><#ff7f00>i</#ff7f00>..."




    /**
     * Color each character in the input string using java.awt.Color values.
     * Produces MiniMessage tags like: <#RRGGBB>c</#RRGGBB>
     *
     * @param input the text to color
     * @param colors array of java.awt.Color (cycled if fewer than characters)
     * @return MiniMessage-formatted string
     */
    public static String colorEachLetterMiniMsg(String input, Color... colors) {
        if (input == null || input.isEmpty()) return "";
        if (colors == null || colors.length == 0) {
            colors = new Color[] { Color.WHITE };
        }

        StringBuilder sb = new StringBuilder(input.length() * 14);
        int len = colors.length;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            Color c = colors[i % len];

            // Format the RGB into hex
            String hex = String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());

            sb.append("<#").append(hex).append('>')
                    .append(ch)
                    .append("</#").append(hex).append('>');
        }

        return sb.toString();
    }





    public static PlayerUtil getPlayerUtil(Player player){
        return Specialization.getInstance().getPlayerUtil(player.getUniqueId());
    }

    private Map<String, Long> cooldowns = new HashMap<String, Long>();


    /**
     * Set cooldown for this key for [durationMillis] milliseconds
     * @param key
     * @param ticks
     */
    public void setCooldown(String key, long ticks) {
        cooldowns.put(key, System.currentTimeMillis() + (ticks*50));
    }
    public void addCooldown(String key, long ticks) {
        if(isOnCooldown(key)){
            cooldowns.put(key, getRemainingCooldown(key) + (ticks*50));
        }else {
            cooldowns.put(key, System.currentTimeMillis() + (ticks * 50));
        }
    }

    public static void setCooldown(Player player, String key, long ticks){
        getPlayerUtil(player).setCooldown(key, ticks);
    }

    public static boolean isOnCooldown(Player player, String key){
        return getPlayerUtil(player).isOnCooldown(key);
    }

    public static long getRemainingCooldown(Player player, String key  ){
        return getPlayerUtil(player).getRemainingCooldown(key);
    }

    /**
     * Returns true if still on cooldown
     */
    public boolean isOnCooldown(String key) {
        if(!cooldowns.containsKey(key)) {
//    		cooldowns.put(key, System.currentTimeMillis());
            return false;
        }
        Long expireTime = cooldowns.get(key);
        return expireTime != null && System.currentTimeMillis() < expireTime;
    }

    /**
     * Returns remaining milliseconds, or 0 if expired
     * @return
     */
    public long getRemainingCooldown(String key) {
//    	if(cooldowns.)
        if(!cooldowns.containsKey(key)) {
            cooldowns.put(key, 0L);
            return 0L;
        }
        Long expireTime = cooldowns.get(key);
        if (expireTime == null) return 0;
        return Math.max(0, expireTime - System.currentTimeMillis());
    }




//    /**
//     * @return true if the player has the requested xp and if it was consumed
//     */
    public static boolean tryConsumeXp(Player player, int xp_amount) {
        int total = getExp(player);
        if (total < xp_amount) return false;

        changeExp(player, -xp_amount);
//        setTotalXp(player, total);
        return true;
    }


    /**
     *
     * source: https://gist.github.com/Jikoo/30ec040443a4701b8980
     * Runs "/xp set <player> <points>" as the console sender.
     * <points> sets raw XP points (no "L" suffix).
     */
    private static void setXpPointsViaConsole(Player player, int points) {
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            String cmd = "xp set " + player.getName() + " " + points;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        });
    }

    /**
     * Calculate a player's total experience based on level and progress to next.
     *
     * @param player the Player
     * @return the amount of experience the Player has
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getExp(Player player) {
        return getExpFromLevel(player.getLevel())
                + Math.round(getExpToNext(player.getLevel()) * player.getExp());
    }

    /**
     * Calculate total experience based on level.
     *
     * @param level the level
     * @return the total experience calculated
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getExpFromLevel(int level) {
        if (level > 30) {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level > 15) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return level * level + 6 * level;
    }

    /**
     * Calculate level (including progress to next level) based on total experience.
     *
     * @param exp the total experience
     * @return the level calculated
     */
    public static double getLevelFromExp(long exp) {
        int level = getIntLevelFromExp(exp);

        // Get remaining exp progressing towards next level. Cast to float for next bit of math.
        float remainder = exp - (float) getExpFromLevel(level);

        // Get level progress with float precision.
        float progress = remainder / getExpToNext(level);

        // Slap both numbers together and call it a day. While it shouldn't be possible for progress
        // to be an invalid value (value < 0 || 1 <= value)
        return ((double) level) + progress;
    }

    /**
     * Calculate level based on total experience.
     *
     * @param exp the total experience
     * @return the level calculated
     */
    public static int getIntLevelFromExp(long exp) {
        if (exp > 1395) {
            return (int) ((Math.sqrt(72 * exp - 54215D) + 325) / 18);
        }
        if (exp > 315) {
            return (int) (Math.sqrt(40 * exp - 7839D) / 10 + 8.1);
        }
        if (exp > 0) {
            return (int) (Math.sqrt(exp + 9D) - 3);
        }
        return 0;
    }

    /**
     * Get the total amount of experience required to progress to the next level.
     *
     * @param level the current level
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    private static int getExpToNext(int level) {
        if (level >= 30) {
            // Simplified formula. Internal: 112 + (level - 30) * 9
            return level * 9 - 158;
        }
        if (level >= 15) {
            // Simplified formula. Internal: 37 + (level - 15) * 5
            return level * 5 - 38;
        }
        // Internal: 7 + level * 2
        return level * 2 + 7;
    }

    /**
     * Change a Player's experience.
     *
     * <p>This method is preferred over {@link Player#giveExp(int)}.
     * <br>In older versions the method does not take differences in exp per level into account.
     * This leads to overlevelling when granting players large amounts of experience.
     * <br>In modern versions, while differing amounts of experience per level are accounted for, the
     * approach used is loop-heavy and requires an excessive number of calculations, which makes it
     * quite slow.
     *
     * @param player the Player affected
     * @param exp the amount of experience to add or remove
     */
    public static void changeExp(Player player, int exp) {
        exp += getExp(player);

        if (exp < 0) {
            exp = 0;
        }

        double levelAndExp = getLevelFromExp(exp);
        int level = (int) levelAndExp;
        player.setLevel(level);
        player.setExp((float) (levelAndExp - level));
    }


}

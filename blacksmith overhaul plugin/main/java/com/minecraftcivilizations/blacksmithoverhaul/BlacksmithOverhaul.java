package com.minecraftcivilizations.blacksmithoverhaul;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * BlacksmithOverhaul — the metalworking system extracted from Specialization.
 *
 * <p>This plugin ships <b>classes only</b>: every listener, item definition, and
 * recipe registration is instantiated and wired up by the Specialization plugin
 * (which compileOnly-depends on this jar). That keeps a single source of truth
 * for skill XP, custom-item registry, and config — all owned by Specialization
 * — while letting the metalworking content live in its own jar so it can be
 * iterated independently.</p>
 *
 * <p>Because Specialization owns the wiring, this plugin's {@code onEnable} is
 * intentionally a no-op aside from setting the singleton instance. The plugin
 * still has to exist as a Bukkit plugin so its classes are loaded into the
 * server's classpath in time for Specialization to import them, and so the
 * resource-pack and {@code plugin.yml} machinery (depend ordering) works.</p>
 */
public final class BlacksmithOverhaul extends JavaPlugin {

    private static BlacksmithOverhaul instance;

    public static BlacksmithOverhaul getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("BlacksmithOverhaul loaded — content classes available to Specialization.");
    }

    @Override
    public void onDisable() {
        instance = null;
    }
}

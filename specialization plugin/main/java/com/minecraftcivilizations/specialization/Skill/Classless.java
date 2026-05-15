package com.minecraftcivilizations.specialization.Skill;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import org.bukkit.Material;
import org.bukkit.Tag;

import java.util.Set;

/**
 * Handles the "Classless" state — active when a player is Novice (tier 0) in ALL specializations.
 * While Classless, basic survival crafting items have no hunger cost.
 *
 * A player becomes Classless:
 *   - On first join (all skills start at 0 XP)
 *   - On death (all skills are reset to 0 XP)
 *
 * A player loses Classless status:
 *   - Upon reaching Apprentice (tier 1) in ANY specialization
 */
public class Classless {

    /**
     * Specific materials exempt from hunger cost while Classless.
     * Wood planks and boats are handled separately via Tag/name checks.
     */
    private static final Set<Material> EXEMPT_ITEMS = Set.of(
            Material.TORCH,
            Material.SOUL_TORCH,
            Material.STICK,
            Material.CRAFTING_TABLE,
            Material.FURNACE,
            Material.CHEST,
            Material.BARREL
    );

    /**
     * A player is Classless when ALL skills are at level 0 (Novice).
     * Returns false as soon as any skill is Apprentice (tier 1) or above.
     */
    public static boolean isClassless(CustomPlayer customPlayer) {
        for (SkillType skillType : SkillType.values()) {
            if (customPlayer.getSkillLevel(skillType) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the given material is exempt from hunger cost while Classless.
     *
     * Exempt items:
     *   - All wood planks       (via Tag.PLANKS)
     *   - All wood boats        (via _BOAT / _CHEST_BOAT suffix)
     *   - Torches, Soul Torches
     *   - Sticks
     *   - Crafting Table
     *   - Furnace
     *   - Chest
     *   - Barrel
     */
    public static boolean isExemptItem(Material material) {
        // Check specific materials first (fast hash lookup)
        if (EXEMPT_ITEMS.contains(material)) return true;

        // All wood plank variants
        if (Tag.PLANKS.isTagged(material)) return true;

        // All wood boat variants (OAK_BOAT, SPRUCE_CHEST_BOAT, etc.)
        String name = material.name();
        if (name.endsWith("_BOAT") || name.endsWith("_CHEST_BOAT")) return true;

        return false;
    }
}

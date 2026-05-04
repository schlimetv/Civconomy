package com.minecraftcivilizations.blacksmithoverhaul.api;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Public touch points exposed by BlacksmithOverhaul to other plugins (Specialization
 * in particular). Anything here is intended to remain stable; everything else in
 * this jar is implementation detail and may move freely.
 *
 * <h3>Why the {@code "specialization"} namespace?</h3>
 * Every PDC key declared here has historically been written under the
 * {@code specialization:} namespace by the original Specialization plugin
 * (before metalworking was extracted). Existing items in player inventories
 * carry those tags, so the namespace is preserved verbatim — changing it
 * would brick every steel piece already crafted on the live server.
 */
public final class MetalworkingApi {

    private MetalworkingApi() {}

    // ─────────────────────────────────────────────────────────────
    //  Steel armor markers
    // ─────────────────────────────────────────────────────────────

    /**
     * BYTE PDC marker set on every <b>purple-tempered (Hardened)</b> steel
     * armor piece by {@code SmithingAssemblyListener.applySteelStats}.
     * Specialization's combat code reads this to apply purple-steel-specific
     * damage and durability mechanics.
     */
    public static final NamespacedKey PURPLE_STEEL_KEY =
        new NamespacedKey("specialization", "purple_steel_armor");

    /**
     * BYTE PDC marker set on every <b>blue-tempered (Toughened)</b> steel
     * tool or armor piece. Required as the base slot for diamond smithing.
     */
    public static final NamespacedKey TOUGHENED_STEEL_KEY =
        new NamespacedKey("specialization", "toughened_steel");

    // ─────────────────────────────────────────────────────────────
    //  Custom-item id key (mirror of Specialization's CustomItemManager key)
    // ─────────────────────────────────────────────────────────────

    /**
     * The PDC key under which {@code CustomItemManager} stores each custom
     * item's id. Mirrored here so BlacksmithOverhaul's API methods can read
     * the id without importing Specialization-internal classes.
     */
    public static final NamespacedKey CUSTOM_ITEM_ID_KEY =
        new NamespacedKey("specialization", "custom_item_id");

    // ─────────────────────────────────────────────────────────────
    //  Wrought-iron tag — consulted by Specialization's vanilla-recipe gate
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the custom-item id of {@code stack}, or {@code null} if the stack
     * carries no Specialization custom-item tag.
     */
    public static String getCustomItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer()
            .get(CUSTOM_ITEM_ID_KEY, PersistentDataType.STRING);
    }

    /**
     * True if {@code stack} is the metalworking "wrought iron ingot" custom item.
     *
     * <p>Specialization's {@code onPrepareItemCraft} consults this when deciding
     * whether to allow a custom item into a non-metalworking-namespaced vanilla
     * recipe — wrought iron is functionally equivalent to a vanilla iron ingot
     * for non-gear, non-tool recipes (buckets, cauldrons, iron bars, etc.).</p>
     */
    public static boolean isWroughtIron(ItemStack stack) {
        return "wrought_iron_ingot".equals(getCustomItemId(stack));
    }
}

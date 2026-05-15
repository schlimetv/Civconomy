package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.StaffTools.Debug;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;

import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.bukkit.inventory.meta.trim.TrimPattern.*;

/**
 * Maps player first/last names to armor trim patterns & materials.
 * If no explicit mapping exists, falls back to a deterministic choice based on the
 * name's hash so every player still gets a stable "signature" style.
 * @author alectriciti
 */
public class BlacksmithArmorTrim {

    // explicit mappings (populate as desired)
    private final Map<String, TrimMaterial> last_name_mappings = new HashMap<>();
    private final Map<String, TrimPattern> first_name_mappings = new HashMap<>();
    private final Map<TrimMaterial, String> trim_mappings = new HashMap<>();

    public BlacksmithArmorTrim() {
        TRIM_PATTERNS = new ArrayList<>();
        TRIM_MATERIALS = new ArrayList<>();

        TRIM_PATTERNS.add(SENTRY);
        TRIM_PATTERNS.add(VEX);
        TRIM_PATTERNS.add(WILD);
        TRIM_PATTERNS.add(COAST);
        TRIM_PATTERNS.add(DUNE);
        TRIM_PATTERNS.add(WAYFINDER);
        TRIM_PATTERNS.add(RAISER);
        TRIM_PATTERNS.add(SHAPER);
        TRIM_PATTERNS.add(HOST);
        TRIM_PATTERNS.add(WARD);
        TRIM_PATTERNS.add(SILENCE);
        TRIM_PATTERNS.add(TIDE);
        TRIM_PATTERNS.add(SNOUT);
        TRIM_PATTERNS.add(RIB);
        TRIM_PATTERNS.add(EYE);
        TRIM_PATTERNS.add(SPIRE);
        TRIM_PATTERNS.add(FLOW);
        TRIM_PATTERNS.add(BOLT);

        TRIM_MATERIALS.add(TrimMaterial.AMETHYST);
        TRIM_MATERIALS.add(TrimMaterial.COPPER);
        TRIM_MATERIALS.add(TrimMaterial.DIAMOND);
        TRIM_MATERIALS.add(TrimMaterial.EMERALD);
        TRIM_MATERIALS.add(TrimMaterial.GOLD);
        TRIM_MATERIALS.add(TrimMaterial.IRON);
        TRIM_MATERIALS.add(TrimMaterial.LAPIS);
        TRIM_MATERIALS.add(TrimMaterial.QUARTZ);
        TRIM_MATERIALS.add(TrimMaterial.NETHERITE);
        TRIM_MATERIALS.add(TrimMaterial.REDSTONE);
        TRIM_MATERIALS.add(TrimMaterial.RESIN);
//        populateNameMappings();
    }

    public void applyLastName(TrimMaterial material, String... last_names) {
        if(material==null)return;
        for (String name : last_names) {
            if (name == null || name.isEmpty()) continue;
            last_name_mappings.put(name.toLowerCase(), material);
        }
    }

    public void applyFirstName(TrimPattern pattern, String... first_names) {
        if(pattern==null)return;
        for (String name : first_names) {
            if (name == null || name.isEmpty()) continue;
            first_name_mappings.put(name.toLowerCase(), pattern);
        }
    }

    // assume these exist and you will populate them somewhere else:
    public List<TrimMaterial> TRIM_MATERIALS;    // supply TrimMaterial choices
    public List<TrimPattern> TRIM_PATTERNS;  // supply TrimPattern choices

    /**
     * Return an ArmorTrim for the player using explicit name mappings if present,
     * otherwise deterministically pick from the provided ARMOR_TRIMS / ARMOR_PATTERNS.
     */
    public ArmorTrim getPlayerTrim(Player player, ItemStack current) {
        String[] parts = player.getName().split("_");
        String first_name = parts.length > 0 ? parts[0].toLowerCase() : player.getName().toLowerCase();
        String last_name = parts.length > 1 ? parts[1].toLowerCase() : "";

        TrimPattern pattern = getPatternByName(first_name);
        String material_tag = last_name.isEmpty() ? first_name : last_name;
        TrimMaterial material = getTrimMaterialByName(material_tag);

        Debug.broadcast("armortrim", "trim applied with "+material_tag + " first name: "+first_name+" and last: "+last_name);
        return new ArmorTrim(material, pattern);
    }

    /**
     * Return a trim pattern by name mapping or deterministic hash fallback using ARMOR_PATTERNS.
     */
    public TrimPattern getPatternByName(String name) {
        if (name != null) {
            if(name.toLowerCase().equals("random")){
                return pickByHash("random_"+ThreadLocalRandom.current().nextInt(1000000)+"_hash", TRIM_PATTERNS, TrimPattern.class);
            }
            TrimPattern explicit = first_name_mappings.get(name.toLowerCase());
            if (explicit != null) return explicit;
        }
        return pickByHash(name, TRIM_PATTERNS, TrimPattern.class);
    }

    /**
     * Return a trim material by name mapping or deterministic hash fallback using ARMOR_TRIMS.
     */
    public TrimMaterial getTrimMaterialByName(String name) {
        if (name != null) {
            TrimMaterial explicit = last_name_mappings.get(name.toLowerCase());
            if (explicit != null) return explicit;
        }
        return pickByHash(name, TRIM_MATERIALS, TrimMaterial.class);
    }

    /**
     * Generic deterministic picker: returns an element from the collection based on name.hashCode().
     * If collection is empty, returns null.
     */
    private static <T> T pickByHash(String name, Collection<T> choices, Class<T> type) {
        if (choices == null || choices.isEmpty()) return null;
        int hash = (name == null) ? 0 : name.hashCode();
        int size = choices.size();
        int idx = Math.floorMod(hash, size);
        // convert to list for indexed access
        if (choices instanceof List) {
            return ((List<T>) choices).get(idx);
        } else {
            Iterator<T> it = choices.iterator();
            for (int i = 0; i < idx; i++) it.next();
            return it.next();
        }
    }

    /**
     * Deterministic selection from enum based on name hash.
     */
    private static <T extends Enum<T>> T pickByHash(String name, T[] values) {
        if (values.length == 0) return null;
        int hash = (name == null) ? 0 : name.hashCode();
        int idx = Math.floorMod(hash, values.length);
        return values[idx];
    }

    /**
     * Convenience: apply the computed trim to the given ItemStack if its ItemMeta supports it.
     * Not all ItemMeta types expose a trim setter depending on the server version; this attempts
     * a safe apply (no-op if unsupported).
     */
    public ArmorTrim applyArmorTrimToItem(Player player, ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        ArmorTrim trim = getPlayerTrim(player, item);
            if(meta instanceof ArmorMeta ameta){
                ameta.setTrim(trim);
                item.setItemMeta(ameta);
                return trim;
            }
        return null;
    }





    public static TrimPattern getPatternOf(String tag) {
        if (tag == null) return null;

        switch (tag.trim().toLowerCase()) {
            case "sentry":      return TrimPattern.SENTRY;
            case "vex":         return TrimPattern.VEX;
            case "wild":        return TrimPattern.WILD;
            case "coast":       return TrimPattern.COAST;
            case "dune":        return TrimPattern.DUNE;
            case "wayfinder":   return TrimPattern.WAYFINDER;
            case "raiser":      return TrimPattern.RAISER;
            case "shaper":      return TrimPattern.SHAPER;
            case "host":        return TrimPattern.HOST;
            case "ward":        return TrimPattern.WARD;
            case "silence":     return TrimPattern.SILENCE;
            case "tide":        return TrimPattern.TIDE;
            case "snout":       return TrimPattern.SNOUT;
            case "rib":         return TrimPattern.RIB;
            case "eye":         return TrimPattern.EYE;
            case "spire":       return TrimPattern.SPIRE;
            case "flow":        return TrimPattern.FLOW;
            case "bolt":        return TrimPattern.BOLT;
            default:            return null;
        }
    }

    public static TrimMaterial getMaterialOf(String tag) {
        if (tag == null) return null;

        switch (tag.trim().toLowerCase()) {
            case "amethyst":    return TrimMaterial.AMETHYST;
            case "copper":      return TrimMaterial.COPPER;
            case "diamond":     return TrimMaterial.DIAMOND;
            case "emerald":     return TrimMaterial.EMERALD;
            case "gold":        return TrimMaterial.GOLD;
            case "iron":        return TrimMaterial.IRON;
            case "lapis":       return TrimMaterial.LAPIS;
            case "quartz":      return TrimMaterial.QUARTZ;
            case "netherite":   return TrimMaterial.NETHERITE;
            case "redstone":    return TrimMaterial.REDSTONE;
            case "resin":       return TrimMaterial.RESIN;
            default:            return null;
        }
    }


    /**
     * Mapping for text color
     * taken from here <a href="https://minecraft.wiki/w/Smithing#Material">...</a>
     */
    public static String getMaterialColor(TrimMaterial mat) {
        if (mat == null) return "#CCCCCC";

        if (mat == TrimMaterial.AMETHYST)   return "#9A5CC6";
        if (mat == TrimMaterial.COPPER)     return "#B4684D";
        if (mat == TrimMaterial.DIAMOND)    return "#6EECD2";
        if (mat == TrimMaterial.EMERALD)    return "#11A036";
        if (mat == TrimMaterial.GOLD)       return "#DEB12D";
        if (mat == TrimMaterial.IRON)       return "#ECECEC";
        if (mat == TrimMaterial.LAPIS)      return "#416E97";
        if (mat == TrimMaterial.QUARTZ)     return "#E3D4C4";
        if (mat == TrimMaterial.NETHERITE)  return "#625859";
        if (mat == TrimMaterial.REDSTONE)   return "#971607";
        if (mat == TrimMaterial.RESIN)      return "#FC7812";

        return "#CCCCCC";
    }


}

package com.animalbreeding.plugin.tasks;

import com.animalbreeding.plugin.AnimalBreedingPlugin;
import com.animalbreeding.plugin.integration.FarmerLevelLookup;
import com.animalbreeding.plugin.listeners.BreedingListener;
import com.animalbreeding.plugin.managers.BreedingManager;
import com.animalbreeding.plugin.managers.GenderManager;
import com.animalbreeding.plugin.managers.GeneticsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * Every tick: teleports a shared TextDisplay entity to follow each nearby
 * supported adult animal visible to at least one player who has reached
 * Farmer level 2+ in the Specialization (Civconomy) plugin.
 *
 * ── Farmer-level tiers ───────────────────────────────────────────────────
 *  Level 0–1 : nothing shown
 *  Level 2   : animal type + gender symbol only
 *  Level 3   : + feeding progress and partner status
 *  Level 4   : + cooldown timer
 *  Level 5   : + full genetic trait list
 *
 *  Each animal gets one TextDisplay per tier it currently needs (lazily —
 *  unused tiers are never spawned). Each player only sees the display whose
 *  tier matches their own farmer level, so two farmers at different levels
 *  near the same animal each see content suited to THEIR level.
 *  If Specialization is not installed, no labels are shown.
 *
 *  Labels are client-side per viewer: TextDisplay entities exist on the
 *  server but are hidden via {@code Player#hideEntity} from anyone whose
 *  tier doesn't match (or who toggled labels off, or is below Farmer 2).
 *
 * ── Smoothness ───────────────────────────────────────────────────────────
 *  We teleport every tick and set interpolationDuration(2) so the Minecraft
 *  client smoothly interpolates between server positions — no jitter and
 *  no passenger / AI side-effects.
 * ─────────────────────────────────────────────────────────────────────────
 */
public class HoeDisplayTask extends BukkitRunnable {

    private static final double DISPLAY_RANGE   = 16.0;
    private static final float  LABEL_Y_OFFSET  = 0.25f;
    private static final String META_DISPLAY    = "ab_label";

    private final AnimalBreedingPlugin plugin;

    /**
     * animal UUID → (tier → TextDisplay UUID).
     * One display per tier so each viewer sees the content matching THEIR farmer level,
     * even when other farmers of different levels are nearby. Tiers spawn lazily —
     * if no current viewer needs tier 3, it's never created for that animal.
     */
    private final Map<UUID, Map<Integer, UUID>> animalTierDisplays = new HashMap<>();

    public HoeDisplayTask(AnimalBreedingPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Main loop
    // ------------------------------------------------------------------

    @Override
    public void run() {
        BreedingManager manager = plugin.getBreedingManager();

        // Per-player tier (1–4) for online qualifying players.
        Map<UUID, Integer> playerTiers = new HashMap<>();
        // For each nearby animal, the set of tiers some qualifying viewer needs.
        Map<UUID, Set<Integer>> animalTiersNeeded = new HashMap<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!plugin.getLabelPreferenceManager().isEnabled(player)) continue;
            int farmerLevel = FarmerLevelLookup.getFarmerLevel(player);
            if (farmerLevel < 2) continue;
            int tier = Math.min(farmerLevel - 1, 4);
            playerTiers.put(player.getUniqueId(), tier);

            for (Entity e : player.getNearbyEntities(DISPLAY_RANGE, DISPLAY_RANGE, DISPLAY_RANGE)) {
                if (e instanceof Animals a && manager.isSupportedAnimal(a) && a.isAdult()) {
                    animalTiersNeeded
                        .computeIfAbsent(a.getUniqueId(), k -> new HashSet<>())
                        .add(tier);
                }
            }
        }

        // Drop tier-displays no one needs anymore. If an animal has no tiers left,
        // its entry is removed entirely.
        animalTierDisplays.entrySet().removeIf(animalEntry -> {
            UUID animalId = animalEntry.getKey();
            Map<Integer, UUID> tierMap = animalEntry.getValue();
            Set<Integer> needed = animalTiersNeeded.getOrDefault(animalId, Collections.emptySet());

            tierMap.entrySet().removeIf(tierEntry -> {
                if (needed.contains(tierEntry.getKey())) return false;
                Entity disp = plugin.getServer().getEntity(tierEntry.getValue());
                if (disp != null) disp.remove();
                return true;
            });

            return tierMap.isEmpty();
        });

        // Spawn or update each (animal, tier) display for the tiers actually in demand.
        for (Map.Entry<UUID, Set<Integer>> entry : animalTiersNeeded.entrySet()) {
            UUID animalId = entry.getKey();
            Entity entity = plugin.getServer().getEntity(animalId);
            if (!(entity instanceof Animals animal)) {
                removeDisplayForAnimal(animalId);
                continue;
            }

            Location loc = labelLocation(animal);
            Map<Integer, UUID> tierMap = animalTierDisplays.computeIfAbsent(animalId, k -> new HashMap<>());

            for (int tier : entry.getValue()) {
                Component label = buildLabel(animal, manager, tier);
                UUID existingId = tierMap.get(tier);
                if (existingId != null) {
                    Entity disp = plugin.getServer().getEntity(existingId);
                    if (disp instanceof TextDisplay td) {
                        td.teleport(loc);
                        td.text(label);
                        continue;
                    }
                    tierMap.remove(tier);
                }

                TextDisplay td = animal.getWorld().spawn(loc, TextDisplay.class, d -> {
                    d.text(label);
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setSeeThrough(false);
                    d.setBackgroundColor(org.bukkit.Color.fromARGB(160, 0, 0, 0));
                    d.setShadowed(true);
                    d.setTransformation(new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(0.6f, 0.6f, 0.6f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                    ));
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(2);
                    d.setPersistent(false);
                    d.setMetadata(META_DISPLAY,
                        new FixedMetadataValue(plugin, animal.getUniqueId().toString()));
                });
                tierMap.put(tier, td.getUniqueId());
            }
        }

        applyVisibility(playerTiers);
    }

    /**
     * For each (animal, tier) display, show it only to players whose own tier
     * matches and hide it from everyone else. Out-of-range players are handled
     * naturally by Bukkit's entity tracker — show/hide just gates which
     * displays they're allowed to see when they ARE in range.
     */
    private void applyVisibility(Map<UUID, Integer> playerTiers) {
        var onlinePlayers = plugin.getServer().getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        for (Map<Integer, UUID> tierMap : animalTierDisplays.values()) {
            for (Map.Entry<Integer, UUID> tierEntry : tierMap.entrySet()) {
                int displayTier = tierEntry.getKey();
                Entity disp = plugin.getServer().getEntity(tierEntry.getValue());
                if (disp == null) continue;
                for (Player p : onlinePlayers) {
                    Integer pTier = playerTiers.get(p.getUniqueId());
                    if (pTier != null && pTier == displayTier) {
                        p.showEntity(plugin, disp);
                    } else {
                        p.hideEntity(plugin, disp);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Label builder — each tier adds one layer of info
    // ------------------------------------------------------------------

    private static NamedTextColor genderColor(GenderManager.Gender g) {
        return g == GenderManager.Gender.MALE ? NamedTextColor.BLUE : NamedTextColor.LIGHT_PURPLE;
    }

    private Component buildLabel(Animals animal, BreedingManager manager, int tier) {
        GenderManager    genderMgr   = plugin.getGenderManager();
        GeneticsManager  geneticsMgr = plugin.getGeneticsManager();
        GenderManager.Gender gender  = genderMgr.getGender(animal);

        // ── Tier 1: type name + gender symbol ──────────────────────────
        Component label = Component.text(BreedingListener.formatType(animal) + " ", NamedTextColor.WHITE)
            .decoration(TextDecoration.BOLD, true)
            .append(Component.text(gender.symbol(), genderColor(gender))
                .decoration(TextDecoration.BOLD, true));

        if (tier < 2) return label;

        // ── Tier 2: feeding progress + partner status ───────────────────
        boolean inPen    = manager.isInEnclosedPen(animal);
        int penAnimals   = inPen ? manager.countPenAnimals(animal) : 0;
        int totalCost    = manager.calculateFoodCost(penAnimals);
        int myFed        = manager.getFedAmount(animal);
        int myMax        = totalCost - 1; // max this animal can hold

        if (!inPen) {
            label = label.append(Component.newline())
                .append(Component.text("⚠ Needs a pen", NamedTextColor.RED));
            // Still try to show tier 3+ info below even out of pen
        } else {
            // Food progress: "me X / max Y  pool X+Y / total"
            NamedTextColor fedColor = myFed >= myMax ? NamedTextColor.GREEN : NamedTextColor.GOLD;
            Component foodLine = Component.text("Food: ", NamedTextColor.GRAY)
                .append(Component.text(myFed + "/" + myMax, fedColor));

            // Find partner info
            Animals partner    = manager.findBreedingPartner(animal, totalCost);
            int partnerFed     = (partner != null)
                ? manager.getFedAmount(partner)
                : bestOppositePartnerFed(animal, manager, genderMgr);

            Component partnerLine;
            GenderManager.Gender opp = gender.opposite();
            if (partner != null) {
                GenderManager.Gender pg = genderMgr.getGender(partner);
                partnerLine = Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(pg.symbol(), genderColor(pg)))
                    .append(Component.text(" ready!", genderColor(pg)));
            } else if (partnerFed > 0) {
                int need = Math.max(0, totalCost - myFed - partnerFed);
                partnerLine = Component.text(opp.symbol(), genderColor(opp))
                    .append(Component.text(" " + partnerFed + " fed", NamedTextColor.GOLD))
                    .append(need > 0
                        ? Component.text("  +" + need + " needed", NamedTextColor.DARK_GRAY)
                        : Component.empty());
            } else {
                partnerLine = Component.text("No ", NamedTextColor.RED)
                    .append(Component.text(opp.symbol(), genderColor(opp)))
                    .append(Component.text(" fed yet", NamedTextColor.RED));
            }

            Component penInfo = Component.text("Pen: " + penAnimals + " adults", NamedTextColor.DARK_AQUA)
                .append(penAnimals > 0
                    ? Component.text("  cost+" + (penAnimals * 2), NamedTextColor.DARK_GRAY)
                    : Component.empty());

            label = label
                .append(Component.newline()).append(foodLine)
                .append(Component.newline()).append(partnerLine)
                .append(Component.newline()).append(penInfo);
        }

        if (tier < 3) return label;

        // ── Tier 3: cooldown timer ──────────────────────────────────────
        if (manager.isOnCooldown(animal)) {
            long rem = manager.getRemainingCooldown(animal);
            label = label.append(Component.newline())
                .append(Component.text("⏳ Cooldown: " + rem + "s", NamedTextColor.RED));
        }

        if (tier < 4) return label;

        // ── Tier 4: behavior + genetic traits ───────────────────────────
        GeneticsManager.Trait behavior = geneticsMgr.getBehavior(animal);
        if (behavior != null) {
            label = label.append(Component.newline())
                .append(Component.text("Behavior: ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true))
                .append(Component.text(behavior.color + behavior.displayName, NamedTextColor.WHITE));
        }

        Set<GeneticsManager.Trait> traits = geneticsMgr.getTraits(animal);
        if (traits.isEmpty()) {
            label = label.append(Component.newline())
                .append(Component.text("Traits: none", NamedTextColor.DARK_GRAY));
        } else {
            Component traitsHeader = Component.text("Traits:", NamedTextColor.WHITE)
                .decoration(TextDecoration.BOLD, true);
            label = label.append(Component.newline()).append(traitsHeader);
            for (GeneticsManager.Trait t : traits) {
                label = label.append(Component.newline())
                    .append(Component.text("  " + t.color + "• " + t.displayName, NamedTextColor.WHITE));
            }
        }

        return label;
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /** Called by TraitListener and BreedingListener when an animal dies or is removed. */
    public void removeDisplayForAnimal(UUID animalId) {
        Map<Integer, UUID> tierMap = animalTierDisplays.remove(animalId);
        if (tierMap == null) return;
        for (UUID displayId : tierMap.values()) {
            Entity e = plugin.getServer().getEntity(displayId);
            if (e != null) e.remove();
        }
    }

    public void cleanup() {
        for (Map<Integer, UUID> tierMap : animalTierDisplays.values()) {
            for (UUID dId : tierMap.values()) {
                Entity e = plugin.getServer().getEntity(dId);
                if (e != null) e.remove();
            }
        }
        animalTierDisplays.clear();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Location labelLocation(Animals animal) {
        return animal.getLocation().add(0, animal.getHeight() + LABEL_Y_OFFSET, 0);
    }

    private int bestOppositePartnerFed(Animals animal, BreedingManager manager,
                                        GenderManager genderManager) {
        return animal.getNearbyEntities(30, 10, 30).stream()
            .filter(e -> e instanceof Animals a
                && a.getType() == animal.getType()
                && a.isAdult()
                && !a.getUniqueId().equals(animal.getUniqueId())
                && !manager.isOnCooldown(a)
                && genderManager.areOpposingGenders(animal, a))
            .mapToInt(e -> manager.getFedAmount((Animals) e))
            .max().orElse(0);
    }
}

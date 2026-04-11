package com.minecraftcivilizations.specialization.Reinforcement;

import org.bukkit.util.Vector;

import java.util.Objects;

/**
 * Represents a reinforced block in the world.
 * <p>
 * equals/hashCode are based on location only, so a Set&lt;Reinforcement&gt;
 * can hold at most one reinforcement per block position.
 */
public class Reinforcement {

    private final Vector location;
    private final ReinforcementTier tier;
    private long placedAtTick;

    public Reinforcement(Vector location, ReinforcementTier tier, long placedAtTick) {
        this.location = location;
        this.tier = tier;
        this.placedAtTick = placedAtTick;
    }

    public Vector getLocation() {
        return location;
    }

    public ReinforcementTier getTier() {
        return tier;
    }

    public long getPlacedAtTick() {
        return placedAtTick;
    }

    public void setPlacedAtTick(long placedAtTick) {
        this.placedAtTick = placedAtTick;
    }

    // ---- Equality by location only ----

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reinforcement that)) return false;
        return location.getBlockX() == that.location.getBlockX()
                && location.getBlockY() == that.location.getBlockY()
                && location.getBlockZ() == that.location.getBlockZ();
    }

    @Override
    public int hashCode() {
        return Objects.hash(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    @Override
    public String toString() {
        return "Reinforcement{" +
                "location=" + location +
                ", tier=" + tier +
                ", placedAtTick=" + placedAtTick +
                '}';
    }
}

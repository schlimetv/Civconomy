package com.minecraftcivilizations.specialization.Reinforcement;

public enum ReinforcementTier {

    WOODEN(0),
    LIGHT(1),
    HEAVY(2);

    private final int rank;

    ReinforcementTier(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    /**
     * Returns true if this tier is strictly higher than the other.
     */
    public boolean isHigherThan(ReinforcementTier other) {
        return this.rank > other.rank;
    }

    /**
     * Returns true if this tier grants fire protection.
     */
    public boolean isFireproof() {
        return this != WOODEN;
    }

    /**
     * Returns true if this tier requires a pickaxe to break.
     */
    public boolean requiresPickaxe() {
        return this != WOODEN;
    }

    /**
     * Returns true if this tier has blast resistance.
     */
    public boolean hasBlastResistance() {
        return this != WOODEN;
    }

    /**
     * Returns true if this tier slows down block breaking.
     */
    public boolean slowsBreaking() {
        return this != WOODEN;
    }
}

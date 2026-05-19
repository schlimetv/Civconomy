package com.animalbreeding.plugin.models;

import org.bukkit.entity.EntityType;
import java.util.UUID;

public record BreedingData(UUID playerUuid, EntityType animalType, long timestamp) {

    public boolean isRareTrait(double chance) {
        return Math.random() < chance;
    }
}

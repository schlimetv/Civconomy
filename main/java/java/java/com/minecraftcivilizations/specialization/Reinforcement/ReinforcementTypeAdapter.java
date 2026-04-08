package com.minecraftcivilizations.specialization.Reinforcement;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.bukkit.util.Vector;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Handles serialization and deserialization of {@link Reinforcement} objects,
 * including backward compatibility with the old format that used a boolean {@code isHeavy}
 * field and had no {@code tier} or {@code placedAtTick}.
 * <p>
 * Old format:  {@code {"location":{"x":1,"y":2,"z":3},"isHeavy":true}}
 * <br>
 * New format:  {@code {"location":{"x":1,"y":2,"z":3},"tier":"HEAVY","placedAtTick":48000}}
 */
public class ReinforcementTypeAdapter implements JsonSerializer<Reinforcement>, JsonDeserializer<Reinforcement> {

    /**
     * Sentinel value meaning "migrated from old format, needs a fresh timestamp".
     * The decay task will replace this with the current world time on first encounter.
     */
    public static final long MIGRATED_SENTINEL = -1L;

    @Override
    public JsonElement serialize(Reinforcement src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.add("location", context.serialize(src.getLocation()));
        obj.addProperty("tier", src.getTier().name());
        obj.addProperty("placedAtTick", src.getPlacedAtTick());
        return obj;
    }

    @Override
    public Reinforcement deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        Vector location = context.deserialize(obj.get("location"), Vector.class);

        ReinforcementTier tier;
        long placedAtTick;

        if (obj.has("tier")) {
            // ---- New format ----
            tier = ReinforcementTier.valueOf(obj.get("tier").getAsString());
            placedAtTick = obj.has("placedAtTick") ? obj.get("placedAtTick").getAsLong() : MIGRATED_SENTINEL;
        } else {
            // ---- Old format migration ----
            boolean isHeavy = obj.has("isHeavy") && obj.get("isHeavy").getAsBoolean();
            tier = isHeavy ? ReinforcementTier.HEAVY : ReinforcementTier.LIGHT;
            placedAtTick = MIGRATED_SENTINEL;
        }

        return new Reinforcement(location, tier, placedAtTick);
    }
}

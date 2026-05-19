"""Per-biome vein assignment. Derives which ore types a biome should have
veins for by reading the vanilla `minecraft:ore_*` placed-feature references
already present in each BDR biome JSON's features[6] step.

This is robust against the original `/data/` source folder being missing —
BDR's own biome JSONs preserve the original ore distribution via their
vanilla ore feature references.

Idempotent: strips any existing immersive_ores:vein_* refs first, then
appends one placed-feature per (ore, size) in ORE_SIZES."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
BDR_BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

# Map vanilla placed-feature ids to ore types.
VANILLA_FEATURE_TO_ORE = {
    "minecraft:ore_coal_upper":      "coal",
    "minecraft:ore_coal_lower":      "coal",
    "minecraft:ore_coal_buried":     "coal",
    "minecraft:ore_copper":          "copper",
    "minecraft:ore_iron_upper":      "iron",
    "minecraft:ore_iron_middle":     "iron",
    "minecraft:ore_iron_small":      "iron",
    "minecraft:ore_gold":            "gold",
    "minecraft:ore_gold_lower":      "gold",
    "minecraft:ore_gold_extra":      "gold",
    "minecraft:ore_lapis":           "lapis",
    "minecraft:ore_lapis_buried":    "lapis",
    "minecraft:ore_redstone":        "redstone",
    "minecraft:ore_redstone_lower":  "redstone",
    "minecraft:ore_diamond":         "diamond",
    "minecraft:ore_diamond_medium":  "diamond",
    "minecraft:ore_diamond_large":   "diamond",
    "minecraft:ore_diamond_buried":  "diamond",
    "minecraft:ore_emerald":         "emerald",
}

# Ore -> available vein sizes (keep in sync with _rebuild_vein_features.py)
ORE_SIZES = {
    "coal":     ["average", "large", "huge"],
    "copper":   ["average", "large"],
    "gold":     ["tiny", "small", "average", "large"],
    "lapis":    ["tiny", "small", "average"],
    "redstone": ["tiny", "small", "average"],
    "emerald":  ["tiny", "small", "average"],
    "iron":     ["tiny", "small", "average", "large", "huge"],
    "diamond":  ["tiny"],
}


def collect_ores_from_biome(biome_data: dict) -> set[str]:
    ores: set[str] = set()
    features = biome_data.get("features")
    if not isinstance(features, list):
        return ores
    for step in features:
        if not isinstance(step, list):
            continue
        for fid in step:
            if isinstance(fid, str) and fid in VANILLA_FEATURE_TO_ORE:
                ores.add(VANILLA_FEATURE_TO_ORE[fid])
    return ores


def vein_ids_for(ores: list[str]) -> list[str]:
    out = []
    for ore in sorted(ores):
        for size in ORE_SIZES.get(ore, []):
            out.append(f"immersive_ores:vein_{ore}_{size}")
    return out


def main():
    edited = 0
    no_ore_biomes = []
    for bdr_path in sorted(BDR_BIOME_DIR.glob("*.json")):
        biome_name = bdr_path.stem
        with bdr_path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features")
        if not isinstance(features, list) or len(features) < 7:
            print(f"SKIP (no features[6]): {biome_name}")
            continue
        if not isinstance(features[6], list):
            print(f"SKIP (features[6] not list): {biome_name}")
            continue

        ores = sorted(collect_ores_from_biome(data))
        if not ores:
            no_ore_biomes.append(biome_name)

        # Strip any existing vein_* refs (idempotency)
        for idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            features[idx] = [x for x in step
                             if not (isinstance(x, str) and x.startswith("immersive_ores:vein_"))]

        new_ids = vein_ids_for(ores)
        existing6 = set(features[6])
        for nid in new_ids:
            if nid not in existing6:
                features[6].append(nid)

        with bdr_path.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        edited += 1
        print(f"  {biome_name}: [{', '.join(ores) if ores else '(none)'}] -> {len(new_ids)} placed features")

    if no_ore_biomes:
        print(f"\nBiomes with NO ore veins (no vanilla minecraft:ore_* refs):")
        for b in no_ore_biomes:
            print(f"  - {b}")
    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

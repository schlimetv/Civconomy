"""For every biome JSON:
  1. Detect which mineable ores it originally had via its vanilla
     minecraft:ore_* references.
  2. Strip every vanilla mineable-ore reference from all feature steps
     (leaves disk_sand/gravel/clay, ore_dirt/granite/etc. — those are
     stone/dirt variants, not minable ores).
  3. Inject surface_boulder_<ore> placed features into features[9]
     (vegetal decoration) for each non-diamond ore the biome had.
"""
import json
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

# Vanilla mineable-ore features → ore name. These are the ones we strip.
VANILLA_ORE_TO_TYPE = {
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

VANILLA_ORE_SET = set(VANILLA_ORE_TO_TYPE.keys())

# Ores that get a surface boulder (diamond excluded).
BOULDER_ORES = {"coal", "copper", "gold", "iron", "lapis", "redstone", "emerald"}


def main():
    edited = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features")
        if not isinstance(features, list):
            print(f"SKIP (no features): {bp.name}")
            continue

        # Pass 1: detect ore types from existing vanilla refs.
        ores_present: set[str] = set()
        for step in features:
            if not isinstance(step, list):
                continue
            for fid in step:
                if isinstance(fid, str) and fid in VANILLA_ORE_TO_TYPE:
                    ores_present.add(VANILLA_ORE_TO_TYPE[fid])

        # Pass 2: strip vanilla mineable-ore refs everywhere.
        stripped = 0
        for idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            new_step = [x for x in step
                        if not (isinstance(x, str) and x in VANILLA_ORE_SET)]
            stripped += len(step) - len(new_step)
            features[idx] = new_step

        # Pass 3: ensure features[9] exists and inject surface boulders for non-diamond ores.
        while len(features) < 10:
            features.append([])
        if not isinstance(features[9], list):
            features[9] = []
        existing = set(features[9])
        added = []
        for ore in sorted(ores_present & BOULDER_ORES):
            ref = f"immersive_ores:surface_boulder_{ore}"
            if ref not in existing:
                features[9].append(ref)
                added.append(ore)

        with bp.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        edited += 1
        ores_str = ", ".join(sorted(ores_present)) if ores_present else "(none)"
        added_str = ", ".join(added) if added else "(none)"
        print(f"  {bp.stem:<32}  stripped {stripped:>2} vanilla ores;  had: [{ores_str}];  added boulders: [{added_str}]")

    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

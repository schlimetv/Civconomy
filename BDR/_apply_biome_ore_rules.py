"""Apply per-biome ore restrictions per spec.

Rules:
  - Jungles (jungle, sparse_jungle, bamboo_jungle): remove diamond.
  - Forests (forest, birch_forest, old_growth_birch_forest, dark_forest,
    flower_forest, pale_garden, cherry_grove): remove iron entirely.
  - Forests EXCEPT cherry_grove and dark_forest: also remove redstone.
  - Plains group (plains, sunflower_plains, meadow): remove redstone.
  - Savannas (savanna, windswept_savanna): remove lapis.
    Plateau keeps lapis.
  - All savanna biomes (savanna, savanna_plateau, windswept_savanna):
    gold veins restricted to tiny + small (remove average, large).
  - Mesa/desert (badlands, eroded_badlands, wooded_badlands, desert):
    gold veins restricted to average + large (remove tiny, small).
  - Taiga/swamp (taiga, old_growth_pine_taiga, old_growth_spruce_taiga,
    swamp, mangrove_swamp): iron restricted to tiny + small (remove
    average, large, huge).
  - Snowy (snowy_plains, snowy_taiga, snowy_beach, snowy_slopes,
    ice_spikes, frozen_peaks, frozen_river): iron restricted to
    average + large + huge (remove tiny, small).

  Surface boulders for an ore are removed when the ore is removed
  entirely. Restriction rules (size limits) keep the boulder.
"""
import json
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

JUNGLES = {"jungle", "sparse_jungle", "bamboo_jungle"}
FORESTS_ALL = {"forest", "birch_forest", "old_growth_birch_forest",
               "dark_forest", "flower_forest", "pale_garden", "cherry_grove"}
FORESTS_LOSE_REDSTONE = FORESTS_ALL - {"cherry_grove", "dark_forest"}
PLAINS_LOSE_REDSTONE = {"plains", "sunflower_plains", "meadow"}
SAVANNAS_LOSE_LAPIS = {"savanna", "windswept_savanna"}
SAVANNAS_ALL = {"savanna", "savanna_plateau", "windswept_savanna"}
MESA_DESERT = {"badlands", "eroded_badlands", "wooded_badlands", "desert"}
TAIGA_SWAMP = {"taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
               "swamp", "mangrove_swamp"}
SNOWY = {"snowy_plains", "snowy_taiga", "snowy_beach", "snowy_slopes",
         "ice_spikes", "frozen_peaks", "frozen_river"}

VEIN_SIZES = ["tiny", "small", "average", "large", "huge"]


def vein(ore: str, size: str) -> str:
    return f"immersive_ores:vein_{ore}_{size}"


def boulder(ore: str) -> str:
    return f"immersive_ores:surface_boulder_{ore}"


def remove_ore_entirely(remove: set, ore: str, all_sizes: list = VEIN_SIZES):
    for s in all_sizes:
        remove.add(vein(ore, s))
    remove.add(boulder(ore))


def edits_for(biome: str) -> set[str]:
    """Return set of feature_ids to remove from this biome."""
    remove = set()
    if biome in JUNGLES:
        # diamond has only tiny + no boulder, but remove both for safety
        remove_ore_entirely(remove, "diamond")
    if biome in FORESTS_ALL:
        remove_ore_entirely(remove, "iron")
    if biome in FORESTS_LOSE_REDSTONE:
        remove_ore_entirely(remove, "redstone")
    if biome in PLAINS_LOSE_REDSTONE:
        remove_ore_entirely(remove, "redstone")
    if biome in SAVANNAS_LOSE_LAPIS:
        remove_ore_entirely(remove, "lapis")
    if biome in SAVANNAS_ALL:
        # Gold: tiny+small only → remove average and large vein
        remove.add(vein("gold", "average"))
        remove.add(vein("gold", "large"))
    if biome in MESA_DESERT:
        # Gold: average+large only → remove tiny and small vein
        remove.add(vein("gold", "tiny"))
        remove.add(vein("gold", "small"))
    if biome in TAIGA_SWAMP:
        # Iron: tiny+small only → remove average/large/huge vein
        remove.add(vein("iron", "average"))
        remove.add(vein("iron", "large"))
        remove.add(vein("iron", "huge"))
    if biome in SNOWY:
        # Iron: average+large+huge only → remove tiny+small vein
        remove.add(vein("iron", "tiny"))
        remove.add(vein("iron", "small"))
    return remove


def main():
    edited = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        biome = bp.stem
        to_remove = edits_for(biome)
        if not to_remove:
            continue
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features", [])
        if not isinstance(features, list):
            continue
        actually_removed = []
        for idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            new_step = []
            for x in step:
                if isinstance(x, str) and x in to_remove:
                    actually_removed.append(x)
                else:
                    new_step.append(x)
            features[idx] = new_step
        with bp.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        edited += 1
        print(f"  {biome}: removed {len(actually_removed)} refs ({sorted(set(actually_removed))})")
    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

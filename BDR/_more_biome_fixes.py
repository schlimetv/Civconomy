"""Additional biome edits:
  - river: strip all ores AND boulders.
  - frozen_river: strip all boulders (keep ore veins per snowy rule).
  - beach, snowy_beach, stony_shore: strip all ores AND boulders.
  - sparse_jungle: match jungle exactly (only coal + copper) — strip gold,
    iron, lapis, redstone.
  - Boulder ordering: in every biome's features[9] step, move any
    surface_boulder_* entries to the position just BEFORE the first
    feature whose id contains 'grass'. This prevents short grass from
    spawning on top of boulders.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

GRASS_PATTERN = re.compile(r"grass", re.IGNORECASE)

NO_ORE_OR_BOULDER = {"river", "beach", "snowy_beach", "stony_shore"}
NO_BOULDER_ONLY = {"frozen_river"}
SPARSE_JUNGLE_STRIP = {"gold", "iron", "lapis", "redstone"}

VEIN_SIZES = ["tiny", "small", "average", "large", "huge"]


def vein(ore, size):
    return f"immersive_ores:vein_{ore}_{size}"


def boulder(ore):
    return f"immersive_ores:surface_boulder_{ore}"


def is_boulder(fid):
    return isinstance(fid, str) and fid.startswith("immersive_ores:surface_boulder_")


def is_vein(fid):
    return isinstance(fid, str) and fid.startswith("immersive_ores:vein_")


def reorder_boulders_before_grass(features):
    """In features[9] (vegetal decoration), move any surface_boulder_* refs
    to just before the first grass-related feature. If no grass feature
    exists, leave them where they are."""
    if len(features) < 10 or not isinstance(features[9], list):
        return False
    step = features[9]
    boulder_refs = [x for x in step if is_boulder(x)]
    if not boulder_refs:
        return False
    # Strip them out first
    rest = [x for x in step if not is_boulder(x)]
    # Find first grass-feature index in `rest`
    grass_idx = None
    for i, fid in enumerate(rest):
        if isinstance(fid, str) and GRASS_PATTERN.search(fid):
            grass_idx = i
            break
    if grass_idx is None:
        # No grass feature — put boulders at the front so they precede
        # any vegetation that might land on them.
        new_step = boulder_refs + rest
    else:
        new_step = rest[:grass_idx] + boulder_refs + rest[grass_idx:]
    changed = new_step != step
    features[9] = new_step
    return changed


def strip_all_ore_refs(features):
    removed = 0
    for idx, step in enumerate(features):
        if not isinstance(step, list):
            continue
        new_step = [x for x in step if not (is_vein(x) or is_boulder(x))]
        removed += len(step) - len(new_step)
        features[idx] = new_step
    return removed


def strip_only_boulders(features):
    removed = 0
    for idx, step in enumerate(features):
        if not isinstance(step, list):
            continue
        new_step = [x for x in step if not is_boulder(x)]
        removed += len(step) - len(new_step)
        features[idx] = new_step
    return removed


def strip_ores_for_sparse_jungle(features):
    """Remove gold, iron, lapis, redstone (veins + boulders) from sparse_jungle
    to match the jungle ore profile (coal + copper only)."""
    targets = set()
    for ore in SPARSE_JUNGLE_STRIP:
        for size in VEIN_SIZES:
            targets.add(vein(ore, size))
        targets.add(boulder(ore))
    removed = 0
    for idx, step in enumerate(features):
        if not isinstance(step, list):
            continue
        new_step = [x for x in step if x not in targets]
        removed += len(step) - len(new_step)
        features[idx] = new_step
    return removed


def main():
    edited = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        biome = bp.stem
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features", [])
        if not isinstance(features, list):
            continue
        changed = False
        actions = []

        if biome in NO_ORE_OR_BOULDER:
            n = strip_all_ore_refs(features)
            if n > 0:
                actions.append(f"stripped all ore/boulder refs ({n})")
                changed = True
        elif biome in NO_BOULDER_ONLY:
            n = strip_only_boulders(features)
            if n > 0:
                actions.append(f"stripped {n} boulders")
                changed = True
        elif biome == "sparse_jungle":
            n = strip_ores_for_sparse_jungle(features)
            if n > 0:
                actions.append(f"stripped {n} sparse-jungle non-coal/copper refs")
                changed = True

        # Reorder remaining boulders to be before grass.
        if reorder_boulders_before_grass(features):
            actions.append("reordered boulders before grass")
            changed = True

        if changed:
            with bp.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            edited += 1
            print(f"  {biome}: {'; '.join(actions)}")
    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

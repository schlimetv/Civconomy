"""In each mountain biome, rewrite features[*] entries for coal/copper/gold/iron
to reference the `_mountain` placed_feature variant (which extends y_max from
384 to 512). Non-mountain biomes are left untouched.

Mountain biome rule (per user spec):
  - biome name ends in 'peaks'   -> frozen_peaks, jagged_peaks, stony_peaks
  - biome name starts with 'windswept' ->
       windswept_hills, windswept_gravelly_hills, windswept_forest,
       windswept_savanna
  - biome name is a badlands variant -> badlands, eroded_badlands,
       wooded_badlands

Only ores in MOUNTAIN_ORES are rewritten. Other ore veins, boulders, and
non-immersive features are untouched.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

MOUNTAIN_ORES = {"coal", "copper", "gold", "iron"}
BADLANDS = {"badlands", "eroded_badlands", "wooded_badlands"}

VEIN_RE = re.compile(r"^immersive_ores:vein_([a-z]+)_([a-z]+)$")


def is_mountain_biome(name: str) -> bool:
    if name.endswith("peaks"):
        return True
    if name.startswith("windswept"):
        return True
    if name in BADLANDS:
        return True
    return False


def rewrite_feature_id(fid: str) -> str | None:
    """Return the rewritten id if it's a mountain-eligible vein, else None."""
    if not isinstance(fid, str):
        return None
    m = VEIN_RE.match(fid)
    if not m:
        return None
    ore, size = m.group(1), m.group(2)
    if ore not in MOUNTAIN_ORES:
        return None
    return f"immersive_ores:vein_{ore}_{size}_mountain"


def main():
    edited = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        biome = bp.stem
        if not is_mountain_biome(biome):
            continue
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features", [])
        if not isinstance(features, list):
            continue
        rewrites = []
        for idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            new_step = []
            for entry in step:
                replacement = rewrite_feature_id(entry)
                if replacement is not None and not entry.endswith("_mountain"):
                    new_step.append(replacement)
                    rewrites.append(f"{entry} -> {replacement}")
                else:
                    new_step.append(entry)
            features[idx] = new_step
        if rewrites:
            with bp.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            edited += 1
            print(f"  {biome}: {len(rewrites)} rewrites")
            for r in rewrites:
                print(f"      {r}")
    print(f"\nDone. {edited} mountain biome JSON(s) updated.")


if __name__ == "__main__":
    main()

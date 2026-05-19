"""Fix vanilla's 'feature order cycle' error by ensuring all surface_boulder_*
references appear at the SAME relative position across every biome's
features[9] step: namely, position 0 (very first). This way no other
feature can be both before and after a boulder across biomes."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"


def is_boulder(fid):
    return isinstance(fid, str) and fid.startswith("immersive_ores:surface_boulder_")


def main():
    edited = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features", [])
        if not isinstance(features, list) or len(features) < 10:
            continue
        step = features[9]
        if not isinstance(step, list):
            continue
        boulders = [x for x in step if is_boulder(x)]
        if not boulders:
            continue
        rest = [x for x in step if not is_boulder(x)]
        # Sort boulders so that ACROSS biomes they appear in the same internal
        # order — alphabetical is a stable canonical sort.
        boulders_sorted = sorted(boulders)
        new_step = boulders_sorted + rest
        if new_step == step:
            continue
        features[9] = new_step
        with bp.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        edited += 1
        print(f"  {bp.stem}: boulders prepended in alphabetical order ({len(boulders_sorted)})")
    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

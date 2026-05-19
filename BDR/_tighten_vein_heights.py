"""One-shot: tighten max_inclusive Y of all vein placed features so geodes
spawn well below sea level (y<=50 or lower per ore), preventing surface and
in-air placement. Idempotent — only changes max_inclusive."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
PLACED_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "placed_feature"

# Per-ore (min, max) absolute Y bounds. Max stays well below typical sea
# level (y=62) so geodes don't poke the surface.
ORE_Y = {
    "coal":     (-64, 50),
    "copper":   (-16, 50),
    "gold":     (-64, 32),
    "lapis":    (-64, 50),
    "redstone": (-64, 16),
    "emerald":  (-16, 50),
    "iron":     (-64, 50),
    "diamond":  (-64, 16),
}


def ore_for(path: Path) -> str | None:
    name = path.stem  # vein_iron_average
    if not name.startswith("vein_"):
        return None
    parts = name.split("_")
    if len(parts) < 3:
        return None
    return parts[1]


def main():
    edited = 0
    for path in sorted(PLACED_DIR.glob("vein_*.json")):
        ore = ore_for(path)
        if ore not in ORE_Y:
            print(f"SKIP (unknown ore): {path.name}")
            continue
        min_y, max_y = ORE_Y[ore]

        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        placement = data.get("placement")
        if not isinstance(placement, list):
            print(f"SKIP (no placement): {path.name}")
            continue
        changed = False
        for mod in placement:
            if not isinstance(mod, dict): continue
            if mod.get("type") != "minecraft:height_range": continue
            h = mod.get("height")
            if not isinstance(h, dict): continue
            new_min = {"absolute": min_y}
            new_max = {"absolute": max_y}
            if h.get("min_inclusive") != new_min or h.get("max_inclusive") != new_max:
                h["min_inclusive"] = new_min
                h["max_inclusive"] = new_max
                changed = True
        if changed:
            with path.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            edited += 1
            print(f"EDITED: {path.name} -> y [{min_y}, {max_y}]")

    print(f"\nDone. {edited} placed feature(s) updated.")


if __name__ == "__main__":
    main()

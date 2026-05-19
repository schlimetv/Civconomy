"""Copy the `effects.ambient_sound` block from `BDR ocean sounds/`'s ocean
biome JSONs into the equivalent BDR/ biome JSONs."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
SRC_DIR = ROOT.parent / "BDR ocean sounds" / "data" / "minecraft" / "worldgen" / "biome"
DST_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

OCEAN_BIOMES = [
    "ocean",
    "cold_ocean",
    "deep_cold_ocean",
    "deep_frozen_ocean",
    "deep_lukewarm_ocean",
    "deep_ocean",
    "frozen_ocean",
    "lukewarm_ocean",
    "warm_ocean",
]


def main():
    edited = 0
    for name in OCEAN_BIOMES:
        src = SRC_DIR / f"{name}.json"
        dst = DST_DIR / f"{name}.json"
        if not src.exists():
            print(f"SKIP (no source): {name}")
            continue
        if not dst.exists():
            print(f"SKIP (no destination): {name}")
            continue

        with src.open("r", encoding="utf-8") as f:
            src_data = json.load(f)
        amb = src_data.get("effects", {}).get("ambient_sound")
        if amb is None:
            print(f"SKIP (no ambient_sound in source): {name}")
            continue

        with dst.open("r", encoding="utf-8") as f:
            dst_data = json.load(f)
        if "effects" not in dst_data or not isinstance(dst_data["effects"], dict):
            print(f"SKIP (no effects block in destination): {name}")
            continue

        current = dst_data["effects"].get("ambient_sound")
        if current == amb:
            print(f"NOOP (already matches): {name}")
            continue

        dst_data["effects"]["ambient_sound"] = amb
        with dst.open("w", encoding="utf-8") as f:
            json.dump(dst_data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        edited += 1
        print(f"EDITED: {name}  ambient_sound -> {amb}")

    print(f"\nDone. {edited} biome JSONs updated.")


if __name__ == "__main__":
    main()

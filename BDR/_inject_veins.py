"""One-shot script: append miner-overhaul vein placed features to each
overworld biome JSON's features[6] (underground_ores) step. Idempotent —
re-running won't duplicate entries. Delete this file after running."""
import json
import os
from pathlib import Path

BIOME_DIR = Path(__file__).parent / "data" / "minecraft" / "worldgen" / "biome"

NEW_FEATURES = [
    "immersive_ores:vein_coal_average",
    "immersive_ores:vein_coal_large",
    "immersive_ores:vein_copper_average",
    "immersive_ores:vein_copper_large",
    "immersive_ores:vein_gold_tiny",
    "immersive_ores:vein_gold_small",
    "immersive_ores:vein_gold_average",
    "immersive_ores:vein_lapis_tiny",
    "immersive_ores:vein_lapis_small",
    "immersive_ores:vein_lapis_average",
    "immersive_ores:vein_redstone_tiny",
    "immersive_ores:vein_redstone_small",
    "immersive_ores:vein_redstone_average",
    "immersive_ores:vein_emerald_tiny",
    "immersive_ores:vein_emerald_small",
    "immersive_ores:vein_emerald_average",
    "immersive_ores:vein_iron_tiny",
    "immersive_ores:vein_iron_small",
    "immersive_ores:vein_iron_average",
    "immersive_ores:vein_iron_large",
    "immersive_ores:vein_diamond_tiny",
]


def main():
    edited = 0
    skipped = 0
    for path in sorted(BIOME_DIR.glob("*.json")):
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features")
        if not isinstance(features, list) or len(features) < 7:
            print(f"SKIP (no features[6]): {path.name}")
            skipped += 1
            continue
        step6 = features[6]
        if not isinstance(step6, list):
            print(f"SKIP (features[6] not list): {path.name}")
            skipped += 1
            continue
        existing = set(step6)
        added_any = False
        for nf in NEW_FEATURES:
            if nf not in existing:
                step6.append(nf)
                added_any = True
        if added_any:
            with path.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            edited += 1
            print(f"EDITED: {path.name}")
        else:
            print(f"NOOP: {path.name}")
    print(f"\nDone. {edited} edited, {skipped} skipped.")


if __name__ == "__main__":
    main()

"""Generate the 7 surface-boulder configured_feature + placed_feature JSONs.
A "boulder" is a tight cluster of 4-8 ore blocks placed on top of the
terrain surface during the vegetal-decoration step. Diamond is excluded by
spec — diamond only spawns as geodes deep underground."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
CF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "configured_feature"
PF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "placed_feature"

ORES = ["coal", "copper", "gold", "iron", "lapis", "redstone", "emerald"]

ORE_BLOCK = {
    "coal":     "minecraft:coal_ore",
    "copper":   "minecraft:copper_ore",
    "gold":     "minecraft:gold_ore",
    "iron":     "minecraft:iron_ore",
    "lapis":    "minecraft:lapis_ore",
    "redstone": "minecraft:redstone_ore",
    "emerald":  "minecraft:emerald_ore",
}

# Per-ore rarity (1/N chance per chunk attempt).
RARITY = {
    "coal":     24,
    "copper":   32,
    "gold":     64,
    "iron":     32,
    "lapis":    64,
    "redstone": 48,
    "emerald":  96,
}


def configured(ore: str) -> dict:
    return {
        "type": "minecraft:random_patch",
        "config": {
            "tries": 8,
            "xz_spread": 1,
            # y_spread=2 lets the patch reach the actual top solid block
            # whether terrain dips or rises by 1-2 blocks across neighbor xz
            # tiles. The "solid + air-above" predicate then selects only the
            # true surface block at each xz, so the boulder hugs terrain.
            "y_spread": 2,
            "feature": {
                "feature": {
                    "type": "minecraft:simple_block",
                    "config": {
                        "to_place": {
                            "type": "minecraft:simple_state_provider",
                            "state": {"Name": ORE_BLOCK[ore]},
                        }
                    },
                },
                "placement": [
                    {
                        "type": "minecraft:block_predicate_filter",
                        "predicate": {
                            # Place only on the top surface block:
                            #  - current block is solid (we OVERRIDE the
                            #    surface grass/dirt/stone with the ore block),
                            #  - block directly above is air (so we're at the
                            #    exposed top of the terrain — not buried, not
                            #    under leaves, not under water).
                            "type": "minecraft:all_of",
                            "predicates": [
                                {"type": "minecraft:solid"},
                                {
                                    "type": "minecraft:matching_blocks",
                                    "blocks": ["minecraft:air"],
                                    "offset": [0, 1, 0],
                                },
                            ],
                        },
                    }
                ],
            },
        },
    }


def placed(ore: str) -> dict:
    return {
        "feature": f"immersive_ores:surface_boulder_{ore}",
        "placement": [
            {"type": "minecraft:rarity_filter", "chance": RARITY[ore]},
            {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
            {"type": "minecraft:biome"},
        ],
    }


def main():
    for ore in ORES:
        cf_path = CF_DIR / f"surface_boulder_{ore}.json"
        pf_path = PF_DIR / f"surface_boulder_{ore}.json"
        with cf_path.open("w", encoding="utf-8") as f:
            json.dump(configured(ore), f, indent=2, ensure_ascii=False)
            f.write("\n")
        with pf_path.open("w", encoding="utf-8") as f:
            json.dump(placed(ore), f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"  surface_boulder_{ore}: rarity {RARITY[ore]}")
    print(f"\nDone. {len(ORES)} configured_feature(s) and placed_feature(s) created.")


if __name__ == "__main__":
    main()

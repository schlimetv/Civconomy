"""One-shot: regenerate every vein configured_feature with vanilla-amethyst-like
geode parameters (so the shape is a proper blob, not an octant), and update
every vein placed_feature to (a) cap max Y at 40 and (b) add a
block_predicate_filter requiring solid material 16 blocks above origin so
geodes never poke through the surface."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
CF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "configured_feature"
PF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "placed_feature"

# Per-size geode shape params (matches vanilla amethyst structure)
SIZE_PARAMS = {
    # Calibrated via _grid_v2.py using the ACTUAL Mojang algorithm:
    # block placed when d7 >= d4 where
    #   d7 = sum over dp points: invSqrt(distSq + pointOffset) + noise
    #   d4 = 1 / sqrt(outer_layer + dp / wall_max)
    # Key insight: wall MUST be a range (not min==max) — single-value stacks
    # all distribution points at (wall,wall,wall), producing massively
    # oversized spheres (the previous diamond_tiny was actually ~3000 blocks).
    # With wall=(3,5) the 3 points scatter in a 3x3x3 cube of corner positions,
    # giving naturally irregular shapes AND correctly-sized spheres.
    "tiny":    {"layers": (0.6, 0.9, 1.1, 1.4), "wall": (3, 5), "dp": (3, 3), "point": (1, 1)},
    "small":   {"layers": (1.0, 1.5, 2.1, 2.9), "wall": (3, 5), "dp": (3, 3), "point": (1, 1)},
    "average": {"layers": (1.3, 2.2, 3.0, 4.3), "wall": (3, 5), "dp": (3, 3), "point": (1, 1)},
    "large":   {"layers": (1.9, 3.2, 4.4, 6.3), "wall": (3, 5), "dp": (3, 3), "point": (1, 1)},
    "huge":    {"layers": (2.4, 3.9, 5.5, 7.8), "wall": (3, 5), "dp": (3, 3), "point": (1, 1)},
}

# Per-ore Y bounds — non-mountain biomes. coal/copper/gold/iron cap at y=384
# normally and extend to MOUNTAIN_Y_MAX in mountain biomes (see below) via
# a separate `_mountain` placed_feature variant. The block_predicate_filter
# (solid block above) handles surface protection.
ORE_Y = {
    "coal":     (0, 384),
    "copper":   (-16, 384),
    "gold":     (-64, 384),
    "lapis":    (-64, 64),
    "redstone": (-64, 64),
    "emerald":  (-16, 256),
    "iron":     (-64, 384),
    "diamond":  (-64, 32),
}

# Ores that get an extra `_mountain` placed_feature variant with y_max=512.
# Mountain biomes (frozen/jagged/stony_peaks, windswept_*, badlands variants)
# wire to the `_mountain` variant in place of the standard one.
MOUNTAIN_VARIANT_ORES = {"coal", "copper", "gold", "iron"}
MOUNTAIN_Y_MAX = 512

# === DEBUG: force every vein to spawn at y=200 ===
# Set to an int to override the per-ore bounds in ORE_Y. All veins will spawn
# in air at the given height (the solid-block-above predicate filter is also
# skipped when this is set). Set back to None to revert to production heights.
DEBUG_SPAWN_Y = None

# Per-size base rarity (1/N chunks attempt rate). Lower = more common.
# Tiny doubled (was 48) to make tiny veins 2x rarer.
SIZE_RARITY = {"tiny": 96, "small": 96, "average": 192, "large": 384, "huge": 768}

# Per-ore rarity multiplier (applied to size base). Lower = more common.
#   Coal 0.5x (most common) > Iron/Gold/Copper 1x > Redstone/Lapis 2x >
#   Emerald 4x > Diamond 10x (rarest)
ORE_RARITY_MULT = {
    "coal":     0.5,
    "iron":     1.0,
    "gold":     1.0,
    "copper":   1.0,
    "redstone": 2.0,
    "lapis":    2.0,
    "emerald":  4.0,
    "diamond":  10.0,
}

# Per-(ore, size) explicit rarity override. Takes precedence over the
# SIZE_RARITY * ORE_RARITY_MULT product. Lower = more common.
#   ("coal", "huge"): huge coal made 1.5x rarer than the formula would give
#   (baseline 768 * 0.5 = 384  ->  576).
RARITY_OVERRIDE = {
    ("coal", "huge"): 576,
}

# (ore, sizes) -> generates the file matrix
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


def weighted_provider(ore: str) -> dict:
    return {
        "type": "minecraft:weighted_state_provider",
        "entries": [
            {"weight": 7, "data": {"Name": f"minecraft:{ore}_ore"}},
            {"weight": 3, "data": {"Name": f"minecraft:deepslate_{ore}_ore"}},
        ],
    }


def configured_feature(ore: str, size: str) -> dict:
    p = SIZE_PARAMS[size]
    f, i, m, o = p["layers"]
    wmin, wmax = p["wall"]
    dmin, dmax = p["dp"]
    pmin, pmax = p["point"]
    if DEBUG_SPAWN_Y is not None:
        # DEBUG: let the geode replace air so it's visible spawned at y=200.
        # The geode codec's HolderSet field only accepts a tag-reference
        # string, not an inline list — so we point at an empty tag (no
        # blocks match, everything is replaceable, threshold never fires).
        cannot_replace = "#immersive_ores:vein_debug_empty"
        invalid_blocks = "#immersive_ores:vein_debug_empty"
        invalid_thresh = 100000
    else:
        cannot_replace = "#immersive_ores:vein_ores_cannot_replace"
        invalid_blocks = "#immersive_ores:vein_ores_cannot_replace"
        invalid_thresh = 1
    return {
        "type": "minecraft:geode",
        "config": {
            "blocks": {
                "filling_provider": weighted_provider(ore),
                "inner_layer_provider": weighted_provider(ore),
                "alternate_inner_layer_provider": weighted_provider(ore),
                "middle_layer_provider": weighted_provider(ore),
                "outer_layer_provider": weighted_provider(ore),
                "inner_placements": [{"Name": f"minecraft:deepslate_{ore}_ore"}],
                "cannot_replace": cannot_replace,
                "invalid_blocks": invalid_blocks,
            },
            "layers": {
                "filling": f, "inner_layer": i, "middle_layer": m, "outer_layer": o,
            },
            "crack": {
                "generate_crack_chance": 0.0,
                "base_crack_size": 0.0,
                "crack_point_offset": 0,
            },
            "noise_multiplier": 0.10,
            "use_potential_placements_chance": 0.0,
            "use_alternate_layer0_chance": 0.0,
            "placements_require_layer0_alternate": False,
            "outer_wall_distance": {
                "type": "minecraft:uniform",
                "min_inclusive": wmin, "max_inclusive": wmax,
            },
            "distribution_points": {
                "type": "minecraft:uniform",
                "min_inclusive": dmin, "max_inclusive": dmax,
            },
            "point_offset": {
                "type": "minecraft:uniform",
                "min_inclusive": pmin, "max_inclusive": pmax,
            },
            "min_gen_offset": -16,
            "max_gen_offset": 16,
            "invalid_blocks_threshold": invalid_thresh,
        },
    }


def rarity_for(ore: str, size: str) -> int:
    override = RARITY_OVERRIDE.get((ore, size))
    if override is not None:
        return max(1, int(override))
    return max(1, round(SIZE_RARITY[size] * ORE_RARITY_MULT[ore]))


def placed_feature(ore: str, size: str, mountain: bool = False) -> dict:
    rarity = rarity_for(ore, size)
    if DEBUG_SPAWN_Y is not None:
        min_y, max_y = DEBUG_SPAWN_Y - 1, DEBUG_SPAWN_Y
    else:
        min_y, max_y = ORE_Y[ore]
        if mountain and ore in MOUNTAIN_VARIANT_ORES:
            max_y = MOUNTAIN_Y_MAX
    placement = [
        {"type": "minecraft:rarity_filter", "chance": rarity},
        {"type": "minecraft:in_square"},
        {
            "type": "minecraft:height_range",
            "height": {
                "type": "minecraft:uniform",
                "min_inclusive": {"absolute": min_y},
                "max_inclusive": {"absolute": max_y},
            },
        },
    ]
    # In production, reject placements that aren't well underground (require a
    # solid block above the origin). Skip this in DEBUG_SPAWN_Y mode so veins
    # actually spawn in the air at the debug height.
    if DEBUG_SPAWN_Y is None:
        placement.append({
            "type": "minecraft:block_predicate_filter",
            "predicate": {
                "type": "minecraft:solid",
                "offset": [0, 12, 0],
            },
        })
    placement.append({"type": "minecraft:biome"})
    return {
        "feature": f"immersive_ores:vein_{ore}_{size}",
        "placement": placement,
    }


def main():
    cf_count = 0
    pf_count = 0
    for ore, sizes in ORE_SIZES.items():
        for size in sizes:
            cf_path = CF_DIR / f"vein_{ore}_{size}.json"
            with cf_path.open("w", encoding="utf-8") as f:
                json.dump(configured_feature(ore, size), f, indent=2, ensure_ascii=False)
                f.write("\n")
            cf_count += 1

            pf_path = PF_DIR / f"vein_{ore}_{size}.json"
            with pf_path.open("w", encoding="utf-8") as f:
                json.dump(placed_feature(ore, size, mountain=False), f, indent=2, ensure_ascii=False)
                f.write("\n")
            pf_count += 1
            if DEBUG_SPAWN_Y is not None:
                ystr = f"y={DEBUG_SPAWN_Y} (DEBUG override; production y was {ORE_Y[ore]})"
            else:
                ystr = f"y [{ORE_Y[ore][0]}, {ORE_Y[ore][1]}]"
            rarity = rarity_for(ore, size)
            tag = " (override)" if (ore, size) in RARITY_OVERRIDE else ""
            print(f"  vein_{ore}_{size}: {ystr}, rarity {rarity}{tag}")

            # Mountain variant — separate placed_feature, same configured_feature.
            if ore in MOUNTAIN_VARIANT_ORES:
                pf_path_m = PF_DIR / f"vein_{ore}_{size}_mountain.json"
                with pf_path_m.open("w", encoding="utf-8") as f:
                    json.dump(placed_feature(ore, size, mountain=True), f, indent=2, ensure_ascii=False)
                    f.write("\n")
                pf_count += 1
                if DEBUG_SPAWN_Y is not None:
                    mystr = f"y={DEBUG_SPAWN_Y} (DEBUG; mountain production y_max={MOUNTAIN_Y_MAX})"
                else:
                    mystr = f"y [{ORE_Y[ore][0]}, {MOUNTAIN_Y_MAX}]"
                print(f"  vein_{ore}_{size}_mountain: {mystr}, rarity {rarity}{tag}")

    print(f"\nDone. {cf_count} configured_feature(s) and {pf_count} placed_feature(s) rewritten.")


if __name__ == "__main__":
    main()

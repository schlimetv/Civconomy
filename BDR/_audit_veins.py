"""Audit every vein configured_feature + placed_feature on disk: extract
geode params, run the simulator on each, and check biome wiring."""
import json
import math
import random
import statistics
from pathlib import Path

ROOT = Path(__file__).parent
CF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "configured_feature"
PF_DIR = ROOT / "data" / "immersive_ores" / "worldgen" / "placed_feature"
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"


def simulate(wall, dp, point, noise_mult, samples=15, gen_offset=16):
    rng = random.Random()
    threshold = wall * wall
    counts = []
    for _ in range(samples):
        pts = [(rng.randint(-point, point), rng.randint(-point, point), rng.randint(-point, point))
               for _ in range(dp)]
        count = 0
        for x in range(-gen_offset, gen_offset + 1):
            for y in range(-gen_offset, gen_offset + 1):
                for z in range(-gen_offset, gen_offset + 1):
                    md2 = min((x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2
                              for (px, py, pz) in pts)
                    noise = noise_mult * (rng.random() - 0.5) * 2.0 * threshold if noise_mult > 0 else 0
                    if md2 + noise < threshold:
                        count += 1
        counts.append(count)
    return counts


def main():
    print("=" * 100)
    print("CONFIGURED FEATURES — params + simulated block counts")
    print("=" * 100)
    print(f"  {'name':<32} {'wall':>5} {'dp':>3} {'pt':>3} {'noise':>6}   {'mean':>5}  {'min':>4}  {'max':>4}")
    rows = []
    for cf_path in sorted(CF_DIR.glob("vein_*.json")):
        with cf_path.open() as f:
            d = json.load(f)["config"]
        wall = d["outer_wall_distance"]["min_inclusive"]
        dp = d["distribution_points"]["min_inclusive"]
        point = d["point_offset"]["min_inclusive"]
        noise = d["noise_multiplier"]
        counts = simulate(wall, dp, point, noise, samples=12)
        mean = statistics.mean(counts)
        rows.append((cf_path.stem, wall, dp, point, noise, mean, min(counts), max(counts)))
        print(f"  {cf_path.stem:<32} {wall:>5} {dp:>3} {point:>3} {noise:>6}   "
              f"{mean:>5.0f}  {min(counts):>4}  {max(counts):>4}")

    print()
    print("=" * 100)
    print("PLACED FEATURES — feature ref, rarity, height range")
    print("=" * 100)
    for pf_path in sorted(PF_DIR.glob("vein_*.json")):
        with pf_path.open() as f:
            d = json.load(f)
        feat = d["feature"]
        placement = d["placement"]
        rarity = next((m["chance"] for m in placement if m.get("type") == "minecraft:rarity_filter"), None)
        hr = next((m for m in placement if m.get("type") == "minecraft:height_range"), None)
        h = hr["height"] if hr else None
        y_min = h["min_inclusive"].get("absolute") if h and "min_inclusive" in h else "?"
        y_max = h["max_inclusive"].get("absolute") if h and "max_inclusive" in h else "?"
        has_predicate = any(m.get("type") == "minecraft:block_predicate_filter" for m in placement)
        print(f"  {pf_path.stem:<32}  feature={feat:<45}  rarity={rarity:>4}  y=[{y_min}, {y_max}]  predicate={has_predicate}")

    print()
    print("=" * 100)
    print("BIOME WIRING — which sizes appear in biomes")
    print("=" * 100)
    seen_in_biomes: dict[str, int] = {}
    for bp in sorted(BIOME_DIR.glob("*.json")):
        with bp.open() as f:
            d = json.load(f)
        features = d.get("features", [])
        for step in features:
            if not isinstance(step, list):
                continue
            for fid in step:
                if isinstance(fid, str) and fid.startswith("immersive_ores:vein_"):
                    seen_in_biomes[fid] = seen_in_biomes.get(fid, 0) + 1
    for cf_path in sorted(CF_DIR.glob("vein_*.json")):
        full_id = f"immersive_ores:{cf_path.stem}"
        count = seen_in_biomes.get(full_id, 0)
        flag = "OK" if count > 0 else "*** NOT WIRED ***"
        print(f"  {cf_path.stem:<32}  used in {count:>3} biomes  {flag}")


if __name__ == "__main__":
    main()

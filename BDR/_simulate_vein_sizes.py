"""Simulate Mojang's GeodeFeature block-placement algorithm to predict vein
block counts WITHOUT running the server. Calibrated against in-game data:
  old Tiny  (wall=3, outer=2.8, dp=3, point=1) -> observed ~550 blocks
  old Avg   (wall=5, outer=4.3, dp=3, point=1) -> observed ~717 blocks

Model (best-fit to those observations):
  - Each geode generation samples wall, dp, point_offset from their uniform
    ranges (we use single-value ranges so it's deterministic per geode shape).
  - distribution_points are placed in a CUBE [-point_offset, point_offset]^3
    around origin (Mojang uses random.nextIntBetweenInclusive per axis).
  - For each block in [-min_gen_offset, max_gen_offset]^3, compute squared
    distance to NEAREST distribution point. The block is placed if that
    distance squared < matchedWall^2 — i.e. each distribution point grows
    a sphere of radius `wall` around itself. outer_layer is only relevant
    when filling_provider == air (vanilla amethyst), which it isn't here,
    so it doesn't affect volume in our solid-ore configuration.
  - noise_multiplier adds a small per-voxel jitter to the distance check.
"""
import math
import random
import statistics
from typing import Tuple


def simulate_geode(wall: int, outer_layer: float, distribution_points: int,
                   point_offset: int, min_gen_offset: int = -16,
                   max_gen_offset: int = 16, noise_multiplier: float = 0.0,
                   samples: int = 30, seed: int | None = None) -> list[int]:
    """Run the geode placement and return a list of block counts (one per sample)."""
    rng = random.Random(seed)
    threshold = wall * wall  # matchedWall^2 per Mojang GeodeFeature.place
    counts = []
    for _ in range(samples):
        # Place distribution points in cube around origin.
        points = []
        for _ in range(distribution_points):
            px = rng.randint(-point_offset, point_offset)
            py = rng.randint(-point_offset, point_offset)
            pz = rng.randint(-point_offset, point_offset)
            points.append((px, py, pz))

        # Iterate blocks in the gen-offset cube.
        count = 0
        for x in range(min_gen_offset, max_gen_offset + 1):
            for y in range(min_gen_offset, max_gen_offset + 1):
                for z in range(min_gen_offset, max_gen_offset + 1):
                    min_d2 = float("inf")
                    for (px, py, pz) in points:
                        d2 = (x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2
                        if d2 < min_d2:
                            min_d2 = d2
                    if noise_multiplier > 0:
                        noise = noise_multiplier * (rng.random() - 0.5) * 2.0 * threshold
                        if min_d2 + noise < threshold:
                            count += 1
                    elif min_d2 < threshold:
                        count += 1
        counts.append(count)
    return counts


def summarize(label: str, counts: list[int], target: Tuple[int, int]):
    mn = min(counts)
    mx = max(counts)
    mean = statistics.mean(counts)
    stdev = statistics.stdev(counts) if len(counts) > 1 else 0
    tlo, thi = target
    in_band = sum(1 for c in counts if tlo <= c <= thi)
    flag = "[OK]" if in_band >= len(counts) * 0.7 else "[BAD]"
    print(f"  {label:<28} min {mn:>4}  mean {mean:>6.1f}  max {mx:>4}  sd {stdev:>5.1f}   "
          f"target [{tlo:>4}-{thi:>4}]  in-band {in_band}/{len(counts)} {flag}")


def main():
    SAMPLES = 30

    print("=" * 80)
    print("Calibration check: old params vs in-game observations")
    print("=" * 80)
    print("  (Observed: Tiny ~550, Avg ~717)\n")
    old_tiny = simulate_geode(wall=3, outer_layer=2.8, distribution_points=3,
                              point_offset=1, samples=SAMPLES)
    old_avg = simulate_geode(wall=5, outer_layer=4.3, distribution_points=3,
                             point_offset=1, samples=SAMPLES)
    print(f"  OLD Tiny (wall=3, outer=2.8): mean {statistics.mean(old_tiny):.0f}   "
          f"min {min(old_tiny)}   max {max(old_tiny)}")
    print(f"  OLD Avg  (wall=5, outer=4.3): mean {statistics.mean(old_avg):.0f}   "
          f"min {min(old_avg)}   max {max(old_avg)}")
    print()

    print("=" * 80)
    print("CURRENT params (from _rebuild_vein_features.py)")
    print("=" * 80)
    current = [
        ("Tiny    (wall=2, outer=2.0)", 2, 2.0, (150, 225)),
        ("Small   (wall=3, outer=2.8)", 3, 2.8, (350, 450)),
        ("Average (wall=4, outer=3.6)", 4, 3.6, (550, 650)),
        ("Large   (wall=5, outer=4.4)", 5, 4.4, (900, 1100)),
    ]
    for label, wall, outer, target in current:
        counts = simulate_geode(wall=wall, outer_layer=outer, distribution_points=3,
                                point_offset=1, samples=SAMPLES)
        summarize(label, counts, target)
    print()

    print("=" * 80)
    print("Grid search: best (wall, dp) per size band")
    print("=" * 80)
    targets = [
        ("Tiny",    (150, 225)),
        ("Small",   (350, 450)),
        ("Average", (550, 650)),
        ("Large",   (900, 1100)),
        ("Huge",    (1500, 1700)),
    ]
    print(f"  {'param':<28} {'min':>4}  {'mean':>6}  {'max':>4}  {'sd':>5}   {'target':>14}  {'in-band':>9}")
    for label, target in targets:
        print(f"\n  -- {label} target [{target[0]}-{target[1]}] --")
        for wall in [2, 3, 4, 5, 6, 7, 8]:
            for dp in [2, 3, 4, 5]:
                for point in [1, 2, 3]:
                    counts = simulate_geode(wall=wall, outer_layer=99.0,
                                            distribution_points=dp,
                                            point_offset=point, samples=20)
                    mean = statistics.mean(counts)
                    if not (target[0] * 0.85 <= mean <= target[1] * 1.15):
                        continue
                    summarize(f"wall={wall} dp={dp} point={point}", counts, target)


if __name__ == "__main__":
    main()

"""Correct geode simulator matching Mojang's GeodeFeature.place().

Algorithm (from net.minecraft.world.level.levelgen.feature.GeodeFeature):

1. Sample numPoints = distributionPoints.sample()
2. For each of numPoints distribution points:
   - sample i5, i6, i7 from outerWallDistance (each axis independently)
   - point position = origin + (i5, i6, i7)
   - point's "pointOffset" = pointOffset.sample()  -- used in d7 formula
3. Compute:
   d = numPoints / outerWallDistance.maxValue
   d4 = 1 / sqrt(outer_layer + d)
4. For each block in [minGenOffset, maxGenOffset]^3:
   d6 = normalNoise(x,y,z) * noiseMultiplier   (≈ [-noiseMult, +noiseMult])
   d7 = sum over points: invSqrt(distSq + pointOffset) + d6
   if d7 >= d4: place block

Notes:
- With single-value wall (min==max), all distribution points stack at
  (wall, wall, wall) — the geode is a single sphere offset to that corner.
- d4 depends on wall.maxValue: smaller wall → larger d (more d4 → smaller sphere).
"""
import math
import random
import statistics


def simulate(wall_min, wall_max, dp, point_offset, outer_layer,
             noise_mult=0.0, min_gen=-16, max_gen=16, samples=10):
    rng = random.Random()
    counts = []
    for _ in range(samples):
        num_points = rng.randint(dp, dp)  # both ends inclusive (single-value)
        points = []
        for _ in range(num_points):
            ix = rng.randint(wall_min, wall_max)
            iy = rng.randint(wall_min, wall_max)
            iz = rng.randint(wall_min, wall_max)
            poff = rng.randint(point_offset, point_offset)
            points.append((ix, iy, iz, poff))
        d = num_points / wall_max
        d4 = 1.0 / math.sqrt(outer_layer + d)

        count = 0
        for x in range(min_gen, max_gen + 1):
            for y in range(min_gen, max_gen + 1):
                for z in range(min_gen, max_gen + 1):
                    d7 = 0.0
                    # noise is computed once per block in Mojang (NormalNoise);
                    # approximate as a uniform jitter scaled by noiseMult.
                    d6 = noise_mult * (rng.random() * 2 - 1) if noise_mult > 0 else 0.0
                    for (px, py, pz, off) in points:
                        dsq = (x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2
                        d7 += 1.0 / math.sqrt(dsq + off) + d6
                    if d7 >= d4:
                        count += 1
        counts.append(count)
    return counts


def summary(label, counts):
    mn, mx = min(counts), max(counts)
    mean = statistics.mean(counts)
    print(f"  {label:<60}  mean {mean:>6.0f}  range [{mn:>5}, {mx:>5}]")


def main():
    # Calibration against the user's reported in-game numbers:
    #   OLD config wall=(5,5) dp=3 point=1 outer=4.3 noise=0.05 -> observed 717
    #   CURRENT  wall=(5,5) dp=2 point=1 outer=4.5 noise=0.05 -> observed ~220 (per chunk fragment? full?)
    #   CURRENT  wall=(3,3) dp=4 point=1 outer=2.7 -> diamond_tiny seen as Large
    print("=== Calibration ===")
    summary("OLD Avg   wall=(5,5) dp=3 point=1 outer=4.3 noise=0.05 (observed ~717)",
            simulate(5, 5, 3, 1, 4.3, noise_mult=0.05, samples=8))
    summary("CURRENT Tiny wall=(3,3) dp=4 point=1 outer=2.7 noise=0.05 (observed Large)",
            simulate(3, 3, 4, 1, 2.7, noise_mult=0.05, samples=8))
    summary("CURRENT Avg  wall=(5,5) dp=2 point=1 outer=4.5 noise=0.05",
            simulate(5, 5, 2, 1, 4.5, noise_mult=0.05, samples=8))
    summary("CURRENT Huge wall=(7,7) dp=2 point=1 outer=6.5 noise=0.05",
            simulate(7, 7, 2, 1, 6.5, noise_mult=0.05, samples=4))

    print()
    print("=== Trying ranges (proper Mojang style) ===")
    summary("Tiny wall=(2,3) dp=3 point=1 outer=1.7",
            simulate(2, 3, 3, 1, 1.7, noise_mult=0.05, samples=8))
    summary("Tiny wall=(2,4) dp=3 point=1 outer=1.7",
            simulate(2, 4, 3, 1, 1.7, noise_mult=0.05, samples=8))
    summary("Small wall=(3,4) dp=3 point=1 outer=2.2",
            simulate(3, 4, 3, 1, 2.2, noise_mult=0.05, samples=8))
    summary("Average wall=(4,5) dp=3 point=1 outer=3.0",
            simulate(4, 5, 3, 1, 3.0, noise_mult=0.05, samples=8))
    summary("Large wall=(5,6) dp=4 point=1 outer=3.7",
            simulate(5, 6, 4, 1, 3.7, noise_mult=0.05, samples=8))


if __name__ == "__main__":
    main()

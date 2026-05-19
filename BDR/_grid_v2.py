"""Grid search for geode params using the CORRECT Mojang algorithm.
Target sizes:  Tiny 150-225, Small 350-450, Average 550-650,
               Large 900-1100, Huge 1500-1700.
"""
import math
import random
import statistics


def simulate(wall_min, wall_max, dp, point_offset, outer_layer,
             noise_mult=0.0, min_gen=-16, max_gen=16, samples=8):
    rng = random.Random()
    counts = []
    for _ in range(samples):
        points = []
        for _ in range(dp):
            ix = rng.randint(wall_min, wall_max)
            iy = rng.randint(wall_min, wall_max)
            iz = rng.randint(wall_min, wall_max)
            points.append((ix, iy, iz, point_offset))
        d = dp / wall_max
        d4 = 1.0 / math.sqrt(outer_layer + d)
        count = 0
        for x in range(min_gen, max_gen + 1):
            for y in range(min_gen, max_gen + 1):
                for z in range(min_gen, max_gen + 1):
                    d7 = 0.0
                    d6 = noise_mult * (rng.random() * 2 - 1) if noise_mult > 0 else 0.0
                    for (px, py, pz, off) in points:
                        dsq = (x - px) ** 2 + (y - py) ** 2 + (z - pz) ** 2
                        d7 += 1.0 / math.sqrt(dsq + off) + d6
                    if d7 >= d4:
                        count += 1
        counts.append(count)
    return counts


def search(target_lo, target_hi, label):
    """Find configurations whose mean lands inside the target band."""
    print(f"\n=== {label}  target [{target_lo}, {target_hi}] ===")
    found = []
    # Keep wall as a range; vary outer_layer continuously; dp=2 or 3.
    for wall_min in [3, 4, 5]:
        for wall_max in [wall_min, wall_min + 1, wall_min + 2]:
            if wall_max > 6:
                continue
            for dp in [2, 3]:
                # binary-search-ish for outer that hits midpoint
                for outer in [v / 10 for v in range(5, 200, 5)]:
                    counts = simulate(wall_min, wall_max, dp, 1, outer,
                                      noise_mult=0.0, samples=4)
                    mean = statistics.mean(counts)
                    if target_lo <= mean <= target_hi:
                        found.append((wall_min, wall_max, dp, outer, mean,
                                      min(counts), max(counts)))
                        break  # next dp combo
    for (wmin, wmax, dp, o, mean, mn, mx) in found[:6]:
        print(f"  wall=({wmin},{wmax}) dp={dp} outer={o:>4.1f}   mean {mean:>5.0f}  range [{mn:>4}, {mx:>4}]")


def main():
    search(150, 225, "Tiny")
    search(350, 450, "Small")
    search(550, 650, "Average")
    search(900, 1100, "Large")
    search(1500, 1700, "Huge")


if __name__ == "__main__":
    main()

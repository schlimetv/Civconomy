"""Strip every immersive_ores:vein_* and immersive_ores:surface_boulder_*
reference from ocean biomes, then print a per-biome breakdown of remaining
ore feature references."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

OCEAN_PATTERN = re.compile(r"ocean", re.IGNORECASE)

VEIN_RE = re.compile(r"^immersive_ores:vein_([a-z]+)_([a-z]+)$")
BOULDER_RE = re.compile(r"^immersive_ores:surface_boulder_([a-z]+)$")

SIZE_ORDER = ["tiny", "small", "average", "large", "huge"]


def is_ocean(biome_name: str) -> bool:
    return OCEAN_PATTERN.search(biome_name) is not None


def main():
    # PASS 1: strip ocean biomes
    stripped_total = 0
    for bp in sorted(BIOME_DIR.glob("*.json")):
        if not is_ocean(bp.stem):
            continue
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features", [])
        if not isinstance(features, list):
            continue
        removed = 0
        for idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            new_step = [x for x in step
                        if not (isinstance(x, str) and (
                            x.startswith("immersive_ores:vein_") or
                            x.startswith("immersive_ores:surface_boulder_")
                        ))]
            removed += len(step) - len(new_step)
            features[idx] = new_step
        with bp.open("w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
        print(f"  stripped {bp.stem}: removed {removed} ore refs")
        stripped_total += removed

    print(f"\nStripped {stripped_total} total ore refs from {sum(1 for p in BIOME_DIR.glob('*.json') if is_ocean(p.stem))} ocean biomes.")

    # PASS 2: build per-biome breakdown
    rows = []
    for bp in sorted(BIOME_DIR.glob("*.json")):
        biome = bp.stem
        with bp.open("r", encoding="utf-8") as f:
            data = json.load(f)
        veins_by_ore = {}
        boulders = set()
        features = data.get("features", [])
        for step in features:
            if not isinstance(step, list):
                continue
            for fid in step:
                if not isinstance(fid, str):
                    continue
                m = VEIN_RE.match(fid)
                if m:
                    ore, size = m.group(1), m.group(2)
                    veins_by_ore.setdefault(ore, set()).add(size)
                    continue
                m = BOULDER_RE.match(fid)
                if m:
                    boulders.add(m.group(1))
        # Render per-ore sizes
        all_ores = sorted(set(veins_by_ore.keys()) | boulders)
        cells = []
        for ore in all_ores:
            sizes = veins_by_ore.get(ore, set())
            sizes_str = ",".join(s[0].upper() for s in SIZE_ORDER if s in sizes)
            tag = sizes_str if sizes_str else ""
            if ore in boulders:
                tag += "+B" if tag else "B"
            cells.append(f"{ore}={tag}")
        ore_summary = "  ".join(cells) if cells else "(no ore generation)"
        rows.append((biome, ore_summary))

    # Print table
    name_w = max(len(b) for b, _ in rows) + 2
    print()
    print("=" * 100)
    print(f"{'Biome':<{name_w}}Ore types (sizes: T=Tiny, S=Small, A=Average, L=Large, H=Huge; +B=surface boulder)")
    print("=" * 100)
    for biome, summary in rows:
        print(f"{biome:<{name_w}}{summary}")


if __name__ == "__main__":
    main()

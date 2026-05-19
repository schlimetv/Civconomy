"""One-shot cleanup: remove all immersive_ores features that aren't part of
the vein_* set, drop the now-orphaned cannot_replace tags, and strip the
deleted placed_feature ids from every biome's features list."""
import json
from pathlib import Path

ROOT = Path(__file__).parent
IO_ROOT = ROOT / "data" / "immersive_ores"
BIOME_DIR = ROOT / "data" / "minecraft" / "worldgen" / "biome"

CONFIGURED_DIR = IO_ROOT / "worldgen" / "configured_feature"
PLACED_DIR = IO_ROOT / "worldgen" / "placed_feature"
TAGS_DIR = IO_ROOT / "tags" / "blocks"

OBSOLETE_TAGS = ["diamond_geode_cannot_replace.json", "iron_geodes_cannot_replace.json"]


def collect_obsolete(dir_path: Path) -> list[Path]:
    return [p for p in sorted(dir_path.glob("*.json")) if not p.name.startswith("vein_")]


def delete_features():
    removed = []
    for d in (CONFIGURED_DIR, PLACED_DIR):
        for p in collect_obsolete(d):
            p.unlink()
            removed.append(p.relative_to(ROOT))
    for tag_name in OBSOLETE_TAGS:
        tag_path = TAGS_DIR / tag_name
        if tag_path.exists():
            tag_path.unlink()
            removed.append(tag_path.relative_to(ROOT))
    return removed


def collect_obsolete_ids() -> set[str]:
    """Build the set of placed-feature ids we're removing, so we can strip
    them from biome features lists."""
    ids = set()
    # The placed_feature dir has already been wiped, so we precompute names
    # before deletion. Caller passes them in. Here we reconstruct from the
    # known obsolete list — only placed_feature names matter for biome refs.
    return ids


def main():
    # Snapshot obsolete placed-feature ids BEFORE deletion so we can also
    # strip biome references.
    obsolete_placed_ids = {
        f"immersive_ores:{p.stem}" for p in collect_obsolete(PLACED_DIR)
    }

    removed = delete_features()
    print("Removed files:")
    for r in removed:
        print(f"  {r}")

    print(f"\nStripping {len(obsolete_placed_ids)} obsolete placed-feature refs from biome JSONs:")
    for fid in sorted(obsolete_placed_ids):
        print(f"  {fid}")

    edited = 0
    for biome_path in sorted(BIOME_DIR.glob("*.json")):
        with biome_path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        features = data.get("features")
        if not isinstance(features, list):
            continue
        dirty = False
        for step_idx, step in enumerate(features):
            if not isinstance(step, list):
                continue
            new_step = [x for x in step if x not in obsolete_placed_ids]
            if len(new_step) != len(step):
                features[step_idx] = new_step
                dirty = True
        if dirty:
            with biome_path.open("w", encoding="utf-8") as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
                f.write("\n")
            edited += 1
            print(f"  EDITED: {biome_path.name}")

    print(f"\nDone. {len(removed)} files deleted, {edited} biome JSONs cleaned.")


if __name__ == "__main__":
    main()

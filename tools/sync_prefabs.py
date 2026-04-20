#!/usr/bin/env python3
"""Sync author-pack prefabs into the Natural20 mod's resources.

Reads .prefab.json files from the three Nat20 author packs that PrefabMaker
writes via `/prefab save`:

    devserver/mods/Natural20.SettlementPiece(s)/Server/Prefabs/
    devserver/mods/Natural20.SettlementFull/Server/Prefabs/
    devserver/mods/Natural20.HostilePOI/Server/Prefabs/

…and copies each into the mod's shippable + dev-loadable locations:

    src/main/resources/Server/Prefabs/Nat20/<category>/<name>.prefab.json  (ships)
    assets/Server/Prefabs/Nat20/<category>/<name>.prefab.json              (dev runtime)

Category layout:

    Natural20.SettlementPiece / .SettlementPieces  ->  Nat20/settlement_pieces/
    Natural20.SettlementFull                       ->  Nat20/settlement_full/
    Natural20.HostilePOI                           ->  Nat20/hostile_poi/

Idempotent: running twice is a no-op. Skips files whose content is unchanged.

Usage:
    python3 tools/sync_prefabs.py            # sync from all three packs
    python3 tools/sync_prefabs.py --check    # dry run, report what would change
"""
from __future__ import annotations

import argparse
import filecmp
import shutil
import sys
from pathlib import Path

# Source pack name -> output category dir (relative to Server/Prefabs/Nat20/)
SOURCES: dict[str, str] = {
    "Natural20.SettlementPiece":  "settlement_pieces",
    "Natural20.SettlementPieces": "settlement_pieces",
    "Natural20.SettlementFull":   "settlement_full",
    "Natural20.HostilePOI":       "hostile_poi",
}

REPO_ROOT = Path(__file__).resolve().parent.parent
DEVSERVER_MODS = REPO_ROOT / "devserver" / "mods"
RESOURCES_DEST = REPO_ROOT / "src" / "main" / "resources" / "Server" / "Prefabs" / "Nat20"
ASSETS_DEST    = REPO_ROOT / "assets" / "Server" / "Prefabs" / "Nat20"


def scan_pack(pack_dir: Path) -> list[Path]:
    prefabs_dir = pack_dir / "Server" / "Prefabs"
    if not prefabs_dir.is_dir():
        return []
    return sorted(p for p in prefabs_dir.rglob("*.prefab.json") if p.is_file())


def copy_if_changed(src: Path, dst: Path, dry_run: bool) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists() and filecmp.cmp(src, dst, shallow=False):
        return False
    if dry_run:
        print(f"  WOULD update {dst.relative_to(REPO_ROOT)}")
    else:
        shutil.copy2(src, dst)
        print(f"  updated {dst.relative_to(REPO_ROOT)}")
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true",
                        help="Dry run: report what would change but don't copy.")
    args = parser.parse_args()

    if not DEVSERVER_MODS.is_dir():
        print(f"ERROR: devserver/mods not found at {DEVSERVER_MODS}", file=sys.stderr)
        return 1

    changed = 0
    scanned = 0

    for pack_name, category in SOURCES.items():
        pack_dir = DEVSERVER_MODS / pack_name
        if not pack_dir.is_dir():
            continue
        prefabs = scan_pack(pack_dir)
        if not prefabs:
            continue
        print(f"{pack_name} -> Nat20/{category}/  ({len(prefabs)} prefabs)")
        for src in prefabs:
            scanned += 1
            rel = src.relative_to(pack_dir / "Server" / "Prefabs")
            dst_res = RESOURCES_DEST / category / rel
            dst_ass = ASSETS_DEST / category / rel
            if copy_if_changed(src, dst_res, args.check):
                changed += 1
            if copy_if_changed(src, dst_ass, args.check):
                changed += 1

    if scanned == 0:
        print("No prefabs found in any of the known author packs. Save some via /prefab save.")
        return 0

    verb = "would change" if args.check else "changed"
    print(f"\n{changed} files {verb} across {scanned} source prefabs.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

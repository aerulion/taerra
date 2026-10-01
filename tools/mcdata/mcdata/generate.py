from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from . import colormap, models, oklab, shape
from .assets import AssetError, AssetTree, provision

TOOL_DIR = Path(__file__).resolve().parent.parent
PROJECT_ROOT = TOOL_DIR.parent.parent
DEFAULT_OUTPUT = PROJECT_ROOT / "plugin" / "src" / "main" / "resources" / "block_data.json"
DEFAULT_REPORT = TOOL_DIR / "last_report.json"
DEFAULT_CACHE = TOOL_DIR / ".cache"
FALLBACK_COLOR = "#FF00FF"


@dataclass
class Config:
    assets_dir: Path | None
    version: str | None
    cache_dir: Path
    output_path: Path
    report_path: Path | None
    full_threshold: float
    bandwidth: float
    alpha_threshold: int


def build_block_data(tree: AssetTree, config: Config) -> tuple[dict, dict]:
    entries: dict[str, dict] = {}
    warnings: list[str] = []
    stats = {"full": 0, "thin": 0, "none": 0, "missing_top": 0, "missing_side": 0}

    for block_name in tree.blockstate_ids():
        block_id = f"minecraft:{block_name}"
        if block_id in models.NON_BLOCK_IDS:
            continue
        try:
            blockstate = tree.blockstate(block_name)
        except (FileNotFoundError, json.JSONDecodeError) as error:
            warnings.append(f"{block_id}: unreadable blockstate ({error})")
            continue

        placements = models.canonical_model_refs(blockstate)
        if not placements:
            warnings.append(f"{block_id}: no model reference in blockstate")

        merged_elements: list[dict] = []
        resolved = None
        for placement in placements:
            candidate = models.flatten_model(tree, block_id, placement.model_ref)
            merged_elements.extend(
                models.rotate_elements(candidate.elements, placement.x_rotation, placement.y_rotation)
            )
            if resolved is None:
                resolved = candidate
        if resolved is None:
            resolved = models.ResolvedModel(block_id=block_id, model_ref="")

        shape_class, fraction = shape.classify(block_id, merged_elements, config.full_threshold)
        stats[shape_class] += 1
        if shape_class == "none":
            entries[block_id] = {"shape": shape_class, "occupancy": round(fraction, 4)}
            continue

        top_rgba, top_tinted = models.face_texture(tree, resolved, "up")
        side_rgba, side_tinted = models.face_texture(tree, resolved, "side")
        if block_id in models.TINT_OVERRIDES:
            top_tinted = True
            side_tinted = True

        top_color = _dominant(top_rgba, config)
        side_color = _dominant(side_rgba, config)
        if top_color is None:
            stats["missing_top"] += 1
            warnings.append(f"{block_id}: no usable top texture")
        if side_color is None:
            stats["missing_side"] += 1
        top_color = top_color or side_color or FALLBACK_COLOR
        side_color = side_color or top_color

        constant_tint = models.CONSTANT_TINTS.get(block_id)
        if constant_tint is not None:
            top_color = apply_tint(top_color, constant_tint)
            side_color = apply_tint(side_color, constant_tint)
            top_tinted = False
            side_tinted = False

        entry = {"top": top_color, "side": side_color, "shape": shape_class}
        if top_tinted:
            entry["tint_top"] = True
        if side_tinted:
            entry["tint_side"] = True
        entry["occupancy"] = round(fraction, 4)
        entries[block_id] = entry

    report = {
        "generated_blocks": len(entries),
        "stats": stats,
        "warnings": warnings,
    }
    return entries, report


def apply_tint(hex_color: str, tint: tuple[int, int, int]) -> str:
    value = hex_color.lstrip("#")
    channels = (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16))
    return "#" + "".join(f"{(channel * factor) // 255:02X}" for channel, factor in zip(channels, tint))


def _dominant(rgba, config: Config) -> str | None:
    if rgba is None:
        return None
    return oklab.dominant_color_hex(rgba, alpha_threshold=config.alpha_threshold, bandwidth=config.bandwidth)


def write_json(path: Path, payload: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generates TAERRA block data (top color, side color, shape) from a vanilla asset tree."
    )
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--assets-dir", type=Path, help="Existing checkout of the minecraft-assets repository")
    source.add_argument("--version", help="Minecraft version to provision from the assets repository, e.g. 26.2")
    parser.add_argument("--cache-dir", type=Path, default=DEFAULT_CACHE, help="Where provisioned checkouts are kept")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Target block_data.json path")
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT, help="Optional report JSON path")
    parser.add_argument("--no-report", action="store_true", help="Do not write a report JSON")
    parser.add_argument("--full-threshold", type=float, default=shape.DEFAULT_FULL_THRESHOLD,
                        help="Cell occupancy at or above which a block counts as full")
    parser.add_argument("--bandwidth", type=float, default=oklab.DEFAULT_BANDWIDTH, help="Mean-shift bandwidth")
    parser.add_argument("--alpha-threshold", type=int, default=16, help="Ignore pixels below this alpha (0-255)")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    config = Config(
        assets_dir=args.assets_dir,
        version=args.version,
        cache_dir=args.cache_dir.resolve(),
        output_path=args.output.resolve(),
        report_path=None if args.no_report else args.report.resolve(),
        full_threshold=args.full_threshold,
        bandwidth=args.bandwidth,
        alpha_threshold=args.alpha_threshold,
    )
    try:
        root = config.assets_dir or provision(config.version, config.cache_dir)
        tree = AssetTree.locate(Path(root).resolve())
        entries, report = build_block_data(tree, config)
        colormaps = colormap.build(tree)
    except AssetError as error:
        print(f"Error: {error}", file=sys.stderr)
        return 2

    write_json(config.output_path, entries)
    colormap_path = config.output_path.with_name("colormaps.json")
    write_json(colormap_path, colormaps)
    if config.report_path is not None:
        write_json(config.report_path, report)

    print(f"Generated {len(entries)} blocks -> {config.output_path}")
    print(f"Fitted {len(colormaps)} colormaps -> {colormap_path}")
    print(f"  shapes: {report['stats']}")
    if report["warnings"]:
        print(f"  warnings: {len(report['warnings'])} (see report)", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

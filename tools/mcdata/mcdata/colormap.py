from __future__ import annotations

import numpy as np

from .assets import AssetError, AssetTree

NAMES = ("grass", "foliage", "dry_foliage")
CHANNELS = ("red", "green", "blue")
SIZE = 256
TERMS = tuple((i, j) for i in range(3) for j in range(3 - i))


def terms(x: np.ndarray, y: np.ndarray) -> np.ndarray:
    return np.stack([x**i * y**j for i, j in TERMS], axis=-1)


def fit(rgb: np.ndarray) -> dict[str, list[float]]:
    rows, columns = np.mgrid[0:SIZE, 0:SIZE]
    sampled = rows >= columns
    design = terms(columns[sampled] / (SIZE - 1), rows[sampled] / (SIZE - 1))
    return {
        channel: [round(float(c), 6) for c in np.linalg.lstsq(design, rgb[..., k][sampled].astype(float), rcond=None)[0]]
        for k, channel in enumerate(CHANNELS)
    }


def evaluate(coefficients: dict[str, list[float]]) -> np.ndarray:
    rows, columns = np.mgrid[0:SIZE, 0:SIZE]
    design = terms(columns / (SIZE - 1), rows / (SIZE - 1))
    channels = [design @ np.asarray(coefficients[channel]) for channel in CHANNELS]
    return np.clip(np.rint(np.stack(channels, axis=-1)), 0, 255).astype(np.uint8)


def build(tree: AssetTree) -> dict[str, dict[str, list[float]]]:
    colormaps = {}
    for name in NAMES:
        rgba = tree.texture(f"colormap/{name}")
        if rgba is None or rgba.shape[:2] != (SIZE, SIZE):
            raise AssetError(f"textures/colormap/{name}.png is missing or not {SIZE}x{SIZE}")
        colormaps[name] = fit(rgba[..., :3])
    return colormaps

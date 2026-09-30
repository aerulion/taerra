from __future__ import annotations

import numpy as np

GRID = 16
CELL_COUNT = GRID ** 3

MIN_SOLID_THICKNESS = 1.0

DEFAULT_FULL_THRESHOLD = 0.25

INVISIBLE_BLOCKS = frozenset({
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier", "minecraft:light",
    "minecraft:structure_void", "minecraft:moving_piston", "minecraft:end_portal", "minecraft:end_gateway",
})

FULL_SUFFIXES = ("_shulker_box", "_chest", "_golem_statue")
FULL_BLOCKS = frozenset({
    "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:shulker_box", "minecraft:chest",
    "minecraft:ender_chest", "minecraft:trapped_chest", "minecraft:decorated_pot", "minecraft:conduit",
})
THIN_SUFFIXES = ("_banner", "_head", "_skull")

FULL_PANEL_SUFFIXES = ("_door", "_trapdoor", "_pane", "_bars", "_fence", "_fence_gate")


def override_for(block_id: str) -> str | None:
    if block_id in INVISIBLE_BLOCKS:
        return "none"
    if block_id in FULL_BLOCKS or block_id.endswith(FULL_SUFFIXES) or block_id.endswith(FULL_PANEL_SUFFIXES):
        return "full"
    if block_id.endswith(THIN_SUFFIXES):
        return "thin"
    return None


def measure(elements: list[dict]) -> tuple[float, bool]:
    if not elements:
        return 0.0, False
    return occupied_fraction(elements), bool(_rasterize(elements).any())


def occupied_fraction(elements: list[dict]) -> float:
    if not elements:
        return 0.0
    grid = _rasterize(elements)
    enclosed = _fill_enclosed(grid)
    if enclosed.sum() > grid.sum():
        return float(enclosed.sum()) / CELL_COUNT
    return float(_rasterize([e for e in elements if _is_solid(e)]).sum()) / CELL_COUNT


def _is_solid(element: dict) -> bool:
    try:
        return all(
            abs(float(element["to"][axis]) - float(element["from"][axis])) >= MIN_SOLID_THICKNESS
            for axis in range(3)
        )
    except (KeyError, TypeError, ValueError):
        return False


def _rasterize(elements: list[dict]) -> np.ndarray:
    grid = np.zeros((GRID, GRID, GRID), dtype=bool)
    cell_low = np.arange(GRID, dtype=np.float64)
    cell_high = cell_low + 1.0
    for element in elements:
        try:
            start = [float(v) for v in element["from"]]
            end = [float(v) for v in element["to"]]
        except (KeyError, TypeError, ValueError):
            continue
        axes = []
        for axis in range(3):
            low, high = sorted((start[axis], end[axis]))
            if high == low:
                axes.append((cell_low <= low) & (low <= cell_high))
            else:
                axes.append((high > cell_low) & (low < cell_high))
        grid |= axes[0][:, None, None] & axes[1][None, :, None] & axes[2][None, None, :]
    return grid


def _fill_enclosed(grid: np.ndarray) -> np.ndarray:
    outside = np.zeros_like(grid)
    frontier = [
        (x, y, z)
        for x in range(GRID) for y in range(GRID) for z in range(GRID)
        if (x in (0, GRID - 1) or y in (0, GRID - 1) or z in (0, GRID - 1)) and not grid[x, y, z]
    ]
    for cell in frontier:
        outside[cell] = True
    while frontier:
        x, y, z = frontier.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            nx, ny, nz = x + dx, y + dy, z + dz
            if 0 <= nx < GRID and 0 <= ny < GRID and 0 <= nz < GRID and not grid[nx, ny, nz] and not outside[nx, ny, nz]:
                outside[nx, ny, nz] = True
                frontier.append((nx, ny, nz))
    return ~outside


def classify(block_id: str, elements: list[dict], full_threshold: float = DEFAULT_FULL_THRESHOLD) -> tuple[str, float]:
    fraction, visible = measure(elements)
    override = override_for(block_id)
    if override is not None:
        return override, fraction
    if not visible:
        return "none", fraction
    return ("full" if fraction >= full_threshold else "thin"), fraction

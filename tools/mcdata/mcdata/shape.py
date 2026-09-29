"""Block shape classification from model geometry.

The isometric renderer needs to know whether a block fills its cell: a full block forms a run in a column stack and
hides the side face of the block behind it, whereas a torch or a flower must do neither. That is a property of the
model's geometry, so it is measured rather than guessed from material flags.
"""

from __future__ import annotations

import numpy as np

GRID = 16
CELL_COUNT = GRID ** 3

# Minimum thickness, in model units, for an element to count as solid rather than as a sheet.
MIN_SOLID_THICKNESS = 1.0

# Default fraction of the cell a model must occupy to count as a full block. Chosen to sit between a wall post
# (8x16x8 = 0.25) and a fence post (4x16x4 = 0.0625): walls read as solid on the map, fences do not.
DEFAULT_FULL_THRESHOLD = 0.25

# Blocks the client renders with dedicated code rather than a block model, so measuring their geometry is not
# possible: their models carry a particle texture and nothing else. Fluids and block entities still occupy their
# cell, while genuinely invisible blocks must not be drawn at all.
INVISIBLE_BLOCKS = frozenset({
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier", "minecraft:light",
    "minecraft:structure_void", "minecraft:moving_piston", "minecraft:end_portal", "minecraft:end_gateway",
})

# Suffix rules for block entities, matched after the explicit sets above.
FULL_SUFFIXES = ("_shulker_box", "_chest", "_golem_statue")
FULL_BLOCKS = frozenset({
    "minecraft:water", "minecraft:lava", "minecraft:bubble_column", "minecraft:shulker_box", "minecraft:chest",
    "minecraft:ender_chest", "minecraft:trapped_chest", "minecraft:decorated_pot", "minecraft:conduit",
})
THIN_SUFFIXES = ("_banner", "_head", "_skull")

# Doors, trapdoors, panes, bars, fences and gates are thin panels that nonetheless stand in for a wall, so they must
# form a run in a column stack; measuring them would put them below the threshold, at 0.0625 to 0.2344. A glass
# facade left thin draws as a hole into the building behind it, and a fence as a stain on the ground.
FULL_PANEL_SUFFIXES = ("_door", "_trapdoor", "_pane", "_bars", "_fence", "_fence_gate")


def override_for(block_id: str) -> str | None:
    """Returns the forced shape class for blocks whose geometry cannot be measured, or None to measure normally."""
    if block_id in INVISIBLE_BLOCKS:
        return "none"
    if block_id in FULL_BLOCKS or block_id.endswith(FULL_SUFFIXES) or block_id.endswith(FULL_PANEL_SUFFIXES):
        return "full"
    if block_id.endswith(THIN_SUFFIXES):
        return "thin"
    return None


def measure(elements: list[dict]) -> tuple[float, bool]:
    """Returns the solid occupancy of a model and whether it has any visible geometry at all.

    The two are separate questions. Crossed sheets occupy no solid volume but are plainly visible, so collapsing
    them into a single number would classify every flower and rail as invisible.
    """
    if not elements:
        return 0.0, False
    return occupied_fraction(elements), bool(_rasterize(elements).any())


def occupied_fraction(elements: list[dict]) -> float:
    """Fraction of the block cell a model occupies, for the purpose of deciding whether it reads as solid.

    Raw volume cannot decide this on its own: mature wheat measures 0.41 and a hopper only 0.31, yet the wheat must
    stay thin and the hopper must not. What separates them is that crops are crossed sheets while a hopper is built
    from boxes. So sheets only count when they seal a volume between them, which is how powder snow and mushroom
    blocks describe a solid cube, and are ignored otherwise.
    """
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
            # A cell counts as occupied when the element overlaps it at all, rather than when it contains the
            # cell center. Minecraft models routinely describe visible geometry thinner than one cell: crossed
            # plants are zero-thickness planes, tripwire is an exact plane, and powder snow is a 0.002 shell.
            # A center test measures every one of them as empty.
            if high == low:
                axes.append((cell_low <= low) & (low <= cell_high))
            else:
                axes.append((high > cell_low) & (low < cell_high))
        grid |= axes[0][:, None, None] & axes[1][None, :, None] & axes[2][None, None, :]
    return grid


def _fill_enclosed(grid: np.ndarray) -> np.ndarray:
    """Counts cells sealed inside the geometry as occupied.

    Several visually solid blocks are authored as hollow shells so the client can render them translucent: powder
    snow is a 0.002 thick box and a mushroom block is six single faces. Measuring only their surfaces would call
    them thin, while a crossed plant, which encloses nothing, must stay thin. Flooding from outside separates the
    two without needing a per-block exception.
    """
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
    """Returns the shape class for a block and the measured occupancy that produced it."""
    fraction, visible = measure(elements)
    override = override_for(block_id)
    if override is not None:
        return override, fraction
    if not visible:
        return "none", fraction
    return ("full" if fraction >= full_threshold else "thin"), fraction

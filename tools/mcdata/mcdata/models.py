"""Blockstate and block-model resolution.

Turns a block id into a single representative model: one canonical variant, the parent chain flattened, ``#ref``
texture indirection resolved, and the outward-facing textures for the up and side directions composited in element
order so that overlays such as ``grass_block_side_overlay`` land on top of the base texture.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from .assets import AssetTree, strip_namespace

MAX_PARENT_DEPTH = 32
CELL_SIZE = 16.0
MAX_AGE = 25
SIDE_DIRECTIONS = ("north", "south", "west", "east")
FACE_PROPERTIES = frozenset({"north", "south", "east", "west", "up", "down"})

# A face is reported as biome tinted only when the tinted layers cover most of it. The side of a grass block is
# dirt with a sparse tinted overlay fringe: tinting the whole face would turn the dirt green.
TINT_COVERAGE_THRESHOLD = 0.5

# Blocks the client renders without a block model cannot declare a tint index in one, so theirs is stated here.
# Water's texture is grayscale and is tinted per biome at runtime; leaving it untinted renders every ocean gray.
TINT_OVERRIDES = frozenset({"minecraft:water", "minecraft:bubble_column"})

# Tints the game applies from code rather than from a biome, and that nothing the map tracks can vary. The model
# declares a tint index, so the face is detected as tinted, but there is no runtime rule to apply: redstone dust is
# tinted by how much power it carries, which a map has no notion of. Baking the unpowered tint in is closer than
# leaving the grayscale texture untinted, which renders a redstone line white.
CONSTANT_TINTS = {
    # RedStoneWireBlock's color for power zero.
    "minecraft:redstone_wire": (0x4C, 0x00, 0x00),
    # StemBlock's color at full growth, which is the age these are sampled at, and AttachedStemBlock's constant.
    "minecraft:melon_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:pumpkin_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:attached_melon_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:attached_pumpkin_stem": (0xE0, 0xC7, 0x1C),
}

# Present in blockstates but rendered as entities rather than blocks, so they are not block ids at all.
NON_BLOCK_IDS = frozenset({"minecraft:item_frame", "minecraft:glow_item_frame"})

# Property values that indicate a non-default block state. The canonical variant is the one with the fewest of them,
# so a door is sampled closed, a slab as a bottom slab, and a grass block without snow.
NON_DEFAULT_VALUES = {
    "snowy": {"true"},
    "half": {"top", "upper"},
    "type": {"top", "double"},
    "open": {"true"},
    "powered": {"true"},
    "waterlogged": {"true"},
    "triggered": {"true"},
    "conditional": {"true"},
    "eye": {"true"},
    "hanging": {"true"},
    "extended": {"true"},
    "short": {"true"},
    "attached": {"true"},
    "disarmed": {"true"},
    "in_wall": {"true"},
    "signal_fire": {"true"},
    "bottom": {"true"},
    "inverted": {"true"},
    "unstable": {"true"},
    "occupied": {"true"},
    "has_book": {"true"},
    "has_bottle_0": {"true"},
    "cracked": {"true"},
    "crafting": {"true"},
}


@dataclass
class ResolvedModel:
    """A flattened block model."""

    block_id: str
    model_ref: str
    elements: list[dict] = field(default_factory=list)
    textures: dict[str, str] = field(default_factory=dict)

    def resolve_texture(self, value: str) -> str | None:
        """Follows ``#ref`` indirection to a concrete texture path."""
        seen = 0
        while isinstance(value, str) and value.startswith("#"):
            seen += 1
            if seen > MAX_PARENT_DEPTH:
                return None
            value = self.textures.get(value[1:], "")
        return value or None


def _texture_value(raw: object) -> str | None:
    """Texture entries are either a path or, since 1.21.x, an object carrying the path under ``sprite``."""
    if isinstance(raw, str):
        return raw
    if isinstance(raw, dict):
        sprite = raw.get("sprite")
        return sprite if isinstance(sprite, str) else None
    return None


def _variant_penalty(state_key: str) -> tuple[int, str]:
    penalty = 0
    if state_key:
        for assignment in state_key.split(","):
            name, _, value = assignment.partition("=")
            if value in NON_DEFAULT_VALUES.get(name, ()):
                penalty += 1
            elif name == "age" and value.isdigit():
                # Crops are most representative fully grown: a wheat field should read golden, not as bare sprouts.
                penalty += MAX_AGE - int(value)
            elif name in ("level", "power", "distance", "layers", "bites", "delay") and value.isdigit():
                penalty += int(value)
    return penalty, state_key


@dataclass(frozen=True)
class ModelPlacement:
    """One model reference together with the axis rotations the blockstate applies to it."""

    model_ref: str
    x_rotation: int = 0
    y_rotation: int = 0


def _placement_of(entry: object) -> ModelPlacement | None:
    if isinstance(entry, list):
        entry = entry[0] if entry else {}
    if not isinstance(entry, dict):
        return None
    model = entry.get("model")
    if not isinstance(model, str):
        return None
    return ModelPlacement(model, int(entry.get("x", 0)) % 360, int(entry.get("y", 0)) % 360)


def rotate_elements(elements: list[dict], x_rotation: int, y_rotation: int) -> list[dict]:
    """Rotates element bounding boxes about the cell center in 90 degree steps.

    Blockstates frequently apply one model at several rotations, which is how a mushroom block describes a cube as
    six copies of a single face. Ignoring the rotations collapses all of them onto the same face and measures the
    block as a flat sheet.
    """
    if not x_rotation and not y_rotation:
        return elements

    def rotate_point(point: list[float]) -> list[float]:
        x, y, z = point
        for _ in range((y_rotation // 90) % 4):
            x, z = CELL_SIZE - z, x
        for _ in range((x_rotation // 90) % 4):
            y, z = CELL_SIZE - z, y
        return [x, y, z]

    rotated = []
    for element in elements:
        try:
            start = rotate_point([float(v) for v in element["from"]])
            end = rotate_point([float(v) for v in element["to"]])
        except (KeyError, TypeError, ValueError):
            continue
        low = [min(a, b) for a, b in zip(start, end)]
        high = [max(a, b) for a, b in zip(start, end)]
        rotated.append({**element, "from": low, "to": high})
    return rotated


def _is_face_keyed(variants: dict) -> bool:
    """True when a blockstate's variants are keyed purely by which faces are drawn.

    Mushroom blocks and chorus plants describe a cube as one model per visible face, so any single variant measures
    as a flat sheet. Their footprint is only correct once every variant is unioned.
    """
    for state_key in variants:
        if not state_key:
            return False
        for assignment in state_key.split(","):
            if assignment.partition("=")[0] not in FACE_PROPERTIES:
                return False
    return bool(variants)


def canonical_model_refs(blockstate: dict) -> list[ModelPlacement]:
    """Picks the model placements that together best represent a block's default appearance."""
    variants = blockstate.get("variants")
    if isinstance(variants, dict) and variants:
        if _is_face_keyed(variants):
            return _unique(_placement_of(entry) for entry in variants.values())
        best_key = min(variants.keys(), key=_variant_penalty)
        return _unique([_placement_of(variants[best_key])])

    multipart = blockstate.get("multipart")
    if isinstance(multipart, list) and multipart:
        # Every part of a wall, fence, pane or bar contributes to its footprint, so all of them are unioned rather
        # than only the unconditional ones; a post on its own would under-measure the block.
        return _unique(_placement_of(part.get("apply")) for part in multipart)
    return []


def _unique(values) -> list[ModelPlacement]:
    seen: dict[ModelPlacement, None] = {}
    for value in values:
        if value is not None:
            seen.setdefault(value, None)
    return list(seen)


def flatten_model(tree: AssetTree, block_id: str, model_ref: str) -> ResolvedModel:
    """Walks the parent chain, merging textures and taking the nearest ancestor that defines elements."""
    resolved = ResolvedModel(block_id=block_id, model_ref=model_ref)
    current: str | None = model_ref
    depth = 0
    while current and depth < MAX_PARENT_DEPTH:
        depth += 1
        try:
            model = tree.model(current)
        except FileNotFoundError:
            break
        for name, raw in (model.get("textures") or {}).items():
            # A child's texture definition always wins over its parent's.
            resolved.textures.setdefault(name, _texture_value(raw) or "")
        if not resolved.elements and isinstance(model.get("elements"), list):
            resolved.elements = model["elements"]
        current = model.get("parent")
    return resolved


def _face_area(element: dict, axis: int) -> float:
    """Area of an element's face perpendicular to an axis."""
    other = [a for a in range(3) if a != axis]
    try:
        return abs(
            (float(element["to"][other[0]]) - float(element["from"][other[0]]))
            * (float(element["to"][other[1]]) - float(element["from"][other[1]]))
        )
    except (KeyError, TypeError, ValueError):
        return 0.0


def _outer_elements(elements: list[dict], direction: str) -> list[dict]:
    """Keeps the elements that dominate a direction's visible face.

    Selection is by face area rather than by position along the axis. A repeater, a comparator and a candle cake
    all carry a small part standing proud of the body, and picking whatever sits highest would sample the torch or
    the candle instead of the surface that actually covers the block.
    """
    axis = {"up": 1, "north": 2, "south": 2, "west": 0, "east": 0}[direction]
    candidates = [e for e in elements if isinstance(e.get("faces"), dict) and direction in e["faces"]]
    if not candidates:
        return []
    largest = max(_face_area(e, axis) for e in candidates)
    if largest <= 0.0:
        return candidates
    return [e for e in candidates if _face_area(e, axis) == largest]


def _crop_uv(rgba: np.ndarray, uv: list | None) -> np.ndarray:
    if not uv or len(uv) != 4:
        return rgba
    height, width = rgba.shape[0], rgba.shape[1]
    x1, x2 = sorted((float(uv[0]), float(uv[2])))
    y1, y2 = sorted((float(uv[1]), float(uv[3])))
    left, right = int(round(x1 / 16 * width)), int(round(x2 / 16 * width))
    top, bottom = int(round(y1 / 16 * height)), int(round(y2 / 16 * height))
    if right <= left or bottom <= top:
        return rgba
    return rgba[top:bottom, left:right]


def _composite(layers: list[np.ndarray]) -> np.ndarray | None:
    """Alpha-composites textures in element order, resampling to the largest layer by nearest neighbor."""
    layers = [layer for layer in layers if layer.size]
    if not layers:
        return None
    height = max(layer.shape[0] for layer in layers)
    width = max(layer.shape[1] for layer in layers)

    result_rgb = np.zeros((height, width, 3), dtype=np.float32)
    result_alpha = np.zeros((height, width, 1), dtype=np.float32)
    for layer in layers:
        if layer.shape[0] != height or layer.shape[1] != width:
            rows = (np.arange(height) * layer.shape[0] // height).clip(0, layer.shape[0] - 1)
            cols = (np.arange(width) * layer.shape[1] // width).clip(0, layer.shape[1] - 1)
            layer = layer[rows][:, cols]
        source = layer.astype(np.float32) / 255.0
        source_rgb, source_alpha = source[..., :3], source[..., 3:4]
        # Straight (non-premultiplied) source-over. Compositing in premultiplied form without dividing the color
        # back out would blend every translucent texture toward black, turning white stained glass gray.
        out_alpha = source_alpha + result_alpha * (1.0 - source_alpha)
        safe_alpha = np.where(out_alpha > 0.0, out_alpha, 1.0)
        result_rgb = (source_rgb * source_alpha + result_rgb * result_alpha * (1.0 - source_alpha)) / safe_alpha
        result_alpha = out_alpha
    return (np.concatenate([result_rgb, result_alpha], axis=2) * 255.0).round().clip(0, 255).astype(np.uint8)


def _opaque_pixels(rgba: np.ndarray, alpha_threshold: int = 16) -> int:
    return int((rgba[..., 3] >= alpha_threshold).sum())


def face_texture(tree: AssetTree, model: ResolvedModel, direction: str) -> tuple[np.ndarray | None, bool]:
    """Returns the composited outward texture for a direction and whether it is predominantly biome tinted."""
    directions = SIDE_DIRECTIONS if direction == "side" else (direction,)
    layers: list[np.ndarray] = []
    opaque_total = 0
    opaque_tinted = 0
    for candidate_direction in directions:
        for element in _outer_elements(model.elements, candidate_direction):
            face = element["faces"][candidate_direction]
            texture_ref = model.resolve_texture(face.get("texture", ""))
            if not texture_ref:
                continue
            rgba = tree.texture(strip_namespace(texture_ref))
            if rgba is None:
                continue
            cropped = _crop_uv(rgba, face.get("uv"))
            layers.append(cropped)
            opaque = _opaque_pixels(cropped)
            opaque_total += opaque
            if "tintindex" in face:
                opaque_tinted += opaque
        if layers:
            # One side is representative; sampling all four would just duplicate the same textures.
            break

    if not layers and direction == "up":
        # Crossed plants have no upward face at all, only the two planes standing in the block. Looking down on one
        # you see those planes, so they supply the top color as well, and crucially their tint: grass, ferns and
        # saplings are grayscale textures that the game tints per biome, and losing that renders every field gray.
        return face_texture(tree, model, "side")

    if not layers:
        # Fluids and block entities carry no face geometry at all. Their model still names a particle texture,
        # which is exactly the representative texture the client uses for them.
        particle = model.resolve_texture("#particle") or model.textures.get("particle")
        if particle:
            rgba = tree.texture(strip_namespace(particle))
            if rgba is not None:
                return rgba, False

    tinted = opaque_total > 0 and (opaque_tinted / opaque_total) >= TINT_COVERAGE_THRESHOLD
    return _composite(layers), tinted

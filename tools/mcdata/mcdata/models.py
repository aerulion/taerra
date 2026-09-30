from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from .assets import AssetTree, strip_namespace

MAX_PARENT_DEPTH = 32
CELL_SIZE = 16.0
MAX_AGE = 25
SIDE_DIRECTIONS = ("north", "south", "west", "east")
FACE_PROPERTIES = frozenset({"north", "south", "east", "west", "up", "down"})

TINT_COVERAGE_THRESHOLD = 0.5

TINT_OVERRIDES = frozenset({"minecraft:water", "minecraft:bubble_column"})

CONSTANT_TINTS = {
    "minecraft:redstone_wire": (0x4C, 0x00, 0x00),
    "minecraft:melon_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:pumpkin_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:attached_melon_stem": (0xE0, 0xC7, 0x1C),
    "minecraft:attached_pumpkin_stem": (0xE0, 0xC7, 0x1C),
}

NON_BLOCK_IDS = frozenset({"minecraft:item_frame", "minecraft:glow_item_frame"})

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
    block_id: str
    model_ref: str
    elements: list[dict] = field(default_factory=list)
    textures: dict[str, str] = field(default_factory=dict)

    def resolve_texture(self, value: str) -> str | None:
        seen = 0
        while isinstance(value, str) and value.startswith("#"):
            seen += 1
            if seen > MAX_PARENT_DEPTH:
                return None
            value = self.textures.get(value[1:], "")
        return value or None


def _texture_value(raw: object) -> str | None:
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
                penalty += MAX_AGE - int(value)
            elif name in ("level", "power", "distance", "layers", "bites", "delay") and value.isdigit():
                penalty += int(value)
    return penalty, state_key


@dataclass(frozen=True)
class ModelPlacement:
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
    for state_key in variants:
        if not state_key:
            return False
        for assignment in state_key.split(","):
            if assignment.partition("=")[0] not in FACE_PROPERTIES:
                return False
    return bool(variants)


def canonical_model_refs(blockstate: dict) -> list[ModelPlacement]:
    variants = blockstate.get("variants")
    if isinstance(variants, dict) and variants:
        if _is_face_keyed(variants):
            return _unique(_placement_of(entry) for entry in variants.values())
        best_key = min(variants.keys(), key=_variant_penalty)
        return _unique([_placement_of(variants[best_key])])

    multipart = blockstate.get("multipart")
    if isinstance(multipart, list) and multipart:
        return _unique(_placement_of(part.get("apply")) for part in multipart)
    return []


def _unique(values) -> list[ModelPlacement]:
    seen: dict[ModelPlacement, None] = {}
    for value in values:
        if value is not None:
            seen.setdefault(value, None)
    return list(seen)


def flatten_model(tree: AssetTree, block_id: str, model_ref: str) -> ResolvedModel:
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
            resolved.textures.setdefault(name, _texture_value(raw) or "")
        if not resolved.elements and isinstance(model.get("elements"), list):
            resolved.elements = model["elements"]
        current = model.get("parent")
    return resolved


def _face_area(element: dict, axis: int) -> float:
    other = [a for a in range(3) if a != axis]
    try:
        return abs(
            (float(element["to"][other[0]]) - float(element["from"][other[0]]))
            * (float(element["to"][other[1]]) - float(element["from"][other[1]]))
        )
    except (KeyError, TypeError, ValueError):
        return 0.0


def _outer_elements(elements: list[dict], direction: str) -> list[dict]:
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
        out_alpha = source_alpha + result_alpha * (1.0 - source_alpha)
        safe_alpha = np.where(out_alpha > 0.0, out_alpha, 1.0)
        result_rgb = (source_rgb * source_alpha + result_rgb * result_alpha * (1.0 - source_alpha)) / safe_alpha
        result_alpha = out_alpha
    return (np.concatenate([result_rgb, result_alpha], axis=2) * 255.0).round().clip(0, 255).astype(np.uint8)


def _opaque_pixels(rgba: np.ndarray, alpha_threshold: int = 16) -> int:
    return int((rgba[..., 3] >= alpha_threshold).sum())


def face_texture(tree: AssetTree, model: ResolvedModel, direction: str) -> tuple[np.ndarray | None, bool]:
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
            break

    if not layers and direction == "up":
        return face_texture(tree, model, "side")

    if not layers:
        particle = model.resolve_texture("#particle") or model.textures.get("particle")
        if particle:
            rgba = tree.texture(strip_namespace(particle))
            if rgba is not None:
                return rgba, False

    tinted = opaque_total > 0 and (opaque_tinted / opaque_total) >= TINT_COVERAGE_THRESHOLD
    return _composite(layers), tinted

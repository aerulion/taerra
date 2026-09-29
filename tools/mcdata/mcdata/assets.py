"""Access to a vanilla ``assets/`` tree.

Assets come from the Corpium/minecraft-assets repository, which archives the extracted client jar on one
``version/<id>`` branch per Minecraft version. A blobless sparse checkout of the three directories this tool reads is
about 30 MB, against roughly 40 MB for the client jar, and it re-fetches incrementally.
"""

from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import numpy as np
from PIL import Image

DEFAULT_REPOSITORY = "https://github.com/Corpium/minecraft-assets.git"
SPARSE_PATHS = (
    "assets/minecraft/blockstates",
    "assets/minecraft/models",
    "assets/minecraft/textures/block",
)


class AssetError(RuntimeError):
    """Raised when the asset tree is missing or unusable."""


def _run(command: list[str], cwd: Path | None = None) -> None:
    result = subprocess.run(command, cwd=cwd, capture_output=True, text=True)
    if result.returncode != 0:
        raise AssetError(f"{' '.join(command)} failed:\n{result.stderr.strip()}")


def provision(version: str, cache_dir: Path, repository: str = DEFAULT_REPOSITORY) -> Path:
    """Ensures a sparse checkout of ``version/<version>`` exists in the cache and returns its root."""
    branch = f"version/{version}"
    checkout = cache_dir / version
    if (checkout / ".git").is_dir():
        _run(["git", "fetch", "--depth", "1", "origin", branch], cwd=checkout)
        _run(["git", "checkout", "-f", "FETCH_HEAD"], cwd=checkout)
        return checkout

    cache_dir.mkdir(parents=True, exist_ok=True)
    _run(
        [
            "git", "clone", "--filter=blob:none", "--no-checkout",
            "--single-branch", "--branch", branch, "--depth", "1",
            repository, str(checkout),
        ]
    )
    _run(["git", "sparse-checkout", "init", "--cone"], cwd=checkout)
    _run(["git", "sparse-checkout", "set", *SPARSE_PATHS], cwd=checkout)
    _run(["git", "checkout"], cwd=checkout)
    return checkout


@dataclass(frozen=True)
class AssetTree:
    """A resolved ``assets/minecraft`` directory."""

    root: Path

    @staticmethod
    def locate(path: Path) -> "AssetTree":
        """Accepts a repository root, an ``assets`` dir or an ``assets/minecraft`` dir and normalizes it."""
        for candidate in (path / "assets" / "minecraft", path / "minecraft", path):
            if (candidate / "blockstates").is_dir() and (candidate / "models").is_dir():
                return AssetTree(candidate)
        raise AssetError(f"No assets/minecraft tree with blockstates/ and models/ under {path}")

    def blockstate_ids(self) -> list[str]:
        return sorted(p.stem for p in (self.root / "blockstates").glob("*.json"))

    def read_json(self, relative: str) -> dict:
        target = self.root / relative
        if not target.is_file():
            raise FileNotFoundError(relative)
        return json.loads(target.read_text(encoding="utf-8"))

    def blockstate(self, block_name: str) -> dict:
        return self.read_json(f"blockstates/{block_name}.json")

    def model(self, model_ref: str) -> dict:
        return self.read_json(f"models/{strip_namespace(model_ref)}.json")

    @lru_cache(maxsize=2048)
    def texture(self, texture_ref: str) -> np.ndarray | None:
        """Loads a texture as RGBA, cropped to its first frame when animated."""
        relative = f"textures/{strip_namespace(texture_ref)}.png"
        target = self.root / relative
        if not target.is_file():
            return None
        with Image.open(target) as image:
            rgba = np.asarray(image.convert("RGBA"), dtype=np.uint8)
        height, width = rgba.shape[0], rgba.shape[1]
        # Animated textures are a vertical strip of square frames; only the first frame is representative.
        if height > width and width > 0 and height % width == 0:
            rgba = rgba[:width]
        return rgba


def strip_namespace(reference: str) -> str:
    """``minecraft:block/stone`` and ``block/stone`` both become ``block/stone``."""
    return reference.split(":", 1)[-1]

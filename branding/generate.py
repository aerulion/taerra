"""Renders the images listed in images.toml with headless Chrome."""

import argparse
import hashlib
import re
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path
from urllib.parse import urlencode

HERE = Path(__file__).resolve().parent
ASSETS = "https://raw.githubusercontent.com/aerulion/taerra/main/branding/images/modrinth/"
CHROMES = (
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
    "chrome",
)


def stamp_assets(markdown):
    """Gives every hosted asset in the description a ?v= of its content, so caches fetch it anew once it changed."""
    def stamp(match):
        name = match.group(1)
        file = next((f for f in (HERE / "images/modrinth" / name, HERE / name) if f.is_file()), None)
        return match.group(0) if not file else f"{ASSETS}{name}?v={hashlib.sha1(file.read_bytes()).hexdigest()[:8]}"
    return re.sub(rf"{re.escape(ASSETS)}([\w.-]+)(?:\?v=\w+)?", stamp, markdown)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("only", nargs="*", help="render only the images whose file contains one of these")
    parser.add_argument("--config", type=Path, default=HERE / "images.toml")
    parser.add_argument("--out", type=Path, default=HERE / "images")
    parser.add_argument("--tiles", type=Path, default=HERE.parent / "run/plugins/Taerra",
                        help="the plugin folder holding tiles/ and tiles-iso/ (default: the dev server's)")
    parser.add_argument("--chrome", help="Chrome or Chromium executable")
    args = parser.parse_args()

    chrome = next(filter(None, map(shutil.which, [args.chrome] if args.chrome else CHROMES)), None)
    if not chrome:
        sys.exit("Chrome or Chromium not found, pass --chrome")
    if not (args.tiles / "tiles-iso").is_dir():
        sys.exit(f"No tiles-iso in {args.tiles}: render a world on the dev server first, or pass --tiles")

    for image in tomllib.loads(args.config.read_text())["image"]:
        if args.only and not any(part in image["file"] for part in args.only):
            continue
        width, height = image["size"]
        # split = n cuts the picture into n side by side, written as <file>-1 to -n, left to right.
        parts = image.get("split", 1)
        # Any other key of the entry, like a section's title, goes to the layout as it is.
        options = {key: value for key, value in image.items() if key not in ("file", "layout", "size", "split")}
        for part in range(parts):
            file = Path(image["file"])
            if parts > 1:
                file = file.with_stem(f"{file.stem}-{part + 1}")
            out = args.out / file
            out.parent.mkdir(parents=True, exist_ok=True)
            query = urlencode({
                "layout": image["layout"],
                "width": width,
                "height": height,
                "left": part * width // parts,
                "tiles": args.tiles.resolve().as_uri(),
                **options,
            })
            subprocess.run([
                chrome,
                "--headless",
                "--disable-gpu",
                "--hide-scrollbars",
                "--force-device-scale-factor=1",
                "--allow-file-access-from-files",
                "--default-background-color=00000000",
                # Virtual time stands still while tiles load, so this only has to cover the page's own work.
                "--virtual-time-budget=30000",
                f"--window-size={width // parts},{height}",
                f"--screenshot={out}",
                f"{(HERE / 'render.html').as_uri()}?{query}",
            ], check=True, capture_output=True)
            print(f"{file}  {width // parts}x{height}  {out.stat().st_size // 1024} KiB")

    description = HERE / "modrinth-description.md"
    description.write_text(stamp_assets(description.read_text()))


if __name__ == "__main__":
    main()

"""Renders the images listed in images.toml with headless Chrome."""

import argparse
import re
import shutil
import subprocess
import sys
import tomllib
from pathlib import Path
from urllib.parse import urlencode

HERE = Path(__file__).resolve().parent
CHROMES = (
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    "google-chrome",
    "google-chrome-stable",
    "chromium",
    "chromium-browser",
    "chrome",
)


def license_text(markdown):
    """LICENSE.md as plain text, for hosting where Markdown is not rendered."""
    lines = []
    for line in markdown.splitlines():
        # Bold only marks defined terms, which plain text puts in quotes.
        line = re.sub(r"\*\*(.+?)\*\*", r'"\1"', line)
        line = re.sub(r"`(.+?)`|<(https?://[^>]+)>", lambda m: m.group(1) or m.group(2), line)
        if heading := re.match(r"(#+) (.*)", line):
            level, line = heading.groups()
            lines += [line, ("=" if level == "#" else "-") * len(line)]
        elif line != "---":
            lines.append(line)
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("only", nargs="*", help="render only the images whose file contains one of these")
    parser.add_argument("--config", type=Path, default=HERE / "images.toml")
    parser.add_argument("--out", type=Path, default=HERE / "images")
    parser.add_argument("--tiles", type=Path, default=HERE.parent / "run/plugins/Taerra",
                        help="the plugin folder holding tiles/ and tiles-iso/ (default: the dev server's)")
    parser.add_argument("--chrome", help="Chrome or Chromium executable")
    args = parser.parse_args()

    (HERE / "license.txt").write_text(license_text((HERE.parent / "LICENSE.md").read_text()))

    chrome = next(filter(None, map(shutil.which, [args.chrome] if args.chrome else CHROMES)), None)
    if not chrome:
        sys.exit("Chrome or Chromium not found, pass --chrome")
    if not (args.tiles / "tiles-iso").is_dir():
        sys.exit(f"No tiles-iso in {args.tiles}: render a world on the dev server first, or pass --tiles")

    for image in tomllib.loads(args.config.read_text())["image"]:
        if args.only and not any(part in image["file"] for part in args.only):
            continue
        out = args.out / image["file"]
        out.parent.mkdir(parents=True, exist_ok=True)
        width, height = image["size"]
        # Any other key of the entry, like a section's title, goes to the layout as it is.
        options = {key: value for key, value in image.items() if key not in ("file", "layout", "size")}
        query = urlencode({
            "layout": image["layout"],
            "width": width,
            "height": height,
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
            # Virtual time stands still while tiles load, so this only has to cover the page's own work.
            "--virtual-time-budget=30000",
            f"--window-size={width},{height}",
            f"--screenshot={out}",
            f"{(HERE / 'render.html').as_uri()}?{query}",
        ], check=True, capture_output=True)
        print(f"{image['file']}  {width}x{height}  {out.stat().st_size // 1024} KiB")


if __name__ == "__main__":
    main()

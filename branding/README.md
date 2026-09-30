# Brand images

The store icons, headers and gallery images in `images/`, rendered from the logo
(`plugin/src/main/resources/web/images/icon.svg`), the web map's fonts and the dev server's map tiles.

```bash
python3 branding/generate.py                  # every image in images.toml
python3 branding/generate.py modrinth/        # only those whose file contains "modrinth/"
python3 branding/generate.py --tiles <dir>    # tiles from another plugin folder
```

Needs Python 3.11+ and Chrome or Chromium (`--chrome <path>` if it is not found), nothing else. The tiles come from
`run/plugins/Taerra` by default: start `./gradlew :plugin:runDevBundleServer` and render the overworld, nether and
end, isometric and top-down, before the first run.

## Adding a size

Every image is one entry in `images.toml`: a file, a layout and a size in pixels. Every layout adapts to any size and
aspect ratio, so another site is one more entry:

```toml
[[image]]
file = "curseforge/logo.png"
layout = "icon"
size = [400, 400]
```

The layouts are in `render.html`. Where each picture looks is set by the constants at the top of its script
(`OVERWORLD`, `DETAIL`, `BACKDROP`, `TOP_DOWN`), in tile coordinates. Rerun with a filter to iterate on one image.

Changes here never trigger a release.

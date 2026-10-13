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

`split = 3` cuts one picture into three side by side, written as `<file>-1` to `-3`: `instagram/pinned-*.png` are
the profile's three pinned posts, one picture across its top row. Portrait sizes stack a gallery's pictures. The
`discord/` emoji are 128 px and named as the emoji: the white mark is for dark themes, the copper one reads on both.

The layouts are in `render.html`. Where each picture looks is set by the constants at the top of its script
(`OVERWORLD`, `DETAIL`, `BACKDROP`, `TOP_DOWN`), in tile coordinates. Rerun with a filter to iterate on one image.

## Showreel

```bash
node branding/video.mjs                       # images/video/showreel.mp4, 1920×1080 at 60 fps
node branding/video.mjs --scale 2 --out branding/images/video/showreel-4k.mp4   # 3840×2160
node branding/video.mjs --still 4.5,19        # only those seconds, as PNGs next to the video
node branding/video.mjs --fps 30 --from 14 --to 23
node branding/video.mjs --music branding/showcase-audio.wav           # with the music
node branding/video.mjs --music branding/showcase-audio.wav --sound   # only lay the music anew onto the rendered video
```

`video.html` is the timeline, every scene drawn from the time alone by its `seek(t)`, and `video.mjs` renders it frame
by frame through Chrome's DevTools protocol into ffmpeg, so a slow frame never stutters. Needs Node 22+, ffmpeg and
Chrome, nothing installed. The web map scene films the real web map: start the dev server first, it is clicked through
in a frame at `--web` (default `http://localhost:8100`). The frame is logged in with every permission and shows three
walking players, both made up in the page, so nothing changes on the server; it needs WorldGuard regions and waypoints
around the village at 563, -1075. Hide in the chunk selection lays the tiles in `run/plugins/Taerra/video-hidden/` over
the map: the `tiles/` and `tiles-iso/` the dev server renders with `PATCH`'s chunks listed under `hidden:` in
`limits.yml` (then `/taerra render radius minecraft:overworld 648 -1072 64`), copied there before taking them out again.

The speed figures are Corpium's main world, so the speed scene and the feature list show that world, loaded from
<https://map.corpium.net> while rendering. The scenes' start times are `AT` at the top of its script, counted in
bars of the track: "Never Give Up" by Enrize on AudioJungle, its 60 second version as `showcase-audio.wav`, 139.63 bpm,
whose drop is 3.433 s in. The in-game scene shows `ingame/ingame-1.png` to `-4.png`, 16:9, captioned in
`INGAME`; a missing one shows its file name instead. Another track needs `BEAT`, `DROP` and the scenes' bars changed to its own.

The only sound is the music passed in. Music files are kept out of git: the track is licensed, not ours to publish.

The closing credit is aerulion's own brand, set by its Nyx guidelines (aerulion.github.io `src/data/design.ts`): only
#000 and #fff, 1px rays, the label in IBM Plex Mono, the wordmark in Tektur Bold, the mark traced then inked, and a 30°
curtain to close. `aerulion/mark.svg`, `Tektur-Bold.woff2` and `IBMPlexMono-Regular.woff2` (both SIL Open Font License)
come from the aerulion.github.io brand folder.

`modrinth-description.md` is the Modrinth project description. Its showreel is embedded by its YouTube ID. Every paragraph and list item in it is one line, never
wrapped: Modrinth renders hard line breaks inconsistently. Its images are hosted
under <https://assets.corpium.net/taerra/>, each by its file name: upload `description-header.png` and every
`section-*.png` there after regenerating them. Every run also gives those URLs in the description a `?v=` of the file's content, so
Modrinth's image proxy fetches a changed image anew: paste the description into Modrinth again after uploading.
`python3 branding/generate.py none` only does that, rendering nothing.

The `view` layout is a picture and nothing else, for taerra.dev to set its own copy over: `python3 branding/generate.py site/`
writes the website's into `site/src/assets/` and its link preview into `site/public/og.png`.

Changes here never trigger a release.

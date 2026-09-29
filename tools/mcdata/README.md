# Block data generator

Generates `src/main/resources/block_data.json` directly from vanilla Minecraft assets. No files are placed by hand.

Assets come from the [Corpium/minecraft-assets](https://github.com/Corpium/minecraft-assets) repository, which
archives the extracted client jar on one `version/<id>` branch per Minecraft version.

## What it produces

One entry per block:

```json
"minecraft:grass_block": {
  "top": "#8C8C8C",
  "side": "#79553A",
  "shape": "full",
  "tint_top": true,
  "occupancy": 1.0
}
```

| Field | Meaning |
| --- | --- |
| `top` | Color of the upward face. Used by the top-down map and by the isometric top face. |
| `side` | Color of the outward side face. Only the isometric renderer needs it. |
| `shape` | `full`, `thin` or `none` — see below. |
| `tint_top` / `tint_side` | Present when that face is biome tinted at runtime. Omitted when false. |
| `occupancy` | Measured solid fraction of the block cell, for diagnostics. |

A block the game never draws — a barrier, a light block, a structure void — has no model to measure, so its entry
carries only `shape: "none"` and `occupancy`. It is written out rather than skipped so that a block *missing* from
the data means one this tool has never seen, which the plugin renders as a debug-colored cube.

### Why there is a separate side color

A grass block is green on top and dirt-brown on the side. An isometric view that derived the side by darkening the
top color would render it as green dirt. 564 of 1189 blocks have a side color that differs from their top.

### Shape classes

| Class | Meaning |
| --- | --- |
| `full` | Fills its cell. Forms a run in a column stack and hides the side face behind it. |
| `thin` | Visible but not substantial: torches, flowers, crops, rails, carpets. Colors a top face but forms no run and culls nothing. |
| `none` | Never drawn: air, barrier, light, structure void. |

Shape is measured from model geometry rather than guessed from material flags, but volume alone is not enough:

- **Sheets do not count as solid.** Mature wheat measures 0.41 by raw volume and a hopper only 0.31, yet wheat must
  stay thin. Crops are crossed sheets while a hopper is built from boxes, so elements thinner than one model unit
  only count when they seal a volume between them.
- **Sealed shells do count.** Powder snow is authored as a 0.002 thick box and a mushroom block as six single faces.
  Flood filling from outside the cell recovers their real footprint without a per-block exception.
- **Rotations matter.** Blockstates apply one model at several rotations; ignoring them collapses a mushroom block's
  six faces onto one and measures a cube as a flat sheet.
- **A few blocks cannot be measured at all.** Fluids and block entities (chests, shulker boxes, banners, heads) carry
  no geometry, only a particle texture. These are listed explicitly in `shape.py`.

## Install

```bash
cd tools/mcdata
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -e .
```

## Run

Provision the assets automatically (requires read access to the private assets repository):

```bash
python -m mcdata.generate --version 26.2
```

Or point at an existing checkout:

```bash
python -m mcdata.generate --assets-dir ../../../minecraft-assets
```

Provisioned checkouts are cached under `tools/mcdata/.cache/<version>` as blobless sparse clones of the three
directories this tool reads, about 30 MB, and re-fetch incrementally.

### Useful options

```bash
python -m mcdata.generate --version 26.2 \
  --output ../../src/main/resources/block_data.json \
  --full-threshold 0.25 \
  --bandwidth 0.055 \
  --alpha-threshold 16
```

`--no-report` skips `last_report.json`.

## How colors are chosen

For each block the tool resolves its blockstate to a canonical variant, flattens the model's parent chain, resolves
`#ref` texture indirection, and composites the outward faces for that direction in element order. That compositing
is what puts the tinted `grass_block_side_overlay` fringe on top of the dirt side texture.

The composited pixels are then clustered in OKLab space by weighted mean shift, and the dominant cluster becomes the
color. Taking the dominant cluster rather than a mean keeps outlines, highlights and stray pixels from dragging the
result. Some details that matter:

- **Faces are chosen by area, not height.** A repeater, a comparator and a candle cake all have a small part standing
  proud of the body; picking whatever sits highest would sample the torch or the candle rather than the surface that
  covers the block.
- **Compositing is non-premultiplied.** Blending translucent textures in premultiplied form against a transparent
  background drags them toward black, which turned white stained glass gray.
- **Animated textures use their first frame only**, since they are stored as a vertical strip.
- **Crops are sampled fully grown**, so a wheat field reads golden rather than as bare sprouts.

## Run tests

```bash
cd tools/mcdata
source .venv/bin/activate
python -m unittest discover -s tests -v
```

The tests cover the geometry and blockstate rules and need no asset tree.

package net.aerulion.taerra.rendering.style;

import java.util.Arrays;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.source.ColumnSource;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Fog {

  public static final @NotNull BlockType GROUND = BlockType.STONE;

  public static final int NO_MIST = 0;

  public static final byte OPEN = 0;

  public static final byte HIDDEN = 1;

  public static final byte TEASED = 2;

  public static final int REACH_CHUNKS = 8;

  public static final int AROUND = 2 * REACH_CHUNKS + 1;

  public static final int SKY_COLOR = 0xFFF4F6F8;

  public static final int SKY_ALPHA = 0xBF;

  public static final int SKY_Y = 192;

  public static final int SKY_THICKNESS = 4;

  private static final int LAYER_HEIGHT = 32;
  private static final int COVER_FADE = 16;
  private static final int MIN_SPILL = 8;
  private static final int MAX_SPILL = 60;
  private static final int MIN_FADE = 32;
  private static final int MAX_FADE = 60;
  private static final float LOBE_SCALE = 64.0f;
  private static final float ZIGZAG_SCALE = 20.0f;
  private static final int WARP = 40;
  private static final int REACH_BLOCKS = REACH_CHUNKS * 16 - 15;
  private static final double TAPER = 32.0;
  private static final float WARP_SCALE = 80.0f;
  private static final int DENSITY_BITS = 4;
  private static final int FULL = (1 << DENSITY_BITS) - 1;
  private static final int TEASER = 1 << DENSITY_BITS;
  private static final int SKY = TEASER << 1;
  private static final int LAYER_SHIFT = DENSITY_BITS + 2;
  private static final int SKY_CELL = 16;
  private static final float SKY_SCALE = 2.5f;
  private static final float SKY_COVER = 0.5f;
  private static final double SKY_SCATTER = 0.1;
  private static final double SKY_LONE = 1.5;
  private static final int CHUNK_SIZE = 16;
  private static final float[] OCTAVE_SCALES = {80.0f, 34.0f, 14.0f, 6.0f};
  private static final float[] OCTAVE_WEIGHTS = {0.5f, 0.3f, 0.14f, 0.06f};
  private static final float THIN = 0.32f;
  private static final float THICK = 0.68f;
  private static final int TONES = 2;
  private static final int OPACITY_STEPS = 6;
  private static final float TEASER_THIN = 0.4f;
  private static final float TEASER_THICK = 0.8f;
  private static final float TEASER_GREY = 0.5f;
  private static final float CLEAR = 1.15f;
  private static final float SOLID = -0.15f;
  private static final float EDGE = 0.3f;
  private static final float PUFF_SCALE = 12.0f;
  private static final float PUFFINESS = 1.0f;
  private static final float SHADOW_REACH = 3.0f;
  private static final float SHADOW = 0.3f;
  private static final int SHADOW_COLOR = 0xFF1B2330;
  private static final float LIGHT_STEP = 4.0f;
  private static final float LIGHT_GAIN = 6.0f;
  private static final int CLOUD_LIGHT = 0xFFE6EAEF;
  private static final int CLOUD_SHADE = 0xFFB4BDC8;
  private static final long REACH_SEED = 0x9E3779B97F4A7C15L;
  private static final long FADE_SEED = 0x5851F42D4C957F2DL;
  private static final long CLOUD_SEED = 0x2545F4914F6CDD1DL;
  private static final long PUFF_SEED = 0x3C6EF372FE94F82BL;
  private static final long WARP_SEED = 0x510E527FADE682D1L;
  private static final long SKY_SEED = 0xA54FF53A5F1D36F1L;

  private Fog() {
    super();
  }

  public static int ground(final int @Nullable [] tops) {
    if (tops == null) {
      return ColumnSource.EMPTY_COLUMN;
    }
    final int @NotNull [] sorted =
        Arrays.stream(tops).filter(top -> top != ColumnSource.EMPTY_COLUMN).sorted().toArray();
    return sorted.length == 0 ? ColumnSource.EMPTY_COLUMN : sorted[sorted.length / 2];
  }

  public static int groundAt(
      final int @NotNull [] groundAround, final int localX, final int localZ) {
    final float offsetX = (localX - 7.5f) / CHUNK_SIZE;
    final float offsetZ = (localZ - 7.5f) / CHUNK_SIZE;
    final int towardX = offsetX < 0 ? 0 : 2;
    final int towardZ = offsetZ < 0 ? 0 : 2;
    final float weightX = Math.abs(offsetX);
    final float near =
        groundAround[4] + (groundAround[towardX * 3 + 1] - groundAround[4]) * weightX;
    final float far =
        groundAround[3 + towardZ]
            + (groundAround[towardX * 3 + towardZ] - groundAround[3 + towardZ]) * weightX;
    return Math.round(lerp(near, far, Math.abs(offsetZ)));
  }

  public static int @NotNull [] hidden(
      final int chunkX,
      final int chunkZ,
      final int @NotNull [] groundAround,
      final boolean teased) {
    final int @NotNull [] mist = new int[CHUNK_SIZE * CHUNK_SIZE];
    for (int localX = 0; localX < CHUNK_SIZE; localX++) {
      for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
        mist[ChunkColumns.columnIndex(localX, localZ)] =
            encode(groundAt(groundAround, localX, localZ), FULL, teased)
                | (sky((chunkX << 4) + localX, (chunkZ << 4) + localZ, 1.0) ? SKY : 0);
      }
    }
    return mist;
  }

  public static int @NotNull [] mist(
      final int chunkX,
      final int chunkZ,
      final @NotNull ChunkColumns columns,
      final int @NotNull [] groundAround,
      final byte @NotNull [] hiddenAround) {
    final int @NotNull [] mist = new int[CHUNK_SIZE * CHUNK_SIZE];
    final int cells = CHUNK_SIZE / SKY_CELL;
    final boolean @NotNull [] skies = new boolean[cells * cells];
    for (int cellX = 0; cellX < cells; cellX++) {
      for (int cellZ = 0; cellZ < cells; cellZ++) {
        final int localX = cellX * SKY_CELL + SKY_CELL / 2;
        final int localZ = cellZ * SKY_CELL + SKY_CELL / 2;
        skies[cellX * cells + cellZ] =
            sky(
                (chunkX << 4) + localX,
                (chunkZ << 4) + localZ,
                thickness(chunkX, chunkZ, localX, localZ, hiddenAround));
      }
    }
    for (int localX = 0; localX < CHUNK_SIZE; localX++) {
      for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
        if (columns.topY(ChunkColumns.columnIndex(localX, localZ)) == ChunkColumns.EMPTY_COLUMN) {
          continue;
        }
        final boolean teased =
            nearest(chunkX, chunkZ, localX, localZ, hiddenAround, TEASED)
                < nearest(chunkX, chunkZ, localX, localZ, hiddenAround, HIDDEN);
        final int density =
            (int)
                Math.round(
                    FULL
                        * Math.clamp(
                            thickness(chunkX, chunkZ, localX, localZ, hiddenAround), 0.0, 1.0));
        final boolean sky = skies[localX / SKY_CELL * cells + localZ / SKY_CELL];
        if (density > 0 || sky) {
          mist[ChunkColumns.columnIndex(localX, localZ)] =
              encode(groundAt(groundAround, localX, localZ), density, teased) | (sky ? SKY : 0);
        }
      }
    }
    return mist;
  }

  public static int layerY(final int mist) {
    return mist >> LAYER_SHIFT;
  }

  public static boolean teased(final int mist) {
    return (mist & TEASER) != 0;
  }

  public static int veil(
      final int argb, final int mist, final float x, final float z, final int blockY) {
    if (mist == NO_MIST) {
      return argb;
    }
    final float cover = Math.clamp((float) (layerY(mist) - blockY) / COVER_FADE, 0.0f, 1.0f);
    if (cover <= 0.0f) {
      return argb;
    }
    final float density = (float) (mist & FULL) / FULL;
    if (density <= 0.0f) {
      return argb;
    }
    final float cellX = (float) Math.floor(x) + 0.5f;
    final float cellZ = (float) Math.floor(z) + 0.5f;
    final float cloud = cloud(cellX, cellZ);
    final float reach =
        teased(mist) ? lerp(TEASER_THIN, TEASER_THICK, smoothstep(THIN, THICK, cloud)) : 1.0f;
    final float shadowX = cellX - SHADOW_REACH;
    final float shadowZ = cellZ - SHADOW_REACH;
    final float body = body(cloud, cellX, cellZ, density) * cover;
    final float shadow =
        Math.max(0.0f, body(cloud(shadowX, shadowZ), shadowX, shadowZ, density) * cover - body)
            * reach
            * SHADOW;
    final float opacity = step(body * reach, OPACITY_STEPS);
    final int ground =
        teased(mist)
            ? MapStyle.blend(argb, grey(argb), step(TEASER_GREY * body, OPACITY_STEPS))
            : argb;
    return MapStyle.blend(
        MapStyle.blend(ground, SHADOW_COLOR, step(shadow, OPACITY_STEPS)),
        step(body, OPACITY_STEPS) < 1.0f ? CLOUD_LIGHT : tone(cloud, cellX, cellZ),
        opacity);
  }

  public static boolean sky(final int mist) {
    return (mist & SKY) != 0;
  }

  public static int ceilingY(final int mist) {
    return sky(mist) ? SKY_Y + SKY_THICKNESS - 1 : layerY(mist);
  }

  public static int around(final int offsetX, final int offsetZ) {
    return (offsetX + REACH_CHUNKS) * AROUND + offsetZ + REACH_CHUNKS;
  }

  private static int grey(final int argb) {
    final int luma =
        (((argb >> 16) & 0xFF) * 77 + ((argb >> 8) & 0xFF) * 150 + (argb & 0xFF) * 29) >> 8;
    return (argb & 0xFF000000) | luma << 16 | luma << 8 | luma;
  }

  private static int tone(final float cloud, final float cellX, final float cellZ) {
    final float lit =
        Math.clamp(
            0.35f
                + 0.45f * smoothstep(THIN, THICK, cloud)
                + (cloud - cloud(cellX - LIGHT_STEP, cellZ - LIGHT_STEP)) * LIGHT_GAIN,
            0.0f,
            1.0f);
    return MapStyle.blend(CLOUD_SHADE, CLOUD_LIGHT, step(lit, TONES));
  }

  private static float body(final float cloud, final float x, final float z, final float density) {
    if (density >= 1.0f) {
      return 1.0f;
    }
    final float puffs = cloud + (noise(x, z, PUFF_SCALE, PUFF_SEED) - 0.5f) * PUFFINESS;
    final float threshold = lerp(CLEAR, SOLID, density);
    return smoothstep(threshold - EDGE, threshold + EDGE, puffs);
  }

  private static double thickness(
      final int chunkX,
      final int chunkZ,
      final int localX,
      final int localZ,
      final byte @NotNull [] hiddenAround) {
    final int blockX = (chunkX << 4) + localX;
    final int blockZ = (chunkZ << 4) + localZ;
    final double nearest = distance(localX, localZ, hiddenAround, OPEN);
    final double distance = nearest(chunkX, chunkZ, localX, localZ, hiddenAround, OPEN);
    final float spill =
        MIN_SPILL
            + smoothstep(
                    0.15f,
                    0.85f,
                    0.55f * noise(blockX, blockZ, LOBE_SCALE, REACH_SEED)
                        + 0.45f * noise(blockX, blockZ, ZIGZAG_SCALE, REACH_SEED + 1))
                * (MAX_SPILL - MIN_SPILL);
    final float fade =
        MIN_FADE + noise(blockX, blockZ, LOBE_SCALE, FADE_SEED) * (MAX_FADE - MIN_FADE);
    return 1.0
        - Math.max(0.0, distance - spill) / fade
        - 2.0 * (1.0 - Math.clamp((REACH_BLOCKS - nearest) / TAPER, 0.0, 1.0));
  }

  private static double nearest(
      final int chunkX,
      final int chunkZ,
      final int localX,
      final int localZ,
      final byte @NotNull [] hiddenAround,
      final byte kind) {
    final int blockX = (chunkX << 4) + localX;
    final int blockZ = (chunkZ << 4) + localZ;
    return Math.min(
        distance(localX, localZ, hiddenAround, kind),
        distance(
            localX + warp(blockX, blockZ, WARP_SEED),
            localZ + warp(blockX, blockZ, WARP_SEED + 1),
            hiddenAround,
            kind));
  }

  private static boolean sky(final int blockX, final int blockZ, final double thickness) {
    final int cellX = Math.floorDiv(blockX, SKY_CELL);
    final int cellZ = Math.floorDiv(blockZ, SKY_CELL);
    final float lone = (float) Math.clamp(-thickness / SKY_LONE, 0.0, 1.0);
    return lerp(
            0.65f * noise(cellX, cellZ, SKY_SCALE, SKY_SEED)
                + 0.35f * hash(cellX, cellZ, SKY_SEED + 1),
            hash(cellX, cellZ, SKY_SEED + 2),
            lone)
        > SKY_COVER + (1.0 - Math.min(1.0, thickness)) * SKY_SCATTER;
  }

  private static double distance(
      final int localX, final int localZ, final byte @NotNull [] hiddenAround, final byte kind) {
    int squared = Integer.MAX_VALUE;
    for (int offsetX = -REACH_CHUNKS; offsetX <= REACH_CHUNKS; offsetX++) {
      final int gapX = gap(localX, offsetX);
      for (int offsetZ = -REACH_CHUNKS; offsetZ <= REACH_CHUNKS; offsetZ++) {
        final byte hidden = hiddenAround[around(offsetX, offsetZ)];
        if (hidden != OPEN && (kind == OPEN || hidden == kind)) {
          final int gapZ = gap(localZ, offsetZ);
          squared = Math.min(squared, gapX * gapX + gapZ * gapZ);
        }
      }
    }
    return squared == Integer.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(squared);
  }

  private static float step(final float value, final int steps) {
    return (float) Math.round(value * steps) / steps;
  }

  private static int encode(final int groundY, final int density, final boolean teased) {
    return (groundY + LAYER_HEIGHT) << LAYER_SHIFT | (teased ? TEASER : 0) | density;
  }

  private static float cloud(final float x, final float z) {
    float cloud = 0.0f;
    for (int octave = 0; octave < OCTAVE_SCALES.length; octave++) {
      cloud += noise(x, z, OCTAVE_SCALES[octave], CLOUD_SEED + octave) * OCTAVE_WEIGHTS[octave];
    }
    return cloud;
  }

  private static int gap(final int local, final int offset) {
    return Math.max(
        0, Math.max(offset * CHUNK_SIZE - local, local - offset * CHUNK_SIZE - (CHUNK_SIZE - 1)));
  }

  private static int warp(final int blockX, final int blockZ, final long seed) {
    return Math.round((noise(blockX, blockZ, WARP_SCALE, seed) - 0.5f) * 2.0f * WARP);
  }

  private static float noise(final float x, final float z, final float scale, final long seed) {
    final float scaledX = x / scale;
    final float scaledZ = z / scale;
    final int cellX = (int) Math.floor(scaledX);
    final int cellZ = (int) Math.floor(scaledZ);
    final float fx = smooth(scaledX - cellX);
    final float fz = smooth(scaledZ - cellZ);
    final float top = lerp(hash(cellX, cellZ, seed), hash(cellX + 1, cellZ, seed), fx);
    final float bottom = lerp(hash(cellX, cellZ + 1, seed), hash(cellX + 1, cellZ + 1, seed), fx);
    return lerp(top, bottom, fz);
  }

  private static float hash(final int x, final int z, final long seed) {
    long h = seed ^ ((long) x * 0x632BE59BD9B4E019L) ^ ((long) z * 0x85157AF5L);
    h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
    h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
    return (float) ((h ^ (h >>> 33)) >>> 40) / (1 << 24);
  }

  private static float smoothstep(final float from, final float to, final float value) {
    return smooth(Math.clamp((value - from) / (to - from), 0.0f, 1.0f));
  }

  private static float smooth(final float t) {
    return t * t * (3.0f - 2.0f * t);
  }

  private static float lerp(final float from, final float to, final float t) {
    return from + (to - from) * t;
  }
}

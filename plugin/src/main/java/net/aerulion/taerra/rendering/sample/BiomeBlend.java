package net.aerulion.taerra.rendering.sample;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.rendering.color.MapColorResolver;
import net.aerulion.taerra.rendering.color.TintColors;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BiomeBlend {

  public static final int RADIUS = 2;

  private static final int CHUNK_SIZE = 16;
  private static final int SPAN = CHUNK_SIZE + 2 * RADIUS;
  private static final int WINDOW = 2 * RADIUS + 1;
  private static final int CHANNELS = 13;
  private static final int COUNT = 12;

  private final @NotNull ChunkColumnsSource source;
  private final @NotNull MapColorResolver colors;
  private final @NotNull Long2ObjectOpenHashMap<TintColors[]> chunks =
      new Long2ObjectOpenHashMap<>();
  private long lastKey = Long.MIN_VALUE;
  private @Nullable TintColors @NotNull [] last = new TintColors[0];

  public BiomeBlend(
      final @NotNull ChunkColumnsSource source, final @NotNull MapColorResolver colors) {
    super();
    this.source = source;
    this.colors = colors;
  }

  public @Nullable TintColors at(final int blockX, final int blockZ) {
    final int chunkX = blockX >> 4;
    final int chunkZ = blockZ >> 4;
    final long key = ChunkCoordinate.key(chunkX, chunkZ);
    if (key != this.lastKey) {
      this.lastKey = key;
      this.last = this.chunks.computeIfAbsent(key, ignored -> blend(chunkX, chunkZ));
    }
    return this.last[ChunkColumns.columnIndex(blockX & 15, blockZ & 15)];
  }

  private @Nullable TintColors @NotNull [] blend(final int chunkX, final int chunkZ) {
    final @Nullable ChunkColumns @NotNull [] around = new ChunkColumns[9];
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        around[(offsetX + 1) * 3 + offsetZ + 1] =
            this.source.columnsFor(chunkX + offsetX, chunkZ + offsetZ);
      }
    }

    final int originX = chunkX * CHUNK_SIZE - RADIUS;
    final int originZ = chunkZ * CHUNK_SIZE - RADIUS;
    final @Nullable TintColors @NotNull [] cells = new TintColors[SPAN * SPAN];
    @Nullable Biome lastBiome = null;
    boolean lastVaries = false;
    @Nullable TintColors lastTint = null;
    boolean uniform = true;
    for (int cellX = 0; cellX < SPAN; cellX++) {
      for (int cellZ = 0; cellZ < SPAN; cellZ++) {
        final int blockX = originX + cellX;
        final int blockZ = originZ + cellZ;
        final @Nullable ChunkColumns columns =
            around[((blockX >> 4) - chunkX + 1) * 3 + (blockZ >> 4) - chunkZ + 1];
        final @Nullable Biome biome =
            columns == null
                ? null
                : columns.columnBiome(ChunkColumns.columnIndex(blockX & 15, blockZ & 15));
        if (biome != lastBiome) {
          lastBiome = biome;
          lastVaries = biome != null && this.colors.tintVaries(biome);
          lastTint = biome == null ? null : this.colors.tintAt(biome, blockX, blockZ);
        } else if (lastVaries) {
          lastTint = this.colors.tintAt(biome, blockX, blockZ);
        }
        cells[cellX * SPAN + cellZ] = lastTint;
        uniform &= lastTint != null && !lastVaries && lastTint == cells[0];
      }
    }
    if (uniform) {
      final @Nullable TintColors @NotNull [] blended = new TintColors[ChunkColumns.COLUMN_COUNT];
      Arrays.fill(blended, cells[0]);
      return blended;
    }

    final int @NotNull [] rows = new int[CHANNELS * CHUNK_SIZE * SPAN];
    for (int outX = 0; outX < CHUNK_SIZE; outX++) {
      for (int cellZ = 0; cellZ < SPAN; cellZ++) {
        final int base = (outX * SPAN + cellZ) * CHANNELS;
        for (int cellX = outX; cellX < outX + WINDOW; cellX++) {
          final @Nullable TintColors cell = cells[cellX * SPAN + cellZ];
          if (cell != null) {
            add(rows, base, 0, cell.water());
            add(rows, base, 3, cell.grass());
            add(rows, base, 6, cell.foliage());
            add(rows, base, 9, cell.dryFoliage());
            rows[base + COUNT]++;
          }
        }
      }
    }

    final @Nullable TintColors @NotNull [] blended = new TintColors[ChunkColumns.COLUMN_COUNT];
    final int @NotNull [] sums = new int[CHANNELS];
    for (int outX = 0; outX < CHUNK_SIZE; outX++) {
      for (int outZ = 0; outZ < CHUNK_SIZE; outZ++) {
        if (cells[(outX + RADIUS) * SPAN + outZ + RADIUS] == null) {
          continue;
        }
        Arrays.fill(sums, 0);
        for (int cellZ = outZ; cellZ < outZ + WINDOW; cellZ++) {
          final int base = (outX * SPAN + cellZ) * CHANNELS;
          for (int channel = 0; channel < CHANNELS; channel++) {
            sums[channel] += rows[base + channel];
          }
        }
        final int count = sums[COUNT];
        blended[ChunkColumns.columnIndex(outX, outZ)] =
            new TintColors(
                average(sums, 0, count),
                average(sums, 3, count),
                average(sums, 6, count),
                average(sums, 9, count));
      }
    }
    return blended;
  }

  private static void add(final int @NotNull [] sums, final int base, final int at, final int rgb) {
    sums[base + at] += (rgb >> 16) & 0xFF;
    sums[base + at + 1] += (rgb >> 8) & 0xFF;
    sums[base + at + 2] += rgb & 0xFF;
  }

  private static int average(final int @NotNull [] sums, final int at, final int count) {
    return ((sums[at] / count) << 16) | ((sums[at + 1] / count) << 8) | (sums[at + 2] / count);
  }
}

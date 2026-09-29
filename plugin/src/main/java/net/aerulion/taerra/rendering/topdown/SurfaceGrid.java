package net.aerulion.taerra.rendering.topdown;

import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SurfaceGrid {

  static final int HALO = 1;

  private static final int CHUNK_SIZE = 16;

  private final int stride;
  private final int originBlockX;
  private final int originBlockZ;
  private final int @NotNull [] surfaceY;
  private final int @NotNull [] floorY;
  private final Material @NotNull [] material;
  private final Material @NotNull [] cap;
  private final Biome @NotNull [] biome;

  private SurfaceGrid(final int stride, final int originBlockX, final int originBlockZ) {
    super();
    this.stride = stride;
    this.originBlockX = originBlockX;
    this.originBlockZ = originBlockZ;
    this.surfaceY = new int[stride * stride];
    this.floorY = new int[stride * stride];
    this.material = new Material[stride * stride];
    this.cap = new Material[stride * stride];
    this.biome = new Biome[stride * stride];
  }

  static @NotNull SurfaceGrid sample(
      final @NotNull ChunkColumnsSource source,
      final int startBlockX,
      final int startBlockZ,
      final int tileSize) {
    final int stride = tileSize + HALO * 2;
    final @NotNull SurfaceGrid surface =
        new SurfaceGrid(stride, startBlockX - HALO, startBlockZ - HALO);

    final int minChunkX = Math.floorDiv(surface.originBlockX, CHUNK_SIZE);
    final int maxChunkX = Math.floorDiv(surface.originBlockX + stride - 1, CHUNK_SIZE);
    final int minChunkZ = Math.floorDiv(surface.originBlockZ, CHUNK_SIZE);
    final int maxChunkZ = Math.floorDiv(surface.originBlockZ + stride - 1, CHUNK_SIZE);

    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        final @Nullable ChunkColumns columns = source.columnsFor(chunkX, chunkZ);
        if (columns == null) {
          continue;
        }
        surface.fillFromChunk(columns, chunkX, chunkZ);
      }
    }
    return surface;
  }

  int index(final int x, final int z) {
    return (z + HALO) * this.stride + (x + HALO);
  }

  @Nullable
  Material material(final int index) {
    return this.material[index];
  }

  @Nullable
  Material cap(final int index) {
    return this.cap[index];
  }

  @Nullable
  Biome biome(final int index) {
    return this.biome[index];
  }

  int surfaceY(final int index) {
    return this.surfaceY[index];
  }

  int floorY(final int index) {
    return this.floorY[index];
  }

  int @NotNull [] surfaceHeights() {
    return this.surfaceY;
  }

  int @NotNull [] floorHeights() {
    return this.floorY;
  }

  int tallerNeighbors(final int @NotNull [] heights, final int x, final int z) {
    final int current = heights[index(x, z)];
    int taller = 0;
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if ((offsetX != 0 || offsetZ != 0)
            && hasData(x + offsetX, z + offsetZ)
            && heights[index(x + offsetX, z + offsetZ)] > current) {
          taller++;
        }
      }
    }
    return taller;
  }

  boolean hasData(final int x, final int z) {
    final int gridX = x + HALO;
    final int gridZ = z + HALO;
    if (gridX < 0 || gridX >= this.stride || gridZ < 0 || gridZ >= this.stride) {
      return false;
    }
    return this.material[gridZ * this.stride + gridX] != null;
  }

  private void fillFromChunk(
      final @NotNull ChunkColumns columns, final int chunkX, final int chunkZ) {
    final int chunkBlockX = chunkX * CHUNK_SIZE;
    final int chunkBlockZ = chunkZ * CHUNK_SIZE;
    final int firstX = Math.max(this.originBlockX, chunkBlockX);
    final int lastX = Math.min(this.originBlockX + this.stride - 1, chunkBlockX + CHUNK_SIZE - 1);
    final int firstZ = Math.max(this.originBlockZ, chunkBlockZ);
    final int lastZ = Math.min(this.originBlockZ + this.stride - 1, chunkBlockZ + CHUNK_SIZE - 1);

    for (int blockX = firstX; blockX <= lastX; blockX++) {
      for (int blockZ = firstZ; blockZ <= lastZ; blockZ++) {
        final int columnIndex =
            ChunkColumns.columnIndex(blockX - chunkBlockX, blockZ - chunkBlockZ);
        final int firstRun = columns.runStart(columnIndex);
        if (firstRun == columns.runEnd(columnIndex)) {
          continue;
        }

        final int gridIndex =
            (blockZ - this.originBlockZ) * this.stride + (blockX - this.originBlockX);
        this.surfaceY[gridIndex] = columns.runTopY(firstRun);
        this.material[gridIndex] = columns.runMaterial(firstRun);
        this.cap[gridIndex] = columns.capMaterial(columnIndex);
        this.biome[gridIndex] = columns.columnBiome(columnIndex);
        this.floorY[gridIndex] = columns.bedY(columnIndex);
      }
    }
  }
}

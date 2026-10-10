package net.aerulion.taerra.rendering.topdown;

import java.util.Arrays;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.style.HeightField;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.style.SunShadow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SurfaceGrid implements HeightField {

  private static final int CHUNK_SIZE = 16;
  private static final int CEILING_CELL = 16;

  private final int halo;
  private final int stride;
  private final int originBlockX;
  private final int originBlockZ;
  private final int @NotNull [] surfaceY;
  private final int @NotNull [] floorY;
  private final int @NotNull [] blockId;
  private final int @NotNull [] capBlockId;
  private int @NotNull [] ceilings = new int[0];
  private int ceilingsPerRow;

  private SurfaceGrid(
      final int halo, final int stride, final int originBlockX, final int originBlockZ) {
    super();
    this.halo = halo;
    this.stride = stride;
    this.originBlockX = originBlockX;
    this.originBlockZ = originBlockZ;
    this.surfaceY = new int[stride * stride];
    this.floorY = new int[stride * stride];
    this.blockId = new int[stride * stride];
    this.capBlockId = new int[stride * stride];
    Arrays.fill(this.blockId, BlockTypes.NONE);
  }

  static @NotNull SurfaceGrid sample(
      final @NotNull ChunkColumnsSource source,
      final int startBlockX,
      final int startBlockZ,
      final int tileSize,
      final int halo) {
    final int stride = tileSize + halo * 2;
    final @NotNull SurfaceGrid surface =
        new SurfaceGrid(halo, stride, startBlockX - halo, startBlockZ - halo);

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
    surface.findCeilings(tileSize);
    return surface;
  }

  private void findCeilings(final int tileSize) {
    this.ceilingsPerRow = Math.ceilDiv(tileSize, CEILING_CELL);
    this.ceilings = new int[this.ceilingsPerRow * this.ceilingsPerRow];
    Arrays.fill(this.ceilings, Integer.MIN_VALUE);
    final int plusX = MapStyle.SUN_STEP_X > 0 ? this.halo : 0;
    final int minusX = MapStyle.SUN_STEP_X < 0 ? this.halo : 0;
    final int plusZ = MapStyle.SUN_STEP_Z > 0 ? this.halo : 0;
    final int minusZ = MapStyle.SUN_STEP_Z < 0 ? this.halo : 0;
    for (int cell = 0; cell < this.ceilings.length; cell++) {
      final int firstX = (cell % this.ceilingsPerRow) * CEILING_CELL;
      final int firstZ = (cell / this.ceilingsPerRow) * CEILING_CELL;
      final int lastX = Math.min(tileSize, firstX + CEILING_CELL) - 1 + plusX;
      final int lastZ = Math.min(tileSize, firstZ + CEILING_CELL) - 1 + plusZ;
      int ceiling = Integer.MIN_VALUE;
      for (int z = firstZ - minusZ; z <= lastZ; z++) {
        for (int x = firstX - minusX; x <= lastX; x++) {
          final int index = index(x, z);
          if (this.blockId[index] != BlockTypes.NONE) {
            ceiling = Math.max(ceiling, this.floorY[index]);
          }
        }
      }
      this.ceilings[cell] = ceiling;
    }
  }

  int index(final int x, final int z) {
    return (z + this.halo) * this.stride + (x + this.halo);
  }

  @Override
  public int topYAt(final int x, final int z) {
    final int index = index(x, z);
    return this.blockId[index] == BlockTypes.NONE ? SunShadow.NO_COLUMN : this.floorY[index];
  }

  @Override
  public int ceilingYNear(final int x, final int z) {
    return this.ceilings[(z / CEILING_CELL) * this.ceilingsPerRow + x / CEILING_CELL];
  }

  int blockId(final int index) {
    return this.blockId[index];
  }

  int capBlockId(final int index) {
    return this.capBlockId[index];
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
    final int gridX = x + this.halo;
    final int gridZ = z + this.halo;
    if (gridX < 0 || gridX >= this.stride || gridZ < 0 || gridZ >= this.stride) {
      return false;
    }
    return this.blockId[gridZ * this.stride + gridX] != BlockTypes.NONE;
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
        this.blockId[gridIndex] = columns.runBlockId(firstRun);
        this.capBlockId[gridIndex] = columns.capBlockId(columnIndex);
        this.floorY[gridIndex] = columns.bedY(columnIndex);
      }
    }
  }
}

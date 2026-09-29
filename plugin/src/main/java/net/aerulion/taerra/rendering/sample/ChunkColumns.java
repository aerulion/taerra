package net.aerulion.taerra.rendering.sample;

import net.aerulion.taerra.rendering.block.Fluids;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkColumns {

  public static final int COLUMN_COUNT = 256;

  public static final int EMPTY_COLUMN = Integer.MIN_VALUE;
  public static final long UNKNOWN_HEIGHTS = Long.MIN_VALUE;
  public static final long MISSING_HEIGHTS = Long.MAX_VALUE;
  private final int chunkX;
  private final int chunkZ;
  private final int @NotNull [] runOffsets;
  private final short @NotNull [] runTopY;
  private final short @NotNull [] runBottomY;
  private final Material @NotNull [] runMaterial;
  private final Material @NotNull [] capMaterial;
  private final Biome @NotNull [] columnBiome;
  private final int minTopY;
  private final int maxTopY;

  ChunkColumns(
      final int chunkX,
      final int chunkZ,
      final int @NotNull [] runOffsets,
      final short @NotNull [] runTopY,
      final short @NotNull [] runBottomY,
      final Material @NotNull [] runMaterial,
      final Material @NotNull [] capMaterial,
      final Biome @NotNull [] columnBiome,
      final int minTopY,
      final int maxTopY) {
    super();
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
    this.runOffsets = runOffsets;
    this.runTopY = runTopY;
    this.runBottomY = runBottomY;
    this.runMaterial = runMaterial;
    this.capMaterial = capMaterial;
    this.columnBiome = columnBiome;
    this.minTopY = minTopY;
    this.maxTopY = maxTopY;
  }

  public static int columnIndex(final int localX, final int localZ) {
    return (localX << 4) | localZ;
  }

  public static @NotNull ChunkColumnsBuilder builder(final int chunkX, final int chunkZ) {
    return new ChunkColumnsBuilder(chunkX, chunkZ);
  }

  public static int minTopOf(final long heights) {
    return (int) (heights >> 32);
  }

  public static int maxTopOf(final long heights) {
    return (int) heights;
  }

  public int chunkX() {
    return this.chunkX;
  }

  public int chunkZ() {
    return this.chunkZ;
  }

  public int runStart(final int columnIndex) {
    return this.runOffsets[columnIndex];
  }

  public int runEnd(final int columnIndex) {
    return this.runOffsets[columnIndex + 1];
  }

  public int runTopY(final int runIndex) {
    return this.runTopY[runIndex];
  }

  public int runBottomY(final int runIndex) {
    return this.runBottomY[runIndex];
  }

  public @NotNull Material runMaterial(final int runIndex) {
    return this.runMaterial[runIndex];
  }

  public @Nullable Material capMaterial(final int columnIndex) {
    return this.capMaterial[columnIndex];
  }

  public @Nullable Biome columnBiome(final int columnIndex) {
    return this.columnBiome[columnIndex];
  }

  public int topY(final int columnIndex) {
    final int start = runStart(columnIndex);
    return start == runEnd(columnIndex) ? EMPTY_COLUMN : this.runTopY[start];
  }

  public int runIndexAt(final int columnIndex, final int y) {
    final int end = runEnd(columnIndex);
    for (int runIndex = runStart(columnIndex); runIndex < end; runIndex++) {

      if (y > this.runTopY[runIndex]) {
        return -1;
      }
      if (y >= this.runBottomY[runIndex]) {
        return runIndex;
      }
    }
    return -1;
  }

  public int bedY(final int columnIndex) {
    final int firstRun = runStart(columnIndex);
    final int lastRun = runEnd(columnIndex);
    int runIndex = firstRun;
    while (runIndex < lastRun && Fluids.isFluid(this.runMaterial[runIndex])) {
      runIndex++;
    }
    if (runIndex == firstRun) {
      return firstRun == lastRun ? EMPTY_COLUMN : this.runTopY[firstRun];
    }
    return runIndex < lastRun ? this.runTopY[runIndex] : this.runBottomY[runIndex - 1];
  }

  public boolean isSolidAt(final int columnIndex, final int y) {
    return runIndexAt(columnIndex, y) >= 0;
  }

  public int minTopY() {
    return this.minTopY;
  }

  public int maxTopY() {
    return this.maxTopY;
  }

  public long heights() {
    return ((long) this.minTopY << 32) | (this.maxTopY & 0xFFFFFFFFL);
  }

  public boolean isEmpty() {
    return this.maxTopY == EMPTY_COLUMN;
  }
}

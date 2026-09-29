package net.aerulion.taerra.rendering.sample;

import java.util.Arrays;
import net.aerulion.taerra.rendering.block.Fluids;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkColumnsBuilder {

  private static final int INITIAL_RUN_CAPACITY = ChunkColumns.COLUMN_COUNT * 2;

  private final int chunkX;
  private final int chunkZ;
  private final int @NotNull [] runOffsets = new int[ChunkColumns.COLUMN_COUNT + 1];
  private final Material @NotNull [] capMaterial = new Material[ChunkColumns.COLUMN_COUNT];
  private final Biome @NotNull [] columnBiome = new Biome[ChunkColumns.COLUMN_COUNT];
  private short @NotNull [] runTopY = new short[INITIAL_RUN_CAPACITY];
  private short @NotNull [] runBottomY = new short[INITIAL_RUN_CAPACITY];
  private Material @NotNull [] runMaterial = new Material[INITIAL_RUN_CAPACITY];
  private int runCount;
  private int completedColumns;
  private int minTopY = ChunkColumns.EMPTY_COLUMN;
  private int maxTopY = ChunkColumns.EMPTY_COLUMN;

  ChunkColumnsBuilder(final int chunkX, final int chunkZ) {
    super();
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
  }

  public @NotNull ChunkColumnsBuilder addRun(
      final int topY, final int bottomY, final @NotNull Material material) {
    if (this.runCount == this.runTopY.length) {
      final int grown = this.runCount * 2;
      this.runTopY = Arrays.copyOf(this.runTopY, grown);
      this.runBottomY = Arrays.copyOf(this.runBottomY, grown);
      this.runMaterial = Arrays.copyOf(this.runMaterial, grown);
    }
    this.runTopY[this.runCount] = (short) topY;
    this.runBottomY[this.runCount] = (short) bottomY;
    this.runMaterial[this.runCount] = material;
    this.runCount++;
    return this;
  }

  public @NotNull ChunkColumnsBuilder completeColumn(
      final @Nullable Biome biome, final @Nullable Material capMaterial) {
    final int columnIndex = this.completedColumns;
    this.columnBiome[columnIndex] = biome;
    this.capMaterial[columnIndex] = capMaterial;
    if (this.runOffsets[columnIndex] < this.runCount) {
      final int topY = this.runTopY[this.runOffsets[columnIndex]];
      this.minTopY =
          this.minTopY == ChunkColumns.EMPTY_COLUMN ? topY : Math.min(this.minTopY, topY);
      this.maxTopY =
          this.maxTopY == ChunkColumns.EMPTY_COLUMN ? topY : Math.max(this.maxTopY, topY);
    }
    this.completedColumns++;
    this.runOffsets[this.completedColumns] = this.runCount;
    return this;
  }

  public @NotNull ChunkColumns build() {
    if (this.completedColumns != ChunkColumns.COLUMN_COUNT) {
      throw new IllegalStateException(
          "Expected "
              + ChunkColumns.COLUMN_COUNT
              + " completed columns but got "
              + this.completedColumns);
    }

    final int @NotNull [] offsets = new int[ChunkColumns.COLUMN_COUNT + 1];
    final short @NotNull [] topY = new short[this.runCount];
    final short @NotNull [] bottomY = new short[this.runCount];
    final Material @NotNull [] material = new Material[this.runCount];

    final int @NotNull [] cursors = new int[4];
    int count = 0;
    for (int column = 0; column < ChunkColumns.COLUMN_COUNT; column++) {
      offsets[column] = count;
      final int localX = column >> 4;
      final int localZ = column & 15;
      final boolean interior = localX > 0 && localX < 15 && localZ > 0 && localZ < 15;
      if (interior) {
        cursors[0] = this.runOffsets[column - 16];
        cursors[1] = this.runOffsets[column + 16];
        cursors[2] = this.runOffsets[column - 1];
        cursors[3] = this.runOffsets[column + 1];
      }
      for (int run = this.runOffsets[column]; run < this.runOffsets[column + 1]; run++) {
        if (interior
            && count > offsets[column]
            && bottomY[count - 1] == this.runTopY[run] + 1
            && !Fluids.isFluid(material[count - 1])
            && !Fluids.isFluid(this.runMaterial[run])
            && isHidden(column, cursors, this.runBottomY[run], this.runTopY[run])) {
          bottomY[count - 1] = this.runBottomY[run];
          continue;
        }
        topY[count] = this.runTopY[run];
        bottomY[count] = this.runBottomY[run];
        material[count] = this.runMaterial[run];
        count++;
      }
    }
    offsets[ChunkColumns.COLUMN_COUNT] = count;
    return new ChunkColumns(
        this.chunkX,
        this.chunkZ,
        offsets,
        Arrays.copyOf(topY, count),
        Arrays.copyOf(bottomY, count),
        Arrays.copyOf(material, count),
        this.capMaterial,
        this.columnBiome,
        this.minTopY,
        this.maxTopY);
  }

  private boolean isHidden(
      final int column, final int @NotNull [] cursors, final int bottomY, final int topY) {
    return isSolidThrough(column - 16, cursors, 0, bottomY, topY)
        && isSolidThrough(column + 16, cursors, 1, bottomY, topY)
        && isSolidThrough(column - 1, cursors, 2, bottomY, topY)
        && isSolidThrough(column + 1, cursors, 3, bottomY, topY);
  }

  private boolean isSolidThrough(
      final int column,
      final int @NotNull [] cursors,
      final int slot,
      final int bottomY,
      final int topY) {
    final int end = this.runOffsets[column + 1];
    int run = cursors[slot];
    while (run < end && this.runBottomY[run] > topY) {
      run++;
    }
    cursors[slot] = run;
    int y = topY;
    for (; run < end && y >= bottomY; run++) {
      if (this.runTopY[run] < y || Fluids.isFluid(this.runMaterial[run])) {
        return false;
      }
      y = this.runBottomY[run] - 1;
    }
    return y < bottomY;
  }
}

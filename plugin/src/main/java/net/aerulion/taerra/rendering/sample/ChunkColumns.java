package net.aerulion.taerra.rendering.sample;

import java.util.function.IntUnaryOperator;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.style.Fog;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
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
  private final int @NotNull [] runBlockId;
  private final int @NotNull [] capBlockId;
  private final Biome @NotNull [] columnBiome;
  private final int minTopY;
  private final int maxTopY;
  private final int @Nullable [] mist;

  ChunkColumns(
      final int chunkX,
      final int chunkZ,
      final int @NotNull [] runOffsets,
      final short @NotNull [] runTopY,
      final short @NotNull [] runBottomY,
      final int @NotNull [] runBlockId,
      final int @NotNull [] capBlockId,
      final Biome @NotNull [] columnBiome,
      final int minTopY,
      final int maxTopY,
      final int @Nullable [] mist) {
    super();
    this.chunkX = chunkX;
    this.chunkZ = chunkZ;
    this.runOffsets = runOffsets;
    this.runTopY = runTopY;
    this.runBottomY = runBottomY;
    this.runBlockId = runBlockId;
    this.capBlockId = capBlockId;
    this.columnBiome = columnBiome;
    this.minTopY = minTopY;
    this.maxTopY = maxTopY;
    this.mist = mist;
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

  public int runBlockId(final int runIndex) {
    return this.runBlockId[runIndex];
  }

  public @NotNull BlockType runBlockType(final int runIndex) {
    return BlockTypes.byId(this.runBlockId[runIndex]);
  }

  public int capBlockId(final int columnIndex) {
    return this.capBlockId[columnIndex];
  }

  public @Nullable BlockType capBlockType(final int columnIndex) {
    final int capBlockId = this.capBlockId[columnIndex];
    return capBlockId == BlockTypes.NONE ? null : BlockTypes.byId(capBlockId);
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
    while (runIndex < lastRun && Fluids.isFluid(this.runBlockId[runIndex])) {
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

  public int mist(final int columnIndex) {
    return this.mist == null ? Fog.NO_MIST : this.mist[columnIndex];
  }

  public @NotNull ChunkColumns withMist(final int @NotNull [] mist) {
    int ceilingY = this.maxTopY;
    for (final int column : mist) {
      if (column != Fog.NO_MIST) {
        ceilingY = Math.max(ceilingY, Fog.ceilingY(column));
      }
    }
    return new ChunkColumns(
        this.chunkX,
        this.chunkZ,
        this.runOffsets,
        this.runTopY,
        this.runBottomY,
        this.runBlockId,
        this.capBlockId,
        this.columnBiome,
        this.minTopY,
        ceilingY,
        mist);
  }

  public @NotNull ChunkColumns mapMist(final @NotNull IntUnaryOperator map) {
    if (this.mist == null) {
      return this;
    }
    final int @NotNull [] mapped = new int[this.mist.length];
    for (int index = 0; index < mapped.length; index++) {
      mapped[index] = map.applyAsInt(this.mist[index]);
    }
    return withMist(mapped);
  }

  public @NotNull ChunkColumns withoutMist() {
    return this.mist == null
        ? this
        : new ChunkColumns(
            this.chunkX,
            this.chunkZ,
            this.runOffsets,
            this.runTopY,
            this.runBottomY,
            this.runBlockId,
            this.capBlockId,
            this.columnBiome,
            this.minTopY,
            this.maxTopY,
            null);
  }

  public long heights() {
    return ((long) this.minTopY << 32) | (this.maxTopY & 0xFFFFFFFFL);
  }

  public boolean isEmpty() {
    return this.maxTopY == EMPTY_COLUMN;
  }
}

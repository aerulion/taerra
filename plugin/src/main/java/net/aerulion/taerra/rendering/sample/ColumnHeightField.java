package net.aerulion.taerra.rendering.sample;

import java.util.Arrays;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.style.SolidField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ColumnHeightField implements SolidField {

  private static final int SLOT_BITS = 3;
  private static final int SLOT_MASK = (1 << SLOT_BITS) - 1;

  private final @NotNull ChunkColumnsSource source;
  private final long @NotNull [] slotKeys = new long[1 << (2 * SLOT_BITS)];
  private final @Nullable ChunkColumns @NotNull [] slotColumns =
      new ChunkColumns[1 << (2 * SLOT_BITS)];
  private int ceilingY = Integer.MAX_VALUE;

  public ColumnHeightField(final @NotNull ChunkColumnsSource source) {
    super();
    this.source = source;
    Arrays.fill(this.slotKeys, Long.MIN_VALUE);
  }

  public void limitRaysTo(final int ceilingY) {
    this.ceilingY = ceilingY;
  }

  private @Nullable ChunkColumns chunk(final int chunkX, final int chunkZ) {
    final int slot = ((chunkX & SLOT_MASK) << SLOT_BITS) | (chunkZ & SLOT_MASK);
    final long key = ChunkCoordinate.key(chunkX, chunkZ);
    if (this.slotKeys[slot] != key) {
      this.slotKeys[slot] = key;
      this.slotColumns[slot] = this.source.columnsFor(chunkX, chunkZ);
    }
    return this.slotColumns[slot];
  }

  @Override
  public boolean isSolidAt(final int blockX, final int blockY, final int blockZ) {
    final @Nullable ChunkColumns columns = chunk(blockX >> 4, blockZ >> 4);
    if (columns == null) {
      return false;
    }
    final int run = columns.runIndexAt(ChunkColumns.columnIndex(blockX & 15, blockZ & 15), blockY);
    return run >= 0 && !Fluids.isFluid(columns.runMaterial(run));
  }

  @Override
  public int ceilingY() {
    return this.ceilingY;
  }

  @Override
  public int topYBetween(final int fromX, final int fromZ, final int toX, final int toZ) {
    int topY = Integer.MIN_VALUE;
    final int lastChunkX = Math.max(fromX, toX) >> 4;
    final int lastChunkZ = Math.max(fromZ, toZ) >> 4;
    for (int chunkX = Math.min(fromX, toX) >> 4; chunkX <= lastChunkX; chunkX++) {
      for (int chunkZ = Math.min(fromZ, toZ) >> 4; chunkZ <= lastChunkZ; chunkZ++) {
        final @Nullable ChunkColumns columns = chunk(chunkX, chunkZ);
        if (columns != null) {
          topY = Math.max(topY, columns.maxTopY());
        }
      }
    }
    return Math.min(topY, this.ceilingY);
  }
}

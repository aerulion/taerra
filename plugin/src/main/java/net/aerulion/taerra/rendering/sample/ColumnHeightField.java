package net.aerulion.taerra.rendering.sample;

import net.aerulion.taerra.rendering.block.Fluids;
import net.aerulion.taerra.rendering.style.HeightField;
import net.aerulion.taerra.rendering.style.SolidField;
import net.aerulion.taerra.rendering.style.SunShadow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ColumnHeightField implements HeightField, SolidField {

  private final @NotNull ChunkColumnsSource source;
  private int heldChunkX = Integer.MIN_VALUE;
  private int heldChunkZ = Integer.MIN_VALUE;
  private @Nullable ChunkColumns held;
  private int ceilingY = Integer.MAX_VALUE;

  public ColumnHeightField(final @NotNull ChunkColumnsSource source) {
    super();
    this.source = source;
  }

  public void limitRaysTo(final int ceilingY) {
    this.ceilingY = ceilingY;
  }

  private void hold(final int blockX, final int blockZ) {
    final int chunkX = blockX >> 4;
    final int chunkZ = blockZ >> 4;
    if (chunkX != this.heldChunkX || chunkZ != this.heldChunkZ) {
      this.held = this.source.columnsFor(chunkX, chunkZ);
      this.heldChunkX = chunkX;
      this.heldChunkZ = chunkZ;
    }
  }

  @Override
  public boolean isSolidAt(final int blockX, final int blockY, final int blockZ) {
    hold(blockX, blockZ);
    if (this.held == null) {
      return false;
    }
    final int run =
        this.held.runIndexAt(ChunkColumns.columnIndex(blockX & 15, blockZ & 15), blockY);
    return run >= 0 && !Fluids.isFluid(this.held.runMaterial(run));
  }

  @Override
  public int topYAt(final int blockX, final int blockZ) {
    hold(blockX, blockZ);
    if (this.held == null) {
      return SunShadow.NO_COLUMN;
    }
    final int topY = this.held.bedY(ChunkColumns.columnIndex(blockX & 15, blockZ & 15));
    return topY == ChunkColumns.EMPTY_COLUMN ? SunShadow.NO_COLUMN : topY;
  }

  @Override
  public int ceilingY() {
    return this.ceilingY;
  }
}

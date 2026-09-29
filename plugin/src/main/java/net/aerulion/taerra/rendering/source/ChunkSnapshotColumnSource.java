package net.aerulion.taerra.rendering.source;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

public final class ChunkSnapshotColumnSource implements ColumnSource {

  private final @NotNull ChunkSnapshot snapshot;
  private final int minHeight;
  private final int maxHeight;
  private final int sectionCount;

  public ChunkSnapshotColumnSource(
      final @NotNull ChunkSnapshot snapshot, final int minHeight, final int maxHeight) {
    super();
    this.snapshot = snapshot;
    this.minHeight = minHeight;
    this.maxHeight = maxHeight;
    this.sectionCount = (maxHeight - minHeight) >> 4;
  }

  @Override
  public int minHeight() {
    return this.minHeight;
  }

  @Override
  public int maxHeight() {
    return this.maxHeight;
  }

  @Override
  public int highestNonAirY(final int localX, final int localZ) {
    for (int y = this.maxHeight - 1; y >= this.minHeight; y--) {
      if (isSectionAllAir(y)) {
        y &= ~15;
        continue;
      }
      if (!this.snapshot.getBlockType(localX, y, localZ).isAir()) {
        return y;
      }
    }
    return EMPTY_COLUMN;
  }

  @Override
  public boolean isSectionAllAir(final int y) {
    final int sectionIndex = (y >> 4) - (this.minHeight >> 4);
    return sectionIndex >= 0
        && sectionIndex < this.sectionCount
        && this.snapshot.isSectionEmpty(sectionIndex);
  }

  @Override
  public @NotNull Material materialAt(final int localX, final int y, final int localZ) {
    return this.snapshot.getBlockType(localX, y, localZ);
  }

  @Override
  public @NotNull Biome biomeAt(final int localX, final int y, final int localZ) {
    return this.snapshot.getBiome(
        localX, Math.max(this.minHeight, Math.min(y, this.maxHeight - 1)), localZ);
  }
}

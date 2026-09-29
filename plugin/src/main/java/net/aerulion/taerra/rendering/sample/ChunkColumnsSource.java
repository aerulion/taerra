package net.aerulion.taerra.rendering.sample;

import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface ChunkColumnsSource {

  @Nullable
  ChunkColumns columnsFor(int chunkX, int chunkZ);

  default long knownHeights(final int chunkX, final int chunkZ) {
    return ChunkColumns.UNKNOWN_HEIGHTS;
  }
}

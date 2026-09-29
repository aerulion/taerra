package net.aerulion.taerra.rendering.source;

import org.jetbrains.annotations.Nullable;

public sealed interface ChunkSourceProvider
    permits RegionFileChunkSourceProvider, LiveChunkSourceProvider {

  @Nullable
  ColumnSource sourceForChunk(int chunkX, int chunkZ);
}

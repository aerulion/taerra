package net.aerulion.taerra.rendering.source;

import java.util.Map;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiveChunkSourceProvider implements ChunkSourceProvider {

  private final @NotNull Map<Long, LiveChunk> snapshots;
  private final int minHeight;
  private final int maxHeight;
  private final @NotNull ChunkSourceProvider fallback;

  public LiveChunkSourceProvider(
      final @NotNull Map<Long, LiveChunk> snapshots,
      final int minHeight,
      final int maxHeight,
      final @NotNull ChunkSourceProvider fallback) {
    super();
    this.snapshots = snapshots;
    this.minHeight = minHeight;
    this.maxHeight = maxHeight;
    this.fallback = fallback;
  }

  @Override
  public @Nullable ColumnSource sourceForChunk(final int chunkX, final int chunkZ) {
    final @Nullable LiveChunk snapshot = this.snapshots.get(ChunkCoordinate.key(chunkX, chunkZ));
    if (snapshot == null) {
      return this.fallback.sourceForChunk(chunkX, chunkZ);
    }
    return ParsedAnvilChunk.fromLive(snapshot, this.minHeight, this.maxHeight);
  }
}

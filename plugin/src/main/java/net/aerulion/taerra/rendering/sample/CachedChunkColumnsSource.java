package net.aerulion.taerra.rendering.sample;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Optional;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.source.ChunkSourceProvider;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CachedChunkColumnsSource implements ChunkColumnsSource {

  private final @NotNull ChunkColumnsCacheImpl cache;
  private final @NotNull World world;
  private final @NotNull ChunkSourceProvider provider;
  private final @NotNull FogView view;

  private final @NotNull Long2ObjectOpenHashMap<Optional<ChunkColumns>> resolved =
      new Long2ObjectOpenHashMap<>();
  private long sampleNanos;
  private int sampledChunks;
  private int reusedChunks;

  CachedChunkColumnsSource(
      final @NotNull ChunkColumnsCacheImpl cache,
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final @NotNull FogView view) {
    super();
    this.cache = cache;
    this.world = world;
    this.provider = provider;
    this.view = view;
  }

  @Override
  public @Nullable ChunkColumns columnsFor(final int chunkX, final int chunkZ) {
    return this.resolved
        .computeIfAbsent(
            ChunkCoordinate.key(chunkX, chunkZ),
            ignored -> Optional.ofNullable(resolve(chunkX, chunkZ)))
        .orElse(null);
  }

  @Override
  public long knownHeights(final int chunkX, final int chunkZ) {
    final @Nullable Optional<ChunkColumns> resolved =
        this.resolved.get(ChunkCoordinate.key(chunkX, chunkZ));
    if (resolved != null) {
      return resolved.map(ChunkColumns::heights).orElse(ChunkColumns.MISSING_HEIGHTS);
    }
    return this.cache.knownHeights(WorldKeys.id(this.world), chunkX, chunkZ);
  }

  public @NotNull FogView view() {
    return this.view;
  }

  private @Nullable ChunkColumns resolve(final int chunkX, final int chunkZ) {
    final int variant = this.cache.variant(WorldKeys.id(this.world), chunkX, chunkZ, this.view);
    final @Nullable ChunkColumns cached =
        this.cache.getIfPresent(WorldKeys.id(this.world), chunkX, chunkZ, variant);
    if (cached != null) {
      this.reusedChunks++;
      return cached;
    }
    final long startedAt = System.nanoTime();
    final @Nullable ChunkColumns columns =
        this.cache.get(this.world, this.provider, chunkX, chunkZ, variant);
    this.sampleNanos += System.nanoTime() - startedAt;
    if (columns != null) {
      this.sampledChunks++;
    }
    return columns;
  }

  public long sampleNanos() {
    return this.sampleNanos;
  }

  public int sampledChunks() {
    return this.sampledChunks;
  }

  public int reusedChunks() {
    return this.reusedChunks;
  }
}

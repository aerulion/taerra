package net.aerulion.taerra.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileCacheImpl implements TileCache {

  private static final int EXPIRY_SECONDS = 300;
  private final @NotNull TileStorage storage;
  private final @NotNull Cache<TileCoordinate, BufferedImage> cache;

  public TileCacheImpl(final @NotNull TileStorage storage, final int maxSize) {
    super();
    this.storage = storage;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .recordStats()
            .expireAfterAccess(EXPIRY_SECONDS, TimeUnit.SECONDS)
            .build();
  }

  @Override
  public @NotNull Optional<BufferedImage> get(final @NotNull TileCoordinate coord) {
    final @Nullable BufferedImage cached = this.cache.getIfPresent(coord);
    if (cached != null) {
      return Optional.of(cached);
    }

    final @NotNull Optional<BufferedImage> loaded = this.storage.loadTile(coord);
    loaded.ifPresent(image -> this.cache.put(coord, image));

    return loaded;
  }

  @Override
  public void put(final @NotNull TileCoordinate coord, final @NotNull BufferedImage image) {
    this.cache.put(coord, image);
  }

  @Override
  public void invalidate(final @NotNull TileCoordinate coord) {
    this.cache.invalidate(coord);
  }

  @Override
  public void clear() {
    this.cache.invalidateAll();
  }

  @Override
  public @NotNull TileCacheStats stats() {
    final @NotNull CacheStats stats = this.cache.stats();
    return new TileCacheStats(
        this.cache.estimatedSize(), stats.hitCount(), stats.missCount(), stats.evictionCount());
  }
}

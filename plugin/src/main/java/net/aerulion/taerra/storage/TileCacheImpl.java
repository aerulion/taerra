package net.aerulion.taerra.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileCacheImpl implements TileCache {

  private static final int EXPIRY_SECONDS = 300;
  private final @NotNull TileStorage storage;
  private final @NotNull Cache<TileCoordinate, Map<FogView, BufferedImage>> cache;

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
    return get(coord, FogView.BASE);
  }

  @Override
  public @NotNull Optional<BufferedImage> get(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    final @NotNull Map<FogView, BufferedImage> layers =
        this.cache.get(coord, _ -> new ConcurrentHashMap<>());
    final @Nullable BufferedImage cached = layers.get(layer);
    if (cached != null) {
      return Optional.of(cached);
    }
    final @NotNull Optional<BufferedImage> loaded = this.storage.loadTile(coord, layer);
    loaded.ifPresent(image -> layers.put(layer, image));
    return loaded;
  }

  @Override
  public void put(final @NotNull TileCoordinate coord, final @NotNull BufferedImage image) {
    put(coord, FogView.BASE, image);
  }

  @Override
  public void put(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull BufferedImage image) {
    this.cache.get(coord, _ -> new ConcurrentHashMap<>()).put(layer, image);
  }

  @Override
  public void invalidate(final @NotNull TileCoordinate coord) {
    this.cache.invalidate(coord);
  }

  @Override
  public void invalidate(final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    final @Nullable Map<FogView, BufferedImage> layers = this.cache.getIfPresent(coord);
    if (layers != null) {
      layers.remove(layer);
    }
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

  @Override
  public void resize(final int maxSize) {
    this.cache.policy().eviction().ifPresent(eviction -> eviction.setMaximum(maxSize));
  }
}

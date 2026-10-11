package net.aerulion.taerra.rendering.sample;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.source.ChunkSourceProvider;
import net.aerulion.taerra.rendering.source.ColumnSource;
import net.aerulion.taerra.rendering.source.LiveChunk;
import net.aerulion.taerra.rendering.source.LiveChunkSourceProvider;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkColumnsCacheImpl implements ChunkColumnsCache {

  private static final long SNAPSHOT_BUDGET_NANOS = 2_000_000L;
  private static final int CHECKS_PER_TICK = 4096;

  private final @NotNull Plugin plugin;
  private final @NotNull Cache<ChunkKey, ChunkColumns> cache;

  private final @NotNull Set<ChunkKey> changed = ConcurrentHashMap.newKeySet();
  private final @NotNull Map<String, Object> refreshLocks = new ConcurrentHashMap<>();
  private final @NotNull AtomicLong epoch = new AtomicLong();

  private final @NotNull Map<String, Long2LongOpenHashMap> heights = new ConcurrentHashMap<>();
  private final @NotNull BlockShapeResolver shapes;
  private final @NotNull WorldLimits limits;

  public ChunkColumnsCacheImpl(
      final @NotNull Plugin plugin,
      final int maximumChunks,
      final @NotNull BlockShapeResolver shapes,
      final @NotNull WorldLimits limits) {
    super();
    this.plugin = plugin;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(Math.max(256, maximumChunks))
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    this.shapes = shapes;
    this.limits = limits;
  }

  private static int takeSnapshots(
      final @NotNull World world,
      final @NotNull List<ChunkCoordinate> chunks,
      final int from,
      final @NotNull Map<Long, LiveChunk> into) {
    final long startedAt = System.nanoTime();
    int index = from;
    while (index < chunks.size()
        && index - from < CHECKS_PER_TICK
        && (into.isEmpty() || System.nanoTime() - startedAt < SNAPSHOT_BUDGET_NANOS)) {
      final @NotNull ChunkCoordinate chunk = chunks.get(index);
      if (world.isChunkLoaded(chunk.x(), chunk.z())) {
        into.put(chunk.toKey(), LiveChunk.capture(world, chunk.x(), chunk.z()));
      }
      index++;
    }
    return index;
  }

  @Nullable
  ChunkColumns get(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int chunkX,
      final int chunkZ) {
    final @NotNull ChunkKey key = new ChunkKey(WorldKeys.id(world), chunkX, chunkZ);
    final long epoch = this.epoch.get();
    final @Nullable ChunkColumns cached = this.cache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    final boolean current = !this.changed.contains(key);
    final @Nullable WorldLimit limit = this.limits.get(WorldKeys.id(world));
    final @Nullable ColumnSource source =
        limit == null
                || limit.intersects(
                    chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15)
            ? provider.sourceForChunk(chunkX, chunkZ)
            : null;
    if (source == null) {
      if (current && epoch == this.epoch.get()) {
        recordHeights(WorldKeys.id(world), chunkX, chunkZ, ChunkColumns.MISSING_HEIGHTS);
      }
      return null;
    }
    final @NotNull ChunkColumns columns = sample(world, source, chunkX, chunkZ);
    if (current) {
      this.cache
          .asMap()
          .compute(
              key,
              (ignored, existing) -> {
                if (existing != null || epoch != this.epoch.get()) {
                  return existing;
                }
                recordHeights(WorldKeys.id(world), chunkX, chunkZ, columns.heights());
                return columns;
              });
    }
    return columns;
  }

  @Nullable
  ChunkColumns getIfPresent(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    return this.cache.getIfPresent(new ChunkKey(worldName, chunkX, chunkZ));
  }

  @Override
  public long knownHeights(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable Long2LongOpenHashMap world = this.heights.get(worldName);
    if (world == null) {
      return ChunkColumns.UNKNOWN_HEIGHTS;
    }

    synchronized (world) {
      return world.get(ChunkCoordinate.key(chunkX, chunkZ));
    }
  }

  @Override
  public int refreshChanged(final @NotNull World world) throws Exception {
    synchronized (this.refreshLocks.computeIfAbsent(WorldKeys.id(world), ignored -> new Object())) {
      final @NotNull List<ChunkCoordinate> changed = new ArrayList<>();
      for (final @NotNull ChunkKey key : this.changed) {
        if (key.worldName().equals(WorldKeys.id(world)) && this.changed.remove(key)) {
          changed.add(new ChunkCoordinate(key.chunkX(), key.chunkZ()));
        }
      }

      int refreshed = 0;
      int next = 0;
      try {
        while (next < changed.size()) {
          final int from = next;
          final @NotNull Map<Long, LiveChunk> snapshots = new HashMap<>();
          next =
              Bukkit.getScheduler()
                  .callSyncMethod(this.plugin, () -> takeSnapshots(world, changed, from, snapshots))
                  .get();
          final @NotNull LiveChunkSourceProvider live =
              new LiveChunkSourceProvider(snapshots, world.getMinHeight(), world.getMaxHeight());
          for (final @NotNull ChunkCoordinate chunk : changed.subList(from, next)) {
            if (snapshots.containsKey(chunk.toKey())) {
              sampleAndStore(world, live, chunk.x(), chunk.z());
            } else {
              this.epoch.incrementAndGet();
              this.cache.invalidate(new ChunkKey(WorldKeys.id(world), chunk.x(), chunk.z()));
            }
          }
          refreshed += snapshots.size();
        }
      } finally {
        for (final @NotNull ChunkCoordinate chunk : changed.subList(next, changed.size())) {
          markChanged(WorldKeys.id(world), chunk.x(), chunk.z());
        }
      }
      return refreshed;
    }
  }

  @Override
  public @NotNull CachedChunkColumnsSource sourceFor(
      final @NotNull World world, final @NotNull ChunkSourceProvider provider) {
    return new CachedChunkColumnsSource(this, world, provider);
  }

  @Override
  public void markChanged(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    this.changed.add(new ChunkKey(worldName, chunkX, chunkZ));
  }

  @Override
  public void forget(final @NotNull String worldName, final @NotNull Collection<Long> chunks) {
    this.epoch.incrementAndGet();
    this.cache.invalidateAll(
        chunks.stream()
            .map(
                chunk ->
                    new ChunkKey(
                        worldName, ChunkCoordinate.keyX(chunk), ChunkCoordinate.keyZ(chunk)))
            .toList());
  }

  @Override
  public void forgetWorld(final @NotNull String worldName) {
    this.epoch.incrementAndGet();
    this.cache.asMap().keySet().removeIf(key -> key.worldName().equals(worldName));
    this.heights.remove(worldName);
  }

  @Override
  public long size() {
    return this.cache.estimatedSize();
  }

  private @NotNull ChunkColumns sample(
      final @NotNull World world,
      final @NotNull ColumnSource source,
      final int chunkX,
      final int chunkZ) {
    final @NotNull String worldName = WorldKeys.id(world);
    return this.limits.hidden(worldName, chunkX, chunkZ)
        ? ColumnRunSampler.blockedChunk(source, chunkX, chunkZ)
        : ColumnRunSampler.sampleChunk(
            source,
            ColumnRunSampler.ceilingY(world),
            this.shapes,
            chunkX,
            chunkZ,
            this.limits.get(worldName));
  }

  private void recordHeights(
      final @NotNull String worldName, final int chunkX, final int chunkZ, final long packed) {
    final @NotNull Long2LongOpenHashMap world =
        this.heights.computeIfAbsent(
            worldName,
            ignored -> {
              final @NotNull Long2LongOpenHashMap map = new Long2LongOpenHashMap();
              map.defaultReturnValue(ChunkColumns.UNKNOWN_HEIGHTS);
              return map;
            });
    synchronized (world) {
      world.put(ChunkCoordinate.key(chunkX, chunkZ), packed);
    }
  }

  private void sampleAndStore(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int chunkX,
      final int chunkZ) {
    final @Nullable ColumnSource source = provider.sourceForChunk(chunkX, chunkZ);
    if (source == null) {
      return;
    }
    final @NotNull ChunkColumns columns = sample(world, source, chunkX, chunkZ);
    recordHeights(WorldKeys.id(world), chunkX, chunkZ, columns.heights());
    this.cache.put(new ChunkKey(WorldKeys.id(world), chunkX, chunkZ), columns);
  }
}

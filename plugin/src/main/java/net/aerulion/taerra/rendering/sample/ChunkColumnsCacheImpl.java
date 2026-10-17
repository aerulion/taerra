package net.aerulion.taerra.rendering.sample;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
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
import net.aerulion.taerra.rendering.style.Fog;
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
  private final @NotNull Map<String, Long2IntOpenHashMap> grounds = new ConcurrentHashMap<>();
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
    final @NotNull ChunkColumns columns = sample(world, provider, source, chunkX, chunkZ);
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
    final @Nullable Long2IntOpenHashMap grounds = this.grounds.get(worldName);
    if (grounds != null) {
      synchronized (grounds) {
        chunks.forEach(chunk -> grounds.remove((long) chunk));
      }
    }
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
    this.grounds.remove(worldName);
  }

  @Override
  public long size() {
    return this.cache.estimatedSize();
  }

  private @NotNull ChunkColumns sample(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final @NotNull ColumnSource source,
      final int chunkX,
      final int chunkZ) {
    final @NotNull String worldName = WorldKeys.id(world);
    final int ceilingY = ColumnRunSampler.ceilingY(world);
    final byte @NotNull [] hiddenAround = new byte[Fog.AROUND * Fog.AROUND];
    boolean misted = false;
    for (int offsetX = -Fog.REACH_CHUNKS; offsetX <= Fog.REACH_CHUNKS; offsetX++) {
      for (int offsetZ = -Fog.REACH_CHUNKS; offsetZ <= Fog.REACH_CHUNKS; offsetZ++) {
        final byte hidden =
            this.limits.teased(worldName, chunkX + offsetX, chunkZ + offsetZ)
                ? Fog.TEASED
                : this.limits.hidden(worldName, chunkX + offsetX, chunkZ + offsetZ)
                    ? Fog.HIDDEN
                    : Fog.OPEN;
        hiddenAround[Fog.around(offsetX, offsetZ)] = hidden;
        misted |= hidden != Fog.OPEN;
      }
    }
    if (!misted) {
      return ColumnRunSampler.sampleChunk(
          source, ceilingY, this.shapes, chunkX, chunkZ, this.limits.get(worldName));
    }
    final int @NotNull [] groundAround = new int[9];
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        final int ground = ground(world, provider, ceilingY, chunkX + offsetX, chunkZ + offsetZ);
        groundAround[(offsetX + 1) * 3 + offsetZ + 1] =
            ground == ColumnSource.EMPTY_COLUMN ? world.getSeaLevel() : ground;
      }
    }
    if (hiddenAround[Fog.around(0, 0)] == Fog.HIDDEN) {
      return ColumnRunSampler.blockedChunk(source, groundAround, hiddenAround, chunkX, chunkZ);
    }
    final @NotNull ChunkColumns columns =
        ColumnRunSampler.sampleChunk(
            source, ceilingY, this.shapes, chunkX, chunkZ, this.limits.get(worldName));
    return columns.withMist(
        hiddenAround[Fog.around(0, 0)] == Fog.TEASED
            ? Fog.hidden(chunkX, chunkZ, groundAround, hiddenAround, true)
            : Fog.mist(chunkX, chunkZ, columns, groundAround, hiddenAround));
  }

  private int ground(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int ceilingY,
      final int chunkX,
      final int chunkZ) {
    if (!opaque(world, chunkX, chunkZ)) {
      return visibleGround(world, provider, ceilingY, chunkX, chunkZ);
    }
    int sum = 0;
    int count = 0;
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (opaque(world, chunkX + offsetX, chunkZ + offsetZ)) {
          continue;
        }
        final int ground =
            visibleGround(world, provider, ceilingY, chunkX + offsetX, chunkZ + offsetZ);
        if (ground != ColumnSource.EMPTY_COLUMN) {
          sum += ground;
          count++;
        }
      }
    }
    return count == 0 ? ColumnSource.EMPTY_COLUMN : Math.round((float) sum / count);
  }

  private boolean opaque(final @NotNull World world, final int chunkX, final int chunkZ) {
    return this.limits.hidden(WorldKeys.id(world), chunkX, chunkZ)
        && !this.limits.teased(WorldKeys.id(world), chunkX, chunkZ);
  }

  private int visibleGround(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int ceilingY,
      final int chunkX,
      final int chunkZ) {
    final @NotNull Long2IntOpenHashMap known =
        this.grounds.computeIfAbsent(WorldKeys.id(world), ignored -> new Long2IntOpenHashMap());
    final long key = ChunkCoordinate.key(chunkX, chunkZ);
    synchronized (known) {
      if (known.containsKey(key)) {
        return known.get(key);
      }
    }
    final @Nullable ColumnSource source = provider.sourceForChunk(chunkX, chunkZ);
    if (source == null) {
      return ColumnSource.EMPTY_COLUMN;
    }
    final int ground = Fog.ground(ColumnRunSampler.columnTops(source, ceilingY, this.shapes));
    synchronized (known) {
      known.put(key, ground);
    }
    return ground;
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
    final @NotNull ChunkColumns columns = sample(world, provider, source, chunkX, chunkZ);
    recordHeights(WorldKeys.id(world), chunkX, chunkZ, columns.heights());
    this.cache.put(new ChunkKey(WorldKeys.id(world), chunkX, chunkZ), columns);
  }

  @Override
  public void resize(final int maximumChunks) {
    this.cache
        .policy()
        .eviction()
        .ifPresent(eviction -> eviction.setMaximum(Math.max(256, maximumChunks)));
  }
}

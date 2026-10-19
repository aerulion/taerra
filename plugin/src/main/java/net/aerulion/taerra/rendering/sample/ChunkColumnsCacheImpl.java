package net.aerulion.taerra.rendering.sample;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionFog;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.source.ChunkSourceProvider;
import net.aerulion.taerra.rendering.source.ColumnSource;
import net.aerulion.taerra.rendering.source.LiveChunk;
import net.aerulion.taerra.rendering.source.LiveChunkSourceProvider;
import net.aerulion.taerra.rendering.source.RegionFileChunkSourceProvider;
import net.aerulion.taerra.rendering.style.Fog;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkColumnsCacheImpl implements ChunkColumnsCache {

  private static final long SNAPSHOT_BUDGET_NANOS = 2_000_000L;
  private static final int CHECKS_PER_TICK = 4096;
  private static final int GROUND_REACH = 2;

  private final @NotNull Plugin plugin;
  static final int PLAIN = 0;
  static final int FOGGED = -1;
  static final int BARE = -2;
  static final int SKY = -3;
  static final int FULL = -4;
  private static final int SKY_CEILING = Fog.SKY_Y + Fog.SKY_THICKNESS - 1;

  private final @NotNull Cache<ChunkKey, Variants> cache;

  private final @NotNull Set<ChunkKey> changed = ConcurrentHashMap.newKeySet();
  private final @NotNull Map<String, Object> refreshLocks = new ConcurrentHashMap<>();
  private final @NotNull AtomicLong epoch = new AtomicLong();

  private final @NotNull Map<String, Long2LongOpenHashMap> heights = new ConcurrentHashMap<>();
  private final @NotNull Map<String, Long2IntOpenHashMap> grounds = new ConcurrentHashMap<>();
  private final @NotNull BlockShapeResolver shapes;
  private final @NotNull WorldLimits limits;
  private final @NotNull MapRegions regions;

  public ChunkColumnsCacheImpl(
      final @NotNull Plugin plugin,
      final int maximumChunks,
      final @NotNull BlockShapeResolver shapes,
      final @NotNull WorldLimits limits,
      final @NotNull MapRegions regions) {
    super();
    this.plugin = plugin;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(Math.max(256, maximumChunks))
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();
    this.shapes = shapes;
    this.limits = limits;
    this.regions = regions;
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

  int variant(
      final @NotNull String worldName,
      final int chunkX,
      final int chunkZ,
      final @NotNull FogView view) {
    final @Nullable MapRegion owner = this.regions.foggedAt(worldName, chunkX, chunkZ);
    final int base = owner == null ? PLAIN : FOGGED;
    return switch (view.kind()) {
      case BASE -> base;
      case CLEAR -> PLAIN;
      case BODY -> owner != null && owner.id().equals(view.region()) ? PLAIN : base;
      case SPILL -> {
        if (owner != null && owner.id().equals(view.region())) {
          yield SKY;
        }
        for (final @NotNull UUID near : this.regions.foggedNear(worldName, chunkX, chunkZ)) {
          if (near.equals(view.region())) {
            yield this.regions.index(near);
          }
        }
        yield owner == null ? PLAIN : BARE;
      }
    };
  }

  @Nullable
  ChunkColumns get(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int chunkX,
      final int chunkZ,
      final int variant) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull ChunkKey key = new ChunkKey(worldName, chunkX, chunkZ);
    final long epoch = this.epoch.get();
    final @Nullable ChunkColumns cached = getIfPresent(key, variant);
    if (cached != null) {
      return cached;
    }

    final boolean current = !this.changed.contains(key);
    final @Nullable ChunkColumns columns;
    if (variant == PLAIN || (variant == FULL && opaque(worldName, chunkX, chunkZ))) {
      final @Nullable WorldLimit limit = this.limits.get(worldName);
      final @Nullable ColumnSource source =
          limit == null
                  || limit.intersects(
                      chunkX << 4, chunkZ << 4, (chunkX << 4) + 15, (chunkZ << 4) + 15)
              ? provider.sourceForChunk(chunkX, chunkZ)
              : null;
      if (source == null) {
        if (current && epoch == this.epoch.get()) {
          recordHeights(worldName, chunkX, chunkZ, ChunkColumns.MISSING_HEIGHTS);
        }
        return null;
      }
      columns =
          variant == PLAIN
              ? ColumnRunSampler.sampleChunk(
                  source, ColumnRunSampler.ceilingY(world), this.shapes, chunkX, chunkZ, limit)
              : ColumnRunSampler.blockedChunk(
                  source,
                  groundAround(world, provider, chunkX, chunkZ),
                  fogAround(worldName, chunkX, chunkZ, null),
                  chunkX,
                  chunkZ);
    } else {
      columns = derive(world, provider, chunkX, chunkZ, variant);
      if (columns == null) {
        return null;
      }
    }
    if (current) {
      this.cache
          .asMap()
          .compute(
              key,
              (ignored, existing) -> {
                if (epoch != this.epoch.get()) {
                  return existing;
                }
                final @NotNull Variants variants =
                    existing == null
                        ? Variants.of(variant, columns)
                        : existing.with(variant, columns);
                if (variant == PLAIN || variant == FOGGED) {
                  recordHeights(worldName, chunkX, chunkZ, variants.heights());
                }
                return variants;
              });
    }
    return columns;
  }

  private @Nullable ChunkColumns derive(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int chunkX,
      final int chunkZ,
      final int variant) {
    final @NotNull String worldName = WorldKeys.id(world);
    return switch (variant) {
      case FULL -> {
        final @Nullable ChunkColumns plain = get(world, provider, chunkX, chunkZ, PLAIN);
        yield plain == null
            ? null
            : plain.withMist(
                Fog.hidden(
                    chunkX,
                    chunkZ,
                    groundAround(world, provider, chunkX, chunkZ),
                    fogAround(worldName, chunkX, chunkZ, null),
                    true));
      }
      case FOGGED -> {
        final @Nullable ChunkColumns full = get(world, provider, chunkX, chunkZ, FULL);
        yield full == null ? null : full.mapMist(Fog::withoutSky);
      }
      case SKY -> {
        final @Nullable ChunkColumns full = get(world, provider, chunkX, chunkZ, FULL);
        yield full == null ? null : full.mapMist(Fog::skyOnly);
      }
      case BARE -> {
        final @Nullable ChunkColumns fogged = get(world, provider, chunkX, chunkZ, FOGGED);
        yield fogged == null ? null : fogged.withoutMist();
      }
      default -> {
        final @Nullable ChunkColumns geometry =
            get(
                world,
                provider,
                chunkX,
                chunkZ,
                this.regions.foggedAt(worldName, chunkX, chunkZ) == null ? PLAIN : FOGGED);
        final @Nullable MapRegion spilling = this.regions.indexed(variant);
        yield geometry == null
            ? null
            : spilling == null
                ? geometry.withoutMist()
                : geometry.withMist(
                    Fog.mist(
                        chunkX,
                        chunkZ,
                        geometry,
                        groundAround(world, provider, chunkX, chunkZ),
                        fogAround(worldName, chunkX, chunkZ, spilling)));
      }
    };
  }

  private @Nullable ChunkColumns getIfPresent(final @NotNull ChunkKey key, final int variant) {
    final @Nullable Variants variants = this.cache.getIfPresent(key);
    return variants == null ? null : variants.get(variant);
  }

  @Nullable
  ChunkColumns getIfPresent(
      final @NotNull String worldName, final int chunkX, final int chunkZ, final int variant) {
    return getIfPresent(new ChunkKey(worldName, chunkX, chunkZ), variant);
  }

  @Override
  public long knownHeights(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    return knownHeights(worldName, chunkX, chunkZ, true);
  }

  @Override
  public long knownHeights(
      final @NotNull String worldName, final int chunkX, final int chunkZ, final boolean sky) {
    final @Nullable Long2LongOpenHashMap world = this.heights.get(worldName);
    if (world == null) {
      return ChunkColumns.UNKNOWN_HEIGHTS;
    }

    final long known;
    synchronized (world) {
      known = world.get(ChunkCoordinate.key(chunkX, chunkZ));
    }
    if (!sky
        || known == ChunkColumns.UNKNOWN_HEIGHTS
        || known == ChunkColumns.MISSING_HEIGHTS
        || ChunkColumns.maxTopOf(known) == ChunkColumns.EMPTY_COLUMN
        || ChunkColumns.maxTopOf(known) >= SKY_CEILING
        || this.regions.foggedNear(worldName, chunkX, chunkZ).length == 0) {
      return known;
    }
    return ((long) ChunkColumns.minTopOf(known) << 32) | (SKY_CEILING & 0xFFFFFFFFL);
  }

  @Override
  public int refreshChanged(final @NotNull World world, final @NotNull LongSet regrounded)
      throws Exception {
    synchronized (this.refreshLocks.computeIfAbsent(WorldKeys.id(world), ignored -> new Object())) {
      final @NotNull List<ChunkCoordinate> changed = new ArrayList<>();
      for (final @NotNull ChunkKey key : this.changed) {
        if (key.worldName().equals(WorldKeys.id(world)) && this.changed.remove(key)) {
          changed.add(new ChunkCoordinate(key.chunkX(), key.chunkZ()));
        }
      }

      if (changed.isEmpty()) {
        return 0;
      }

      int refreshed = 0;
      int next = 0;
      try (final @NotNull RegionFileChunkSourceProvider disk =
              new RegionFileChunkSourceProvider(world)) {
        while (next < changed.size()) {
          final int from = next;
          final @NotNull Map<Long, LiveChunk> snapshots = new HashMap<>();
          next =
              Bukkit.getScheduler()
                  .callSyncMethod(this.plugin, () -> takeSnapshots(world, changed, from, snapshots))
                  .get();
          final @NotNull LiveChunkSourceProvider live =
              new LiveChunkSourceProvider(
                  snapshots, world.getMinHeight(), world.getMaxHeight(), disk);
          for (final @NotNull ChunkCoordinate chunk : changed.subList(from, next)) {
            if (snapshots.containsKey(chunk.toKey())) {
              reground(world, disk, live, chunk.x(), chunk.z(), regrounded);
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
  public @NotNull LongSet groundDependents(
      final @NotNull String worldName, final @NotNull Collection<Long> chunks) {
    final @NotNull LongSet dependents = new LongOpenHashSet();
    if (!this.regions.anyFogged(worldName)) {
      return dependents;
    }
    for (final long chunk : chunks) {
      for (int offsetX = -GROUND_REACH; offsetX <= GROUND_REACH; offsetX++) {
        for (int offsetZ = -GROUND_REACH; offsetZ <= GROUND_REACH; offsetZ++) {
          final int chunkX = ChunkCoordinate.keyX(chunk) + offsetX;
          final int chunkZ = ChunkCoordinate.keyZ(chunk) + offsetZ;
          if (this.regions.foggedNear(worldName, chunkX, chunkZ).length > 0) {
            dependents.add(ChunkCoordinate.key(chunkX, chunkZ));
          }
        }
      }
    }
    return dependents;
  }

  private void reground(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider before,
      final @NotNull ChunkSourceProvider after,
      final int chunkX,
      final int chunkZ,
      final @NotNull LongSet regrounded) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull LongSet dependents =
        groundDependents(worldName, List.of(ChunkCoordinate.key(chunkX, chunkZ)));
    if (dependents.isEmpty()) {
      return;
    }
    final int ceilingY = ColumnRunSampler.ceilingY(world);
    final int was = visibleGround(world, before, ceilingY, chunkX, chunkZ);
    final @Nullable ColumnSource source = after.sourceForChunk(chunkX, chunkZ);
    if (source == null) {
      return;
    }
    final int ground = Fog.ground(ColumnRunSampler.columnTops(source, ceilingY, this.shapes));
    if (ground == was) {
      return;
    }
    remember(worldName, chunkX, chunkZ, ground);
    this.epoch.incrementAndGet();
    for (final long chunk : dependents) {
      this.cache
          .asMap()
          .computeIfPresent(
              new ChunkKey(worldName, ChunkCoordinate.keyX(chunk), ChunkCoordinate.keyZ(chunk)),
              (_, variants) -> variants.only(PLAIN));
    }
    regrounded.addAll(dependents);
  }

  @Override
  public @NotNull CachedChunkColumnsSource sourceFor(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final @NotNull FogView view) {
    return new CachedChunkColumnsSource(this, world, provider, view);
  }

  @Override
  public void markChanged(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    this.changed.add(new ChunkKey(worldName, chunkX, chunkZ));
  }

  @Override
  public void forget(
      final @NotNull String worldName,
      final @NotNull Collection<Long> chunks,
      final boolean terrain) {
    this.epoch.incrementAndGet();
    final @Nullable Long2IntOpenHashMap grounds = this.grounds.get(worldName);
    if (terrain && grounds != null) {
      synchronized (grounds) {
        chunks.forEach(chunk -> grounds.remove((long) chunk));
      }
    }
    for (final long chunk : chunks) {
      final @NotNull ChunkKey key =
          new ChunkKey(worldName, ChunkCoordinate.keyX(chunk), ChunkCoordinate.keyZ(chunk));
      if (terrain) {
        this.cache.invalidate(key);
      } else {
        this.cache.asMap().computeIfPresent(key, (_, variants) -> variants.only(PLAIN));
      }
    }
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

  private int @NotNull [] groundAround(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int chunkX,
      final int chunkZ) {
    final int ceilingY = ColumnRunSampler.ceilingY(world);
    final int @NotNull [] groundAround = new int[9];
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        final int ground = ground(world, provider, ceilingY, chunkX + offsetX, chunkZ + offsetZ);
        groundAround[(offsetX + 1) * 3 + offsetZ + 1] =
            ground == ColumnSource.EMPTY_COLUMN ? world.getSeaLevel() : ground;
      }
    }
    return groundAround;
  }

  private byte @NotNull [] fogAround(
      final @NotNull String worldName,
      final int chunkX,
      final int chunkZ,
      final @Nullable MapRegion only) {
    final byte @NotNull [] around = new byte[Fog.AROUND * Fog.AROUND];
    for (int offsetX = -Fog.REACH_CHUNKS; offsetX <= Fog.REACH_CHUNKS; offsetX++) {
      for (int offsetZ = -Fog.REACH_CHUNKS; offsetZ <= Fog.REACH_CHUNKS; offsetZ++) {
        final @Nullable MapRegion region =
            only == null
                ? this.regions.foggedAt(worldName, chunkX + offsetX, chunkZ + offsetZ)
                : only.contains(chunkX + offsetX, chunkZ + offsetZ) ? only : null;
        around[Fog.around(offsetX, offsetZ)] =
            region == null ? Fog.OPEN : region.fog() == RegionFog.VEIL ? Fog.VEILED : Fog.HIDDEN;
      }
    }
    return around;
  }

  private int ground(
      final @NotNull World world,
      final @NotNull ChunkSourceProvider provider,
      final int ceilingY,
      final int chunkX,
      final int chunkZ) {
    if (!opaque(WorldKeys.id(world), chunkX, chunkZ)) {
      return visibleGround(world, provider, ceilingY, chunkX, chunkZ);
    }
    int sum = 0;
    int count = 0;
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (opaque(WorldKeys.id(world), chunkX + offsetX, chunkZ + offsetZ)) {
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

  private boolean opaque(final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable MapRegion region = this.regions.foggedAt(worldName, chunkX, chunkZ);
    return region != null && region.fog() == RegionFog.HIDE;
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
    remember(WorldKeys.id(world), chunkX, chunkZ, ground);
    return ground;
  }

  private void remember(
      final @NotNull String worldName, final int chunkX, final int chunkZ, final int ground) {
    final @NotNull Long2IntOpenHashMap known =
        this.grounds.computeIfAbsent(worldName, ignored -> new Long2IntOpenHashMap());
    synchronized (known) {
      known.put(ChunkCoordinate.key(chunkX, chunkZ), ground);
    }
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
    if (provider.sourceForChunk(chunkX, chunkZ) == null) {
      return;
    }
    this.cache.invalidate(new ChunkKey(WorldKeys.id(world), chunkX, chunkZ));
    get(
        world,
        provider,
        chunkX,
        chunkZ,
        variant(WorldKeys.id(world), chunkX, chunkZ, FogView.BASE));
  }

  @Override
  public void resize(final int maximumChunks) {
    this.cache
        .policy()
        .eviction()
        .ifPresent(eviction -> eviction.setMaximum(Math.max(256, maximumChunks)));
  }

  private record Variants(int @NotNull [] ids, ChunkColumns @NotNull [] columns) {

    static @NotNull Variants of(final int id, final @NotNull ChunkColumns columns) {
      return new Variants(new int[] {id}, new ChunkColumns[] {columns});
    }

    @Nullable
    ChunkColumns get(final int id) {
      for (int index = 0; index < this.ids.length; index++) {
        if (this.ids[index] == id) {
          return this.columns[index];
        }
      }
      return null;
    }

    @Nullable
    Variants only(final int id) {
      final @Nullable ChunkColumns columns = get(id);
      return columns == null ? null : of(id, columns);
    }

    @NotNull
    Variants with(final int id, final @NotNull ChunkColumns columns) {
      if (get(id) != null) {
        return this;
      }
      final int @NotNull [] ids = Arrays.copyOf(this.ids, this.ids.length + 1);
      final ChunkColumns @NotNull [] all = Arrays.copyOf(this.columns, this.columns.length + 1);
      ids[this.ids.length] = id;
      all[this.columns.length] = columns;
      return new Variants(ids, all);
    }

    long heights() {
      int min = Integer.MAX_VALUE;
      int max = Integer.MIN_VALUE;
      long heights = ChunkColumns.MISSING_HEIGHTS;
      for (int index = 0; index < this.ids.length; index++) {
        if (this.ids[index] != PLAIN && this.ids[index] != FOGGED) {
          continue;
        }
        heights = this.columns[index].heights();
        if (!this.columns[index].isEmpty()) {
          min = Math.min(min, this.columns[index].minTopY());
          max = Math.max(max, this.columns[index].maxTopY());
        }
      }
      return max == Integer.MIN_VALUE ? heights : ((long) min << 32) | (max & 0xFFFFFFFFL);
    }
  }
}

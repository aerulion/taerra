package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.util.HexColors;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapRegions {

  public static final int CLOSE_CHUNKS = 5;
  private static final @NotNull UUID @NotNull [] NONE = new UUID[0];

  private final @NotNull Plugin plugin;
  private final @NotNull File file;
  private final @NotNull Map<UUID, Integer> indexes = new ConcurrentHashMap<>();
  private final @NotNull Map<Integer, UUID> indexed = new ConcurrentHashMap<>();
  private final @NotNull AtomicInteger nextIndex = new AtomicInteger(1);
  private volatile @NotNull Snapshot snapshot = Snapshot.EMPTY;

  public MapRegions(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "regions.yml");
    final @NotNull List<MapRegion> loaded = new ArrayList<>();
    if (this.file.isFile()) {
      final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
      for (final @NotNull Map<?, ?> entry : yaml.getMapList("regions")) {
        try {
          final @NotNull UUID id = UUID.fromString(String.valueOf(entry.get("id")));
          final @Nullable RegionFog fog = RegionFog.parse(String.valueOf(entry.get("fog")));
          final @NotNull OptionalInt color =
              HexColors.parseRgb(
                  entry.get("color") == null ? null : String.valueOf(entry.get("color")));
          loaded.add(
              MapRegion.of(
                  id,
                  (String) entry.get("world"),
                  entry.get("name") == null ? "" : String.valueOf(entry.get("name")),
                  color.orElse(
                      MapRegion.PALETTE[Math.floorMod(id.hashCode(), MapRegion.PALETTE.length)]),
                  fog == null ? RegionFog.HIDE : fog,
                  Boolean.TRUE.equals(entry.get("discoverable")),
                  entry.get("reset") instanceof Number reset ? reset.longValue() : 0L,
                  chunks((List<?>) entry.get("chunks"))));
        } catch (final RuntimeException e) {
          plugin.getLogger().warning("Skipping a malformed region in regions.yml: " + entry);
        }
      }
    } else {
      loaded.addAll(migrate(new File(plugin.getDataFolder(), "limits.yml")));
    }
    this.snapshot = Snapshot.of(withoutOverlaps(loaded));
    if (!this.file.isFile() && !loaded.isEmpty()) {
      save();
    }
  }

  static @NotNull LongSet chunks(final @NotNull List<?> entries) {
    final @NotNull LongSet chunks = new LongOpenHashSet();
    for (final @NotNull Object chunk : entries) {
      final @NotNull String[] xz = String.valueOf(chunk).split(",");
      chunks.add(
          ChunkCoordinate.key(Integer.parseInt(xz[0].strip()), Integer.parseInt(xz[1].strip())));
    }
    return chunks;
  }

  private @NotNull List<MapRegion> migrate(final @NotNull File limits) {
    if (!limits.isFile()) {
      return List.of();
    }
    final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(limits);
    final @NotNull Map<String, LongSet> hidden = legacy(yaml, "hidden");
    final @NotNull Map<String, LongSet> veils = legacy(yaml, "teased");
    final @NotNull List<MapRegion> migrated = new ArrayList<>();
    hidden.forEach(
        (world, chunks) -> {
          final @NotNull LongSet veiled = new LongOpenHashSet(chunks);
          veiled.retainAll(veils.getOrDefault(world, new LongOpenHashSet()));
          final @NotNull LongSet blocked = new LongOpenHashSet(chunks);
          blocked.removeAll(veiled);
          for (final @NotNull Set<Long> area : RegionShapes.areas(blocked)) {
            migrated.add(migrated(migrated.size(), world, RegionFog.HIDE, area));
          }
          for (final @NotNull Set<Long> area : RegionShapes.areas(veiled)) {
            migrated.add(migrated(migrated.size(), world, RegionFog.VEIL, area));
          }
        });
    if (!migrated.isEmpty()) {
      this.plugin
          .getLogger()
          .info("Turned the hidden chunks in limits.yml into " + migrated.size() + " regions");
    }
    return migrated;
  }

  private static @NotNull MapRegion migrated(
      final int index,
      final @NotNull String world,
      final @NotNull RegionFog fog,
      final @NotNull Set<Long> area) {
    return MapRegion.of(
        UUID.randomUUID(),
        world,
        "",
        MapRegion.PALETTE[index % MapRegion.PALETTE.length],
        fog,
        false,
        0L,
        new LongOpenHashSet(area));
  }

  private @NotNull Map<String, LongSet> legacy(
      final @NotNull YamlConfiguration yaml, final @NotNull String key) {
    final @NotNull Map<String, LongSet> chunksByWorld = new HashMap<>();
    for (final @NotNull Map<?, ?> entry : yaml.getMapList(key)) {
      try {
        chunksByWorld.put((String) entry.get("world"), chunks((List<?>) entry.get("chunks")));
      } catch (final RuntimeException e) {
        this.plugin
            .getLogger()
            .warning("Skipping malformed " + key + " chunks in limits.yml: " + entry);
      }
    }
    return chunksByWorld;
  }

  private @NotNull List<MapRegion> withoutOverlaps(final @NotNull List<MapRegion> regions) {
    final @NotNull Map<String, LongSet> taken = new HashMap<>();
    final @NotNull List<MapRegion> kept = new ArrayList<>();
    for (final @NotNull MapRegion region : regions) {
      final @NotNull LongSet world =
          taken.computeIfAbsent(region.world(), _ -> new LongOpenHashSet());
      final @NotNull LongSet chunks = new LongOpenHashSet(region.chunks());
      chunks.removeAll(world);
      if (chunks.size() != region.chunks().size()) {
        this.plugin
            .getLogger()
            .warning("Region " + region.id() + " overlaps another one, dropped the shared chunks");
      }
      if (!chunks.isEmpty()) {
        world.addAll(chunks);
        kept.add(
            region.with(
                region.name(), region.color(), region.fog(), region.discoverable(), chunks));
      }
    }
    return kept;
  }

  public @Nullable MapRegion get(final @NotNull UUID id) {
    return this.snapshot.byId().get(id);
  }

  public int index(final @NotNull UUID id) {
    return this.indexes.computeIfAbsent(
        id,
        key -> {
          final int index = this.nextIndex.getAndIncrement();
          this.indexed.put(index, key);
          return index;
        });
  }

  public @Nullable MapRegion indexed(final int index) {
    final @Nullable UUID id = this.indexed.get(index);
    return id == null ? null : get(id);
  }

  public @Nullable MapRegion at(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable Long2ObjectOpenHashMap<MapRegion> owners =
        this.snapshot.owners().get(worldName);
    return owners == null ? null : owners.get(ChunkCoordinate.key(chunkX, chunkZ));
  }

  public @Nullable MapRegion foggedAt(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable MapRegion region = at(worldName, chunkX, chunkZ);
    return region != null && region.fog().fogged() ? region : null;
  }

  public @NotNull List<MapRegion> in(final @NotNull String worldName) {
    return this.snapshot.byWorld().getOrDefault(worldName, List.of());
  }

  public @NotNull UUID @NotNull [] foggedNear(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    return near(this.snapshot.near(), worldName, chunkX, chunkZ);
  }

  public @NotNull UUID @NotNull [] foggedClose(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    return near(this.snapshot.close(), worldName, chunkX, chunkZ);
  }

  private static @NotNull UUID @NotNull [] near(
      final @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> reach,
      final @NotNull String worldName,
      final int chunkX,
      final int chunkZ) {
    final @Nullable Long2ObjectOpenHashMap<UUID[]> world = reach.get(worldName);
    final @NotNull UUID @Nullable [] ids =
        world == null ? null : world.get(ChunkCoordinate.key(chunkX, chunkZ));
    return ids == null ? NONE : ids;
  }

  public boolean anyFogged(final @NotNull String worldName) {
    return this.snapshot.near().containsKey(worldName);
  }

  public int freshColor(final @NotNull String worldName) {
    final int @NotNull [] uses = new int[MapRegion.PALETTE.length];
    for (final @NotNull MapRegion region : in(worldName)) {
      for (int index = 0; index < uses.length; index++) {
        if (MapRegion.PALETTE[index] == region.color()) {
          uses[index]++;
        }
      }
    }
    int best = 0;
    for (int index = 1; index < uses.length; index++) {
      if (uses[index] < uses[best]) {
        best = index;
      }
    }
    return MapRegion.PALETTE[best];
  }

  public synchronized void put(final @NotNull MapRegion region) {
    final @NotNull Map<UUID, MapRegion> regions = new HashMap<>(this.snapshot.byId());
    regions.put(region.id(), region);
    this.snapshot = Snapshot.of(regions.values());
    save();
  }

  public synchronized void remove(final @NotNull UUID id) {
    final @NotNull Map<UUID, MapRegion> regions = new HashMap<>(this.snapshot.byId());
    if (regions.remove(id) != null) {
      this.snapshot = Snapshot.of(regions.values());
      save();
    }
  }

  private void save() {
    final @NotNull List<Map<String, Object>> entries = new ArrayList<>();
    this.snapshot.byId().values().stream()
        .sorted(Comparator.comparing(MapRegion::world).thenComparing(MapRegion::id))
        .forEach(
            region -> {
              final @NotNull Map<String, Object> entry = new LinkedHashMap<>();
              entry.put("id", region.id().toString());
              entry.put("world", region.world());
              entry.put("name", region.name());
              entry.put("color", HexColors.format(region.color()));
              entry.put("fog", region.fog().id());
              entry.put("discoverable", region.discoverable());
              if (region.reset() > 0L) {
                entry.put("reset", region.reset());
              }
              entry.put(
                  "chunks",
                  region
                      .chunks()
                      .longStream()
                      .sorted()
                      .mapToObj(
                          chunk -> ChunkCoordinate.keyX(chunk) + "," + ChunkCoordinate.keyZ(chunk))
                      .toList());
              entries.add(entry);
            });
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("regions", entries);
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to save regions.yml", e);
    }
  }

  private record Snapshot(
      @NotNull Map<UUID, MapRegion> byId,
      @NotNull Map<String, List<MapRegion>> byWorld,
      @NotNull Map<String, Long2ObjectOpenHashMap<MapRegion>> owners,
      @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> near,
      @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> close) {

    private static final @NotNull Snapshot EMPTY =
        new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    private static @NotNull Snapshot of(final @NotNull Iterable<MapRegion> regions) {
      final @NotNull Map<UUID, MapRegion> byId = new HashMap<>();
      final @NotNull Map<String, List<MapRegion>> byWorld = new HashMap<>();
      final @NotNull Map<String, Long2ObjectOpenHashMap<MapRegion>> owners = new HashMap<>();
      final @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> near = new HashMap<>();
      final @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> close = new HashMap<>();
      for (final @NotNull MapRegion region : regions) {
        if (region.fog().fogged()) {
          spread(near, region, Fog.REACH_CHUNKS);
          spread(close, region, CLOSE_CHUNKS);
        }
        byId.put(region.id(), region);
        byWorld.computeIfAbsent(region.world(), _ -> new ArrayList<>()).add(region);
        final @NotNull Long2ObjectOpenHashMap<MapRegion> world =
            owners.computeIfAbsent(region.world(), _ -> new Long2ObjectOpenHashMap<>());
        for (final long chunk : region.chunks()) {
          world.put(chunk, region);
        }
      }
      byWorld.replaceAll(
          (world, list) ->
              list.stream()
                  .sorted(Comparator.comparing(MapRegion::name).thenComparing(MapRegion::id))
                  .toList());
      return new Snapshot(
          Map.copyOf(byId),
          Map.copyOf(byWorld),
          Map.copyOf(owners),
          Map.copyOf(near),
          Map.copyOf(close));
    }

    private static void spread(
        final @NotNull Map<String, Long2ObjectOpenHashMap<UUID[]>> into,
        final @NotNull MapRegion region,
        final int reach) {
      final @NotNull LongSet reached = new LongOpenHashSet();
      for (final long chunk : region.chunks()) {
        final int chunkX = ChunkCoordinate.keyX(chunk);
        final int chunkZ = ChunkCoordinate.keyZ(chunk);
        if (inside(region, chunkX, chunkZ)) {
          reached.add(chunk);
          continue;
        }
        for (int offsetX = -reach; offsetX <= reach; offsetX++) {
          for (int offsetZ = -reach; offsetZ <= reach; offsetZ++) {
            reached.add(ChunkCoordinate.key(chunkX + offsetX, chunkZ + offsetZ));
          }
        }
      }
      final @NotNull Long2ObjectOpenHashMap<UUID[]> world =
          into.computeIfAbsent(region.world(), _ -> new Long2ObjectOpenHashMap<>());
      for (final long chunk : reached) {
        final @NotNull UUID @Nullable [] ids = world.get(chunk);
        if (ids == null) {
          world.put(chunk, new UUID[] {region.id()});
        } else {
          final @NotNull UUID @NotNull [] grown = Arrays.copyOf(ids, ids.length + 1);
          grown[ids.length] = region.id();
          world.put(chunk, grown);
        }
      }
    }

    private static boolean inside(
        final @NotNull MapRegion region, final int chunkX, final int chunkZ) {
      for (int offsetX = -1; offsetX <= 1; offsetX++) {
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
          if (!region.contains(chunkX + offsetX, chunkZ + offsetZ)) {
            return false;
          }
        }
      }
      return true;
    }
  }
}

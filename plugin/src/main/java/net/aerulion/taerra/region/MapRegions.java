package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.util.AtomicFiles;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapRegions {

  private final @NotNull Plugin plugin;
  private final @NotNull File file;
  private volatile @NotNull Snapshot snapshot = Snapshot.EMPTY;
  private int nextId = 1;

  public MapRegions(final @NotNull Plugin plugin) {
    super();
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "regions.yml");
    final @NotNull List<MapRegion> loaded = new ArrayList<>();
    if (this.file.isFile()) {
      final @NotNull YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
      this.nextId = Math.max(1, yaml.getInt("next-id", 1));
      for (final @NotNull Map<?, ?> entry : yaml.getMapList("regions")) {
        try {
          final int id = ((Number) entry.get("id")).intValue();
          final @Nullable RegionFog fog = RegionFog.parse(String.valueOf(entry.get("fog")));
          loaded.add(
              MapRegion.of(
                  id,
                  (String) entry.get("world"),
                  entry.get("name") == null ? "" : String.valueOf(entry.get("name")),
                  fog == null ? RegionFog.HIDE : fog,
                  Boolean.TRUE.equals(entry.get("discoverable")),
                  chunks((List<?>) entry.get("chunks"))));
          this.nextId = Math.max(this.nextId, id + 1);
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
    final @NotNull Map<String, LongSet> teased = legacy(yaml, "teased");
    final @NotNull List<MapRegion> migrated = new ArrayList<>();
    hidden.forEach(
        (world, chunks) -> {
          final @NotNull LongSet veiled = new LongOpenHashSet(chunks);
          veiled.retainAll(teased.getOrDefault(world, new LongOpenHashSet()));
          final @NotNull LongSet blocked = new LongOpenHashSet(chunks);
          blocked.removeAll(veiled);
          for (final @NotNull Set<Long> area : RegionShapes.areas(blocked)) {
            migrated.add(
                MapRegion.of(
                    this.nextId++, world, "", RegionFog.HIDE, false, new LongOpenHashSet(area)));
          }
          for (final @NotNull Set<Long> area : RegionShapes.areas(veiled)) {
            migrated.add(
                MapRegion.of(
                    this.nextId++, world, "", RegionFog.VEIL, false, new LongOpenHashSet(area)));
          }
        });
    if (!migrated.isEmpty()) {
      this.plugin
          .getLogger()
          .info("Turned the hidden chunks in limits.yml into " + migrated.size() + " regions");
    }
    return migrated;
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
        kept.add(region.with(region.name(), region.fog(), region.discoverable(), chunks));
      }
    }
    return kept;
  }

  public @Nullable MapRegion get(final int id) {
    return this.snapshot.byId().get(id);
  }

  public @Nullable MapRegion at(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable Long2IntOpenHashMap owners = this.snapshot.owners().get(worldName);
    return owners == null ? null : get(owners.get(ChunkCoordinate.key(chunkX, chunkZ)));
  }

  public @Nullable MapRegion foggedAt(
      final @NotNull String worldName, final int chunkX, final int chunkZ) {
    final @Nullable MapRegion region = at(worldName, chunkX, chunkZ);
    return region != null && region.fog().fogged() ? region : null;
  }

  public @NotNull List<MapRegion> in(final @NotNull String worldName) {
    return this.snapshot.byWorld().getOrDefault(worldName, List.of());
  }

  public @NotNull List<MapRegion> all() {
    return List.copyOf(this.snapshot.byId().values());
  }

  public @NotNull List<MapRegion> fogged(
      final @NotNull String worldName,
      final int minChunkX,
      final int minChunkZ,
      final int maxChunkX,
      final int maxChunkZ,
      final int reach) {
    final @NotNull List<MapRegion> found = new ArrayList<>();
    for (final @NotNull MapRegion region : in(worldName)) {
      if (region.fog().fogged()
          && region.reaches(minChunkX, minChunkZ, maxChunkX, maxChunkZ, reach)) {
        found.add(region);
      }
    }
    return found;
  }

  public boolean foggedNear(
      final @NotNull String worldName, final int chunkX, final int chunkZ, final int reach) {
    for (final @NotNull MapRegion region : in(worldName)) {
      if (region.fog().fogged() && region.reaches(chunkX, chunkZ, chunkX, chunkZ, reach)) {
        return true;
      }
    }
    return false;
  }

  public boolean anyFogged(final @NotNull String worldName) {
    return in(worldName).stream().anyMatch(region -> region.fog().fogged());
  }

  public long revision() {
    return this.snapshot.revision();
  }

  public synchronized int reserveId() {
    return this.nextId++;
  }

  public synchronized void put(final @NotNull MapRegion region) {
    final @NotNull Map<Integer, MapRegion> regions = new HashMap<>(this.snapshot.byId());
    regions.put(region.id(), region);
    this.snapshot = Snapshot.of(regions.values(), this.snapshot.revision() + 1);
    save();
  }

  public synchronized void remove(final int id) {
    final @NotNull Map<Integer, MapRegion> regions = new HashMap<>(this.snapshot.byId());
    if (regions.remove(id) != null) {
      this.snapshot = Snapshot.of(regions.values(), this.snapshot.revision() + 1);
      save();
    }
  }

  private void save() {
    final @NotNull List<Map<String, Object>> entries = new ArrayList<>();
    this.snapshot.byId().values().stream()
        .sorted(Comparator.comparingInt(MapRegion::id))
        .forEach(
            region ->
                entries.add(
                    Map.of(
                        "id",
                        region.id(),
                        "world",
                        region.world(),
                        "name",
                        region.name(),
                        "fog",
                        region.fog().id(),
                        "discoverable",
                        region.discoverable(),
                        "chunks",
                        region
                            .chunks()
                            .longStream()
                            .sorted()
                            .mapToObj(
                                chunk ->
                                    ChunkCoordinate.keyX(chunk) + "," + ChunkCoordinate.keyZ(chunk))
                            .toList())));
    final @NotNull YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("next-id", this.nextId);
    yaml.set("regions", entries);
    try {
      AtomicFiles.write(this.file, yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    } catch (final IOException e) {
      this.plugin.getLogger().log(Level.WARNING, "Failed to save regions.yml", e);
    }
  }

  private record Snapshot(
      @NotNull Map<Integer, MapRegion> byId,
      @NotNull Map<String, List<MapRegion>> byWorld,
      @NotNull Map<String, Long2IntOpenHashMap> owners,
      long revision) {

    private static final @NotNull Snapshot EMPTY = new Snapshot(Map.of(), Map.of(), Map.of(), 0L);

    private static @NotNull Snapshot of(final @NotNull List<MapRegion> regions) {
      return of(regions, 1L);
    }

    private static @NotNull Snapshot of(
        final @NotNull Iterable<MapRegion> regions, final long revision) {
      final @NotNull Map<Integer, MapRegion> byId = new Int2ObjectOpenHashMap<>();
      final @NotNull Map<String, List<MapRegion>> byWorld = new HashMap<>();
      final @NotNull Map<String, Long2IntOpenHashMap> owners = new HashMap<>();
      for (final @NotNull MapRegion region : regions) {
        byId.put(region.id(), region);
        byWorld.computeIfAbsent(region.world(), _ -> new ArrayList<>()).add(region);
        final @NotNull Long2IntOpenHashMap world =
            owners.computeIfAbsent(region.world(), _ -> new Long2IntOpenHashMap());
        for (final long chunk : region.chunks()) {
          world.put(chunk, region.id());
        }
      }
      byWorld.replaceAll(
          (world, list) -> list.stream().sorted(Comparator.comparingInt(MapRegion::id)).toList());
      return new Snapshot(Map.copyOf(byId), Map.copyOf(byWorld), Map.copyOf(owners), revision);
    }
  }
}

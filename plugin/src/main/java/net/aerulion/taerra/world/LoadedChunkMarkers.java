package net.aerulion.taerra.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionShapes;
import net.aerulion.taerra.web.player.PlayerMapPayloadSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoadedChunkMarkers {

  private static final long SYNC_INTERVAL_TICKS = 100L;

  private LoadedChunkMarkers() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final @NotNull WorldLimits limits,
      final @NotNull MapRegions regions,
      final boolean shownByDefault) {
    final @NotNull MarkerLayer layer =
        registry.layer(plugin, "chunks", "Loaded chunks", shownByDefault);
    return Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              final @NotNull List<Marker> markers = new ArrayList<>();
              for (final @NotNull World world : Bukkit.getWorlds()) {
                if (config.worldEnabled(WorldKeys.id(world))) {
                  markers.addAll(markers(world, limits, regions));
                }
              }
              layer.setAll(markers);
            },
            20L,
            SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> markers(
      final @NotNull World world,
      final @NotNull WorldLimits limits,
      final @NotNull MapRegions regions) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @Nullable WorldLimit limit = limits.get(worldName);
    final @NotNull Map<Long, Level> levels = new HashMap<>();
    for (final @NotNull Chunk chunk : world.getLoadedChunks()) {
      final @Nullable Level level = Level.of(chunk.getLoadLevel());
      if (level == null
          || regions.foggedAt(worldName, chunk.getX(), chunk.getZ()) != null
          || (limit != null && !limit.contains((chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8))) {
        continue;
      }
      levels.put(ChunkCoordinate.key(chunk.getX(), chunk.getZ()), level);
    }
    if (levels.isEmpty()) {
      return List.of();
    }

    final @NotNull Set<Long> forced = new HashSet<>();
    for (final @NotNull Chunk chunk : world.getForceLoadedChunks()) {
      forced.add(ChunkCoordinate.key(chunk.getX(), chunk.getZ()));
    }
    final @NotNull Map<Long, Set<String>> tickets = new HashMap<>();
    world
        .getPluginChunkTickets()
        .forEach(
            (owner, chunks) -> {
              for (final @NotNull Chunk chunk : chunks) {
                tickets
                    .computeIfAbsent(
                        ChunkCoordinate.key(chunk.getX(), chunk.getZ()), _ -> new TreeSet<>())
                    .add(owner.getName());
              }
            });
    final @NotNull Map<Long, List<Player>> players = new HashMap<>();
    for (final @NotNull Player player : world.getPlayers()) {
      if (PlayerMapPayloadSerializer.shouldIncludePlayer(player)) {
        players
            .computeIfAbsent(
                ChunkCoordinate.key(
                    player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4),
                _ -> new ArrayList<>())
            .add(player);
      }
    }

    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull Level level : Level.values()) {
      final @NotNull Set<Long> reached = new HashSet<>();
      levels.forEach(
          (key, at) -> {
            if (at.compareTo(level) <= 0) {
              reached.add(key);
            }
          });
      for (final @NotNull Set<Long> area : RegionShapes.areas(reached)) {
        final @NotNull Map<String, String> details =
            details(world, level, area, levels, forced, tickets, players);
        final long id = area.stream().mapToLong(Long::longValue).min().orElseThrow();
        final @NotNull List<List<long[]>> rings = RegionShapes.rings(area);
        for (int i = 0; i < rings.size(); i++) {
          markers.add(
              Marker.polygon(
                      worldName + "/" + level.key() + "/" + id + "/" + i,
                      world.getKey(),
                      rings.get(i).stream()
                          .map(corner -> new Vector(corner[0] << 4, 0, corner[1] << 4))
                          .toList(),
                      63,
                      64,
                      level.label + " · " + count(area.size(), "chunk"))
                  .hollowed()
                  .withColor(level.color)
                  .withDetails(details));
        }
      }
    }
    return markers;
  }

  private static @NotNull Map<String, String> details(
      final @NotNull World world,
      final @NotNull Level level,
      final @NotNull Set<Long> area,
      final @NotNull Map<Long, Level> levels,
      final @NotNull Set<Long> forced,
      final @NotNull Map<Long, Set<String>> tickets,
      final @NotNull Map<Long, List<Player>> players) {
    final @NotNull Map<Level, Integer> counts = new HashMap<>();
    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    int forcedCount = 0;
    final @NotNull Set<String> owners = new TreeSet<>();
    final @NotNull List<String> names = new ArrayList<>();
    int unlisted = 0;
    for (final long key : area) {
      counts.merge(levels.get(key), 1, Integer::sum);
      minX = Math.min(minX, ChunkCoordinate.keyX(key));
      minZ = Math.min(minZ, ChunkCoordinate.keyZ(key));
      maxX = Math.max(maxX, ChunkCoordinate.keyX(key));
      maxZ = Math.max(maxZ, ChunkCoordinate.keyZ(key));
      if (forced.contains(key)) {
        forcedCount++;
      }
      owners.addAll(tickets.getOrDefault(key, Set.of()));
      for (final @NotNull Player player : players.getOrDefault(key, List.of())) {
        if (player.isAllowingServerListings()) {
          names.add(player.getName());
        } else {
          unlisted++;
        }
      }
    }

    final @NotNull Map<String, String> details = new LinkedHashMap<>();
    details.put("level", level.key());
    details.put("chunks", String.valueOf(area.size()));
    for (final @NotNull Level shown : Level.values()) {
      if (shown.compareTo(level) <= 0 && counts.containsKey(shown)) {
        details.put(shown.key(), String.valueOf(counts.get(shown)));
      }
    }
    details.put("width", String.valueOf(maxX - minX + 1));
    details.put("depth", String.valueOf(maxZ - minZ + 1));
    details.put("minX", String.valueOf(minX << 4));
    details.put("maxX", String.valueOf((maxX << 4) + 15));
    details.put("minZ", String.valueOf(minZ << 4));
    details.put("maxZ", String.valueOf((maxZ << 4) + 15));
    if (!names.isEmpty()) {
      names.sort(String.CASE_INSENSITIVE_ORDER);
      details.put("players", String.join(", ", names));
    }
    if (unlisted > 0) {
      details.put("anonymous", String.valueOf(unlisted));
    }
    if (forcedCount > 0) {
      details.put("forced", String.valueOf(forcedCount));
    }
    if (!owners.isEmpty()) {
      details.put("tickets", String.join(", ", owners));
    }
    details.put("view", String.valueOf(world.getViewDistance()));
    details.put("simulation", String.valueOf(world.getSimulationDistance()));
    return details;
  }

  private static @NotNull String count(final int count, final @NotNull String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }

  private enum Level {
    ENTITY_TICKING("Entity ticking", Color.fromRGB(0xFF4FD8)),
    TICKING("Ticking", Color.fromRGB(0xFF8A1F)),
    BORDER("Loaded", Color.fromRGB(0x2EE6E6));

    private final @NotNull String label;
    private final @NotNull Color color;

    Level(final @NotNull String label, final @NotNull Color color) {
      this.label = label;
      this.color = color;
    }

    private @NotNull String key() {
      return name().toLowerCase(Locale.ROOT);
    }

    private static @Nullable Level of(final @NotNull Chunk.LoadLevel level) {
      return switch (level) {
        case ENTITY_TICKING -> ENTITY_TICKING;
        case TICKING -> TICKING;
        case BORDER -> BORDER;
        case INACCESSIBLE, UNLOADED -> null;
      };
    }
  }
}

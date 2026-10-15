package net.aerulion.taerra.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
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
import net.aerulion.taerra.limit.HiddenAreaMarkers;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
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
  private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

  private LoadedChunkMarkers() {
    super();
  }

  public static @NotNull BukkitTask start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull TaerraConfig config,
      final @NotNull WorldLimits limits,
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
                  markers.addAll(markers(world, limits));
                }
              }
              layer.setAll(markers);
            },
            20L,
            SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> markers(
      final @NotNull World world, final @NotNull WorldLimits limits) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @Nullable WorldLimit limit = limits.get(worldName);
    final @NotNull Map<Long, Level> levels = new HashMap<>();
    for (final @NotNull Chunk chunk : world.getLoadedChunks()) {
      final @Nullable Level level = Level.of(chunk.getLoadLevel());
      if (level == null
          || limits.hidden(worldName, chunk.getX(), chunk.getZ())
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
      for (final @NotNull Set<Long> area : areas(reached)) {
        final @NotNull Map<String, String> details =
            details(world, level, area, levels, forced, tickets, players);
        final long id = area.stream().mapToLong(Long::longValue).min().orElseThrow();
        final @NotNull List<List<long[]>> rings = HiddenAreaMarkers.rings(area);
        for (int i = 0; i < rings.size(); i++) {
          markers.add(
              Marker.polygon(
                      worldName + "/" + level.name().toLowerCase(Locale.ROOT) + "/" + id + "/" + i,
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
    details.put("Load level", level.description);
    final @NotNull List<String> split = new ArrayList<>();
    for (final @NotNull Level shown : Level.values()) {
      if (shown.compareTo(level) <= 0 && counts.containsKey(shown)) {
        split.add(counts.get(shown) + " " + shown.name);
      }
    }
    details.put(
        "Chunks",
        split.size() > 1 ? area.size() + " (" + String.join(", ", split) + ")" : "" + area.size());
    details.put("Size", (maxX - minX + 1) + " × " + (maxZ - minZ + 1) + " chunks");
    details.put(
        "Blocks",
        "X "
            + (minX << 4)
            + " to "
            + ((maxX << 4) + 15)
            + ", Z "
            + (minZ << 4)
            + " to "
            + ((maxZ << 4) + 15));
    names.sort(String.CASE_INSENSITIVE_ORDER);
    if (unlisted > 0) {
      names.add(unlisted + " anonymous");
    }
    details.put("Players", names.isEmpty() ? "None" : String.join(", ", names));
    if (forcedCount > 0) {
      details.put("Force loaded", count(forcedCount, "chunk"));
    }
    if (!owners.isEmpty()) {
      details.put("Plugin tickets", String.join(", ", owners));
    }
    details.put("View distance", count(world.getViewDistance(), "chunk"));
    details.put("Simulation distance", count(world.getSimulationDistance(), "chunk"));
    return details;
  }

  private static @NotNull String count(final int count, final @NotNull String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }

  static @NotNull List<Set<Long>> areas(final @NotNull Collection<Long> chunks) {
    final @NotNull Set<Long> left = new HashSet<>(chunks);
    final @NotNull List<Set<Long>> areas = new ArrayList<>();
    while (!left.isEmpty()) {
      final long start = left.iterator().next();
      left.remove(start);
      final @NotNull Set<Long> area = new HashSet<>();
      final @NotNull Deque<Long> open = new ArrayDeque<>();
      open.add(start);
      while (!open.isEmpty()) {
        final long at = open.poll();
        area.add(at);
        for (final int @NotNull [] side : SIDES) {
          final long next =
              ChunkCoordinate.key(
                  ChunkCoordinate.keyX(at) + side[0], ChunkCoordinate.keyZ(at) + side[1]);
          if (left.remove(next)) {
            open.add(next);
          }
        }
      }
      areas.add(area);
    }
    return areas;
  }

  private enum Level {
    ENTITY_TICKING(
        "Entity ticking",
        "entity ticking",
        "Everything ticks: blocks, redstone and entities",
        Color.fromRGB(0xFF4FD8)),
    TICKING(
        "Ticking",
        "ticking",
        "Blocks and redstone tick, entities only in entity ticking chunks",
        Color.fromRGB(0xFF8A1F)),
    BORDER(
        "Loaded",
        "border",
        "In memory, nothing ticks outside ticking chunks",
        Color.fromRGB(0x2EE6E6));

    private final @NotNull String label;
    private final @NotNull String name;
    private final @NotNull String description;
    private final @NotNull Color color;

    Level(
        final @NotNull String label,
        final @NotNull String name,
        final @NotNull String description,
        final @NotNull Color color) {
      this.label = label;
      this.name = name;
      this.description = description;
      this.color = color;
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

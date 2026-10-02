package net.aerulion.taerra.limit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HiddenAreaMarkers {

  private static final long SYNC_INTERVAL_TICKS = 20L;
  private static final @NotNull Color COLOR = Color.fromRGB(0xFFCF3F);
  private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
  private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private HiddenAreaMarkers() {
    super();
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull MarkerRegistry registry,
      final @NotNull WorldLimits limits) {
    final @NotNull MarkerLayer layer = registry.layer(plugin, "hidden", "Hidden areas", true);
    final @NotNull Map<String, Set<Long>> synced = new HashMap<>();
    Bukkit.getScheduler()
        .runTaskTimer(
            plugin,
            () -> {
              final @NotNull Map<String, Set<Long>> hidden = limits.hidden();
              if (hidden.size() == synced.size()
                  && hidden.entrySet().stream()
                      .allMatch(entry -> synced.get(entry.getKey()) == entry.getValue())) {
                return;
              }
              synced.clear();
              synced.putAll(hidden);
              final @NotNull List<Marker> markers = new ArrayList<>();
              hidden.forEach((world, chunks) -> markers.addAll(markers(world, chunks)));
              layer.setAll(markers);
            },
            0L,
            SYNC_INTERVAL_TICKS);
  }

  private static @NotNull List<Marker> markers(
      final @NotNull String worldName, final @NotNull Set<Long> chunks) {
    final @Nullable NamespacedKey world = NamespacedKey.fromString(worldName);
    if (world == null) {
      return List.of();
    }
    final @NotNull List<Marker> markers = new ArrayList<>();
    final @NotNull List<List<long[]>> rings = rings(chunks);
    for (int i = 0; i < rings.size(); i++) {
      markers.add(
          Marker.polygon(
                  worldName + "/" + i,
                  world,
                  rings.get(i).stream()
                      .map(corner -> new Vector(corner[0] << 4, 0, corner[1] << 4))
                      .toList(),
                  63,
                  64,
                  "Hidden area")
              .hollowed()
              .withColor(COLOR));
    }
    return markers;
  }

  static @NotNull List<List<long[]>> rings(final @NotNull Set<Long> chunks) {
    final @NotNull Map<Long, Deque<long[]>> edges = new HashMap<>();
    for (final long chunk : chunks) {
      final int x = ChunkCoordinate.keyX(chunk);
      final int z = ChunkCoordinate.keyZ(chunk);
      for (int side = 0; side < SIDES.length; side++) {
        if (chunks.contains(ChunkCoordinate.key(x + SIDES[side][0], z + SIDES[side][1]))) {
          continue;
        }
        final int[] from = CORNERS[side];
        final int[] to = CORNERS[(side + 1) % CORNERS.length];
        edges
            .computeIfAbsent(ChunkCoordinate.key(x + from[0], z + from[1]), _ -> new ArrayDeque<>())
            .add(new long[] {x + to[0], z + to[1]});
      }
    }
    final @NotNull List<List<long[]>> rings = new ArrayList<>();
    while (!edges.isEmpty()) {
      final long start = edges.keySet().iterator().next();
      final @NotNull List<long[]> ring = new ArrayList<>();
      long at = start;
      do {
        final @NotNull Deque<long[]> next = edges.get(at);
        final long @NotNull [] to = next.poll();
        if (next.isEmpty()) {
          edges.remove(at);
        }
        ring.add(new long[] {ChunkCoordinate.keyX(at), ChunkCoordinate.keyZ(at)});
        at = ChunkCoordinate.key((int) to[0], (int) to[1]);
      } while (at != start);
      rings.add(withoutStraightCorners(ring));
    }
    return rings;
  }

  private static @NotNull List<long[]> withoutStraightCorners(final @NotNull List<long[]> ring) {
    final @NotNull List<long[]> kept = new ArrayList<>();
    for (int i = 0; i < ring.size(); i++) {
      final long @NotNull [] before = ring.get((i + ring.size() - 1) % ring.size());
      final long @NotNull [] point = ring.get(i);
      final long @NotNull [] after = ring.get((i + 1) % ring.size());
      if ((point[0] - before[0]) * (after[1] - point[1])
          != (point[1] - before[1]) * (after[0] - point[0])) {
        kept.add(point);
      }
    }
    return kept;
  }
}

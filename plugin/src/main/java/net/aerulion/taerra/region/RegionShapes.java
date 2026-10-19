package net.aerulion.taerra.region;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;

public final class RegionShapes {

  private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
  private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private RegionShapes() {
    super();
  }

  public static @NotNull List<Set<Long>> areas(final @NotNull Collection<Long> chunks) {
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

  public static boolean connected(final @NotNull Collection<Long> chunks) {
    return areas(chunks).size() == 1;
  }

  public static @NotNull List<List<long[]>> rings(final @NotNull Set<Long> chunks) {
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

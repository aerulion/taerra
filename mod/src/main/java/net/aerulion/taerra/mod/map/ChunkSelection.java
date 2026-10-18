package net.aerulion.taerra.mod.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkSelection {

  public static final int MAX_CHUNKS = 16384;
  private static final int CHUNK = 16;
  private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
  private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private final Set<Long> chunks = new LinkedHashSet<>();
  private final int limit;
  private final LongPredicate taken;
  private @Nullable List<int[][]> rings;
  private @Nullable List<Long> detached;
  private @Nullable Stroke stroke;

  public ChunkSelection() {
    this(MAX_CHUNKS, _ -> false);
  }

  public ChunkSelection(final int limit, final @NotNull LongPredicate taken) {
    this.limit = limit;
    this.taken = taken;
  }

  public int limit() {
    return this.limit;
  }

  public void addAll(final @NotNull Collection<Long> chunks) {
    this.chunks.addAll(chunks);
    changed();
  }

  private void changed() {
    this.rings = null;
    this.detached = null;
  }

  public boolean connected() {
    return detached().isEmpty();
  }

  public @NotNull List<Long> detached() {
    if (this.detached == null) {
      final @NotNull Set<Long> left = new HashSet<>(this.chunks);
      final @NotNull List<List<Long>> pieces = new ArrayList<>();
      while (!left.isEmpty()) {
        final long first = left.iterator().next();
        left.remove(first);
        final @NotNull List<Long> piece = new ArrayList<>(List.of(first));
        for (int i = 0; i < piece.size(); i++) {
          final long at = piece.get(i);
          for (final int @NotNull [] side : SIDES) {
            final long next = key(chunkX(at) + side[0], chunkZ(at) + side[1]);
            if (left.remove(next)) {
              piece.add(next);
            }
          }
        }
        pieces.add(piece);
      }
      pieces.sort((a, b) -> b.size() - a.size());
      this.detached = List.copyOf(pieces.stream().skip(1).flatMap(List::stream).toList());
    }
    return this.detached;
  }

  public boolean taken(final int chunkX, final int chunkZ) {
    return this.taken.test(key(chunkX, chunkZ));
  }

  public void begin(final boolean erase) {
    this.stroke = new Stroke(erase, new LinkedHashSet<>(), new LinkedHashSet<>(), rings());
  }

  public boolean stroking() {
    return this.stroke != null;
  }

  public @NotNull List<int[][]> settledRings() {
    return this.stroke == null ? rings() : this.stroke.rings;
  }

  public @NotNull Set<Long> strokeAdded() {
    return this.stroke == null ? Set.of() : Collections.unmodifiableSet(this.stroke.added);
  }

  public @NotNull Set<Long> strokeRemoved() {
    return this.stroke == null ? Set.of() : Collections.unmodifiableSet(this.stroke.removed);
  }

  public boolean containsChunk(final long key) {
    if (this.stroke != null) {
      return this.stroke.removed.contains(key)
          || !this.stroke.added.contains(key) && this.chunks.contains(key);
    }
    return this.chunks.contains(key);
  }

  public boolean erasing() {
    return this.stroke != null && this.stroke.erase;
  }

  public @Nullable Change end() {
    final @Nullable Stroke stroke = this.stroke;
    this.stroke = null;
    return stroke == null || stroke.added.isEmpty() && stroke.removed.isEmpty()
        ? null
        : new Change(Set.copyOf(stroke.added), Set.copyOf(stroke.removed));
  }

  public void cancel() {
    final @Nullable Change change = end();
    if (change != null) {
      revert(change);
    }
  }

  public void revert(final @NotNull Change change) {
    this.chunks.removeAll(change.added());
    this.chunks.addAll(change.removed());
    changed();
  }

  public void replay(final @NotNull Change change) {
    this.chunks.removeAll(change.removed());
    this.chunks.addAll(change.added());
    changed();
  }

  public static @NotNull List<Long> footprint(final int blockX, final int blockZ, final int size) {
    final int chunkX = Math.floorDiv(blockX, CHUNK);
    final int chunkZ = Math.floorDiv(blockZ, CHUNK);
    final int from = -size / 2;
    final @NotNull List<Long> chunks = new ArrayList<>(size * size);
    for (int offsetX = from; offsetX < from + size; offsetX++) {
      for (int offsetZ = from; offsetZ < from + size; offsetZ++) {
        chunks.add(key(chunkX + offsetX, chunkZ + offsetZ));
      }
    }
    return chunks;
  }

  public static long key(final int chunkX, final int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  public static int chunkX(final long key) {
    return (int) (key >> 32);
  }

  public static int chunkZ(final long key) {
    return (int) key;
  }

  public @NotNull Set<Long> chunks() {
    return Collections.unmodifiableSet(this.chunks);
  }

  public int size() {
    return this.chunks.size();
  }

  public boolean isEmpty() {
    return this.chunks.isEmpty();
  }

  public void clear() {
    this.chunks.clear();
    changed();
  }

  public @NotNull List<int[][]> rings() {
    if (this.rings == null) {
      this.rings = trace(this.chunks);
    }
    return this.rings;
  }

  public boolean contains(final int blockX, final int blockZ) {
    return this.chunks.contains(key(Math.floorDiv(blockX, CHUNK), Math.floorDiv(blockZ, CHUNK)));
  }

  public void brush(final int blockX, final int blockZ, final int size) {
    for (final long chunk : footprint(blockX, blockZ, size)) {
      apply(chunkX(chunk), chunkZ(chunk));
    }
  }

  public void box(final int fromX, final int fromZ, final int toX, final int toZ) {
    final int[] bounds = boxChunks(fromX, fromZ, toX, toZ);
    for (int chunkX = bounds[0]; chunkX <= bounds[2]; chunkX++) {
      for (int chunkZ = bounds[1]; chunkZ <= bounds[3]; chunkZ++) {
        apply(chunkX, chunkZ);
      }
    }
  }

  public void lasso(final double @NotNull [][] outline) {
    if (outline.length < 3) {
      return;
    }
    double minX = Double.MAX_VALUE;
    double minZ = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxZ = -Double.MAX_VALUE;
    for (final double @NotNull [] point : outline) {
      minX = Math.min(minX, point[0]);
      minZ = Math.min(minZ, point[1]);
      maxX = Math.max(maxX, point[0]);
      maxZ = Math.max(maxZ, point[1]);
    }
    for (int chunkX = (int) Math.floor(minX / CHUNK); chunkX <= maxX / CHUNK; chunkX++) {
      for (int chunkZ = (int) Math.floor(minZ / CHUNK); chunkZ <= maxZ / CHUNK; chunkZ++) {
        if (Geometry.contains(
            outline, chunkX * CHUNK + CHUNK / 2.0, chunkZ * CHUNK + CHUNK / 2.0)) {
          apply(chunkX, chunkZ);
        }
      }
    }
  }

  public static int @NotNull [] boxChunks(
      final int fromX, final int fromZ, final int toX, final int toZ) {
    return new int[] {
      Math.floorDiv(Math.min(fromX, toX), CHUNK),
      Math.floorDiv(Math.min(fromZ, toZ), CHUNK),
      Math.floorDiv(Math.max(fromX, toX), CHUNK),
      Math.floorDiv(Math.max(fromZ, toZ), CHUNK)
    };
  }

  public static int @NotNull [] touching(final double at) {
    final int chunk = (int) Math.floor(at / CHUNK);
    return at % CHUNK == 0 ? new int[] {chunk - 1, chunk} : new int[] {chunk};
  }

  public static int median(final int @NotNull [] heights, final int count) {
    final int[] sorted = Arrays.copyOf(heights, count);
    Arrays.sort(sorted);
    return sorted[count / 2];
  }

  public static @NotNull List<double[]> corners(final int @NotNull [][] ring) {
    final @NotNull List<double[]> kept = new ArrayList<>();
    for (int i = 0; i < ring.length; i++) {
      final int[] previous = ring[Math.floorMod(i - 1, ring.length)];
      final int[] point = ring[i];
      final int[] next = ring[(i + 1) % ring.length];
      if ((long) (point[0] - previous[0]) * (next[1] - point[1])
          != (long) (point[1] - previous[1]) * (next[0] - point[0])) {
        kept.add(new double[] {point[0], point[1]});
      }
    }
    return kept;
  }

  private void apply(final int chunkX, final int chunkZ) {
    final @Nullable Stroke stroke = this.stroke;
    if (stroke == null) {
      return;
    }
    final long key = key(chunkX, chunkZ);
    if (stroke.erase) {
      if (this.chunks.remove(key)) {
        if (!stroke.added.remove(key)) {
          stroke.removed.add(key);
        }
        changed();
      }
    } else if (this.chunks.size() < this.limit && !this.taken.test(key) && this.chunks.add(key)) {
      if (!stroke.removed.remove(key)) {
        stroke.added.add(key);
      }
      changed();
    }
  }

  public record Change(@NotNull Set<Long> added, @NotNull Set<Long> removed) {}

  private record Stroke(
      boolean erase,
      @NotNull Set<Long> added,
      @NotNull Set<Long> removed,
      @NotNull List<int[][]> rings) {}

  public static @NotNull List<int[][]> outline(final @NotNull Set<Long> chunks) {
    return trace(chunks);
  }

  private static @NotNull List<int[][]> trace(final @NotNull Set<Long> chunks) {
    final @NotNull Map<Long, Deque<Long>> edges = new HashMap<>();
    for (final long chunk : chunks) {
      final int x = chunkX(chunk);
      final int z = chunkZ(chunk);
      for (int side = 0; side < SIDES.length; side++) {
        if (!chunks.contains(key(x + SIDES[side][0], z + SIDES[side][1]))) {
          final int[] from = CORNERS[side];
          final int[] to = CORNERS[(side + 1) % CORNERS.length];
          edges
              .computeIfAbsent(key(x + from[0], z + from[1]), _ -> new ArrayDeque<>())
              .add(key(x + to[0], z + to[1]));
        }
      }
    }
    final @NotNull List<int[][]> rings = new ArrayList<>();
    while (!edges.isEmpty()) {
      final long start = edges.keySet().iterator().next();
      final @NotNull List<int[]> ring = new ArrayList<>();
      long at = start;
      do {
        final @NotNull Deque<Long> next = edges.get(at);
        final long to = next.poll();
        if (next.isEmpty()) {
          edges.remove(at);
        }
        ring.add(new int[] {chunkX(at) * CHUNK, chunkZ(at) * CHUNK});
        at = to;
      } while (at != start);
      rings.add(ring.toArray(int[][]::new));
    }
    return List.copyOf(rings);
  }
}

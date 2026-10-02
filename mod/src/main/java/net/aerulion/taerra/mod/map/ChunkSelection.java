package net.aerulion.taerra.mod.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChunkSelection {

  public static final int MAX_CHUNKS = 16384;
  private static final int CHUNK = 16;
  private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
  private static final int[][] CORNERS = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};

  private final Set<Long> chunks = new LinkedHashSet<>();
  private @Nullable List<int[][]> rings;

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
    this.rings = null;
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

  public void brush(final int blockX, final int blockZ, final boolean erase) {
    apply(Math.floorDiv(blockX, CHUNK), Math.floorDiv(blockZ, CHUNK), erase);
  }

  public void box(
      final int fromX, final int fromZ, final int toX, final int toZ, final boolean erase) {
    final int[] bounds = boxChunks(fromX, fromZ, toX, toZ);
    for (int chunkX = bounds[0]; chunkX <= bounds[2]; chunkX++) {
      for (int chunkZ = bounds[1]; chunkZ <= bounds[3]; chunkZ++) {
        apply(chunkX, chunkZ, erase);
      }
    }
  }

  public void lasso(final double @NotNull [][] outline, final boolean erase) {
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
          apply(chunkX, chunkZ, erase);
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

  private void apply(final int chunkX, final int chunkZ, final boolean erase) {
    final boolean changed =
        erase
            ? this.chunks.remove(key(chunkX, chunkZ))
            : this.chunks.size() < MAX_CHUNKS && this.chunks.add(key(chunkX, chunkZ));
    if (changed) {
      this.rings = null;
    }
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

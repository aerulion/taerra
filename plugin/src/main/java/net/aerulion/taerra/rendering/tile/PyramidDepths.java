package net.aerulion.taerra.rendering.tile;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyramidDepths {

  static final int FIT_TILES = 2;
  static final int AREA_LEVELS = 5;
  static final int AREA_GRAIN = 512;

  private final @NotNull Function<TileCoordinate, List<TileCoordinate>> tilesAt;
  private final @NotNull Map<TileCoordinate, Integer> depths = new ConcurrentHashMap<>();
  private final @NotNull Map<TileCoordinate, int[]> areas = new ConcurrentHashMap<>();

  public PyramidDepths(final @NotNull Function<TileCoordinate, List<TileCoordinate>> tilesAt) {
    super();
    this.tilesAt = tilesAt;
  }

  private static @NotNull TileCoordinate layer(final @NotNull TileCoordinate tile) {
    return tile.at(0, 0, 0);
  }

  private static int span(final @NotNull List<TileCoordinate> tiles) {
    if (tiles.isEmpty()) {
      return 0;
    }
    final @NotNull IntSummaryStatistics x =
        tiles.stream().mapToInt(TileCoordinate::x).summaryStatistics();
    final @NotNull IntSummaryStatistics z =
        tiles.stream().mapToInt(TileCoordinate::z).summaryStatistics();
    return Math.max(x.getMax() - x.getMin(), z.getMax() - z.getMin()) + 1;
  }

  private static int @NotNull [] area(final @NotNull TileCoordinate tile) {
    final int size = TileGeometry.blocksPerTile(tile.zoom());
    return new int[] {
      Math.floorDiv(tile.x() * size, AREA_GRAIN) * AREA_GRAIN,
      Math.floorDiv(tile.z() * size, AREA_GRAIN) * AREA_GRAIN,
      Math.floorDiv((tile.x() + 1) * size - 1, AREA_GRAIN) * AREA_GRAIN + AREA_GRAIN - 1,
      Math.floorDiv((tile.z() + 1) * size - 1, AREA_GRAIN) * AREA_GRAIN + AREA_GRAIN - 1
    };
  }

  private static int @NotNull [] union(final int @NotNull [] a, final int @NotNull [] b) {
    return new int[] {
      Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3])
    };
  }

  public @Nullable Integer known(final @NotNull TileCoordinate tile) {
    return this.depths.get(layer(tile));
  }

  public synchronized @NotNull Measure measure(
      final @NotNull TileCoordinate tile, final boolean mayShrink) {
    final @NotNull TileCoordinate layer = layer(tile);
    int top = TileGeometry.MAX_ZOOM;
    @NotNull List<TileCoordinate> topTiles = this.tilesAt.apply(layer.at(top, 0, 0));
    while (topTiles.isEmpty() && top > 0) {
      top--;
      topTiles = this.tilesAt.apply(layer.at(top, 0, 0));
    }

    int depth = top;
    if (span(topTiles) > FIT_TILES) {
      @NotNull List<TileCoordinate> level = topTiles;
      while (depth < TileGeometry.MAX_ZOOM && span(level) > FIT_TILES) {
        level = level.stream().map(TileHierarchy::parentOf).distinct().toList();
        depth++;
      }
    } else if (mayShrink) {
      while (depth > 0) {
        final @NotNull List<TileCoordinate> below = this.tilesAt.apply(layer.at(depth - 1, 0, 0));
        if (below.isEmpty() || span(below) > FIT_TILES) {
          break;
        }
        depth--;
      }
    }
    this.depths.put(layer, depth);
    if (layer.view() == MapView.TOP_DOWN) {
      this.tilesAt.apply(layer.at(Math.min(top, Math.max(0, depth - AREA_LEVELS)), 0, 0)).stream()
          .map(PyramidDepths::area)
          .reduce(PyramidDepths::union)
          .ifPresentOrElse(area -> this.areas.put(layer, area), () -> this.areas.remove(layer));
    }
    return new Measure(depth, top, topTiles);
  }

  public int @Nullable [] area(final @NotNull String world) {
    return this.areas.get(TileCoordinate.topDown(world, 0, 0, 0));
  }

  public void extend(final @NotNull TileCoordinate tile) {
    if (tile.view() == MapView.TOP_DOWN) {
      this.areas.computeIfPresent(layer(tile), (layer, area) -> union(area, area(tile)));
    }
  }

  public record Measure(int depth, int top, @NotNull List<TileCoordinate> topTiles) {}
}

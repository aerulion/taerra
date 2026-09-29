package net.aerulion.taerra.rendering.tile;

import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyramidDepths {

  static final int FIT_TILES = 2;

  private final @NotNull Function<TileCoordinate, List<TileCoordinate>> tilesAt;
  private final @NotNull Map<TileCoordinate, Integer> depths = new ConcurrentHashMap<>();

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
    return new Measure(depth, top, topTiles);
  }

  public record Measure(int depth, int top, @NotNull List<TileCoordinate> topTiles) {}
}

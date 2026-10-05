package net.aerulion.taerra.rendering.tile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PyramidDepthsTest {

  private static final @NotNull TileCoordinate LAYER = TileCoordinate.topDown("world", 0, 0, 0);

  private static @NotNull PyramidDepths disk(final int firstX, final int lastX, final int top) {
    final @NotNull Set<TileCoordinate> tiles = new HashSet<>();
    for (int x = firstX; x <= lastX; x++) {
      for (@NotNull TileCoordinate tile = TileCoordinate.topDown("world", 0, x, 0);
          tile.zoom() <= top;
          tile = TileHierarchy.parentOf(tile)) {
        tiles.add(tile);
      }
    }
    return new PyramidDepths(
        level ->
            tiles.stream()
                .filter(tile -> tile.zoom() == level.zoom() && tile.view() == level.view())
                .toList());
  }

  @Test
  @DisplayName("a 2k map keeps the four levels it always had")
  void twoThousandBlocksFitAtFour() {
    assertEquals(4, disk(-16, 15, 4).measure(LAYER, true).depth());
  }

  @Test
  @DisplayName("a map that outgrew its pyramid grows it from the top level on disk")
  void grows() {
    final PyramidDepths.@NotNull Measure measure = disk(-64, 63, 4).measure(LAYER, true);
    assertEquals(6, measure.depth());
    assertEquals(4, measure.top());
    assertEquals(8, measure.topTiles().size());
  }

  @Test
  @DisplayName("a map that shrank drops the levels it no longer needs, unless a render holds them")
  void shrinks() {
    assertEquals(1, disk(0, 3, 4).measure(LAYER, true).depth());
    assertEquals(4, disk(0, 3, 4).measure(LAYER, false).depth());
  }

  @Test
  @DisplayName("an empty layer has no levels, and a missing level never tears the pyramid down")
  void emptyLevels() {
    assertEquals(0, disk(0, -1, 0).measure(LAYER, true).depth());
    final @NotNull PyramidDepths onlyTop =
        new PyramidDepths(level -> level.zoom() == 4 ? List.of(level) : List.of());
    assertEquals(4, onlyTop.measure(LAYER, true).depth());
  }

  @Test
  @DisplayName("the rendered area is measured in whole regions and grows with new tiles")
  void area() {
    final @NotNull PyramidDepths depths = disk(-16, 15, 4);
    assertNull(depths.area("world"));
    depths.measure(LAYER, true);
    assertArrayEquals(new int[] {-1024, 0, 1023, 511}, depths.area("world"));
    depths.extend(TileCoordinate.topDown("world", 0, 20, -1));
    assertArrayEquals(new int[] {-1024, -512, 1535, 511}, depths.area("world"));
    depths.extend(TileCoordinate.isometric("world", MapRotation.R0, 0, 500, 500));
    assertArrayEquals(new int[] {-1024, -512, 1535, 511}, depths.area("world"));
  }
}

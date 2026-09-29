package net.aerulion.taerra.rendering.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsoProjectionTest {

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 2, 3, 5, 6, 7, 10})
  @DisplayName("block widths that would put edges between pixels are rejected")
  void invalidBlockWidthsAreRejected(final int blockWidth) {
    assertThrows(IllegalArgumentException.class, () -> IsoProjection.ofBlockWidth(blockWidth));
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32})
  @DisplayName(
      "stepping one block moves an exact whole number of pixels, which is the point of 2:1")
  void stepsAreWholePixels(final int blockWidth) {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(blockWidth);
    final long originX = projection.screenX(0, 0);
    final long originY = projection.screenY(0, 0, 0);

    assertEquals(projection.halfWidth(), projection.screenX(1, 0) - originX);
    assertEquals(projection.quarterWidth(), projection.screenY(1, 0, 0) - originY);
    assertEquals(-projection.halfWidth(), projection.screenX(0, 1) - originX);
    assertEquals(projection.quarterWidth(), projection.screenY(0, 0, 1) - originY);
    assertEquals(0, projection.screenX(0, 0) - originX);
    assertEquals(-projection.halfWidth(), projection.screenY(0, 1, 0) - originY);
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32})
  @DisplayName("one step along x is two pixels across for every one down")
  void horizontalRunIsTwiceTheVerticalDrop(final int blockWidth) {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(blockWidth);
    final long acrossX = projection.screenX(1, 0) - projection.screenX(0, 0);
    final long downY = projection.screenY(1, 0, 0) - projection.screenY(0, 0, 0);
    assertEquals(2 * downY, acrossX);
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32})
  @DisplayName("a cube is exactly as tall as it is wide")
  void cubeIsSquare(final int blockWidth) {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(blockWidth);
    assertEquals(blockWidth, projection.blockScreenHeight());
    assertEquals(blockWidth, projection.lastColumnOffset() - projection.firstColumnOffset() + 1);
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32})
  @DisplayName("the top face and the side face below it are contiguous and never overlap")
  void facesTileEachColumnWithoutGaps(final int blockWidth) {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(blockWidth);
    for (int column = projection.firstColumnOffset();
        column <= projection.lastColumnOffset();
        column++) {
      final int topFirst = projection.topFaceFirstRow(column);
      final int topLast = projection.topFaceLastRow(column);
      final int sideFirst = projection.sideFaceFirstRow(column);
      assertEquals(
          topLast + 1,
          sideFirst,
          "side face must start on the row after the top face ends, column " + column);
      assertTrue(topFirst <= sideFirst, "column " + column);
      assertTrue(projection.faceInset(column) <= projection.quarterWidth(), "column " + column);
    }
  }

  @Test
  @DisplayName("the two outermost columns show only a side face")
  void outermostColumnsHaveNoTopFace() {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(8);
    for (final int column :
        new int[] {projection.firstColumnOffset(), projection.lastColumnOffset()}) {
      assertTrue(
          projection.topFaceLastRow(column) < projection.topFaceFirstRow(column),
          "column " + column + " should have an empty top face");
    }
    assertTrue(projection.topFaceLastRow(0) >= projection.topFaceFirstRow(0));
  }

  @Test
  @DisplayName("columns right of the top vertex show the x face and columns left of it the z face")
  void sideFacesSplitAtTheTopVertex() {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(8);
    assertTrue(projection.isPositiveXFace(0));
    assertTrue(projection.isPositiveXFace(projection.lastColumnOffset()));
    assertFalse(projection.isPositiveXFace(-1));
    assertFalse(projection.isPositiveXFace(projection.firstColumnOffset()));
  }

  @ParameterizedTest
  @ValueSource(ints = {4, 8, 16, 32})
  @DisplayName(
      "the top faces of a flat surface cover every pixel exactly once, with no seams and no overdraw")
  void flatSurfaceTilesTheScreenExactly(final int blockWidth) {

    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(blockWidth);
    final int extent = 40;
    final int halfWidth = projection.halfWidth();
    final int quarterWidth = projection.quarterWidth();

    final int offsetX = extent * halfWidth + blockWidth;
    final int offsetY = blockWidth;
    final int @NotNull [][] coverage =
        new int[2 * offsetX + 1][2 * extent * quarterWidth + 2 * blockWidth];

    for (int blockX = 0; blockX < extent; blockX++) {
      for (int blockZ = 0; blockZ < extent; blockZ++) {
        final long topVertexX = projection.screenX(blockX, blockZ);
        final long topVertexY = projection.screenY(blockX, 1, blockZ);
        for (int column = projection.firstColumnOffset();
            column <= projection.lastColumnOffset();
            column++) {
          for (int row = projection.topFaceFirstRow(column);
              row <= projection.topFaceLastRow(column);
              row++) {
            coverage[(int) topVertexX + column + offsetX][(int) topVertexY + row + offsetY]++;
          }
        }
      }
    }

    final int centerY = extent * quarterWidth + offsetY;
    int checked = 0;
    for (int x = offsetX - 4 * halfWidth; x <= offsetX + 4 * halfWidth; x++) {
      for (int y = centerY - 4 * halfWidth; y <= centerY + 4 * halfWidth; y++) {
        assertEquals(
            1,
            coverage[x][y],
            "pixel "
                + (x - offsetX)
                + ","
                + (y - offsetY)
                + " covered "
                + coverage[x][y]
                + " times at block width "
                + blockWidth);
        checked++;
      }
    }
    assertTrue(checked > 0);
  }

  @Test
  @DisplayName("negative coordinates project without rounding toward zero")
  void negativeCoordinatesProjectConsistently() {
    final @NotNull IsoProjection projection = IsoProjection.ofBlockWidth(8);
    for (int blockX = -3; blockX <= 3; blockX++) {
      for (int blockZ = -3; blockZ <= 3; blockZ++) {
        assertEquals(
            projection.screenX(blockX, blockZ) + projection.halfWidth(),
            projection.screenX(blockX + 1, blockZ));
        assertEquals(
            projection.screenY(blockX, 0, blockZ) + projection.quarterWidth(),
            projection.screenY(blockX + 1, 0, blockZ));
      }
    }
  }
}

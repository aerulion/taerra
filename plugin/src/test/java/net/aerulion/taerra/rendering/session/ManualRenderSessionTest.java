package net.aerulion.taerra.rendering.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ManualRenderSessionTest {

  private static final int DEPTH = 4;

  private static @NotNull List<TileCoordinate> topDown(final int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(x -> TileCoordinate.topDown("world", 0, x, 0))
        .toList();
  }

  private static @NotNull List<TileCoordinate> isometric(final int count) {
    return java.util.stream.IntStream.range(0, count)
        .mapToObj(x -> TileCoordinate.isometric("world", MapRotation.R0, 0, x, 0))
        .toList();
  }

  private static @NotNull ManualRenderSession started(final @NotNull List<TileCoordinate> tiles) {
    final @NotNull ManualRenderSession session =
        ManualRenderSession.scanning("full", "world", tile -> DEPTH);
    session.activate(1, 16, tiles, "planned");
    return session;
  }

  private static @NotNull TileCoordinate topDownAt(final int zoom) {
    return TileCoordinate.topDown("world", zoom, 0, 0);
  }

  @Test
  @DisplayName(
      "every zoom level above the ground belongs to the render, and it only finishes once they are built")
  void finishesOnceZoomLevelsAreBuilt() {
    final @NotNull List<TileCoordinate> tiles = topDown(2);
    final @NotNull ManualRenderSession session = started(tiles);
    assertEquals(
        6, session.snapshot().totalTiles(), "2 ground tiles and one ancestor per zoom level");

    tiles.forEach(session::completeTile);
    assertFalse(session.finishIfDrained());

    for (int zoom = 1; zoom <= DEPTH; zoom++) {
      assertFalse(session.finishIfDrained());
      session.completeTile(topDownAt(zoom));
    }
    assertTrue(session.finishIfDrained());
    assertFalse(session.finishIfDrained(), "only the first call says so");
    assertEquals(6, session.snapshot().completedTiles());
    assertEquals("completed", session.snapshot().phase());
  }

  @Test
  @DisplayName("a zoom tile built while one of its children is still pending does not count yet")
  void zoomTileWaitsForItsChildren() {
    final @NotNull List<TileCoordinate> tiles = topDown(2);
    final @NotNull ManualRenderSession session = started(tiles);

    session.completeTile(tiles.getFirst());
    session.completeTile(topDownAt(1));
    assertEquals(1, session.snapshot().completedTiles(), "its second child is still queued");

    session.completeTile(tiles.get(1));
    session.completeTile(topDownAt(1));
    assertEquals(3, session.snapshot().completedTiles());
  }

  @Test
  @DisplayName("isometric coverage brings its own zoom levels, each counted once")
  void isometricCoverageAddsItsZoomLevels() {
    final @NotNull ManualRenderSession session = started(topDown(2));

    final @NotNull List<TileCoordinate> added = isometric(3);
    assertEquals(added, session.addIsometricCoverage(2, added));
    assertEquals(
        6 + 3 + 4 + 1,
        session.snapshot().totalTiles(),
        "3 ground, x 0-1 at zoom 1, one above that");
    assertTrue(
        session.addIsometricCoverage(0, added).isEmpty(),
        "already known tiles are not added again");
    assertEquals(14, session.snapshot().totalTiles());
  }

  @Test
  @DisplayName(
      "the total is extrapolated from the ground measured so far, so progress never runs backwards")
  void totalIsEstimatedFromMeasuredGround() {
    final @NotNull ManualRenderSession session = started(topDown(10));
    assertEquals(21, session.snapshot().totalTiles(), "10 + 5 + 3 + 2 + 1 top-down tiles");

    session.addIsometricCoverage(2, isometric(6));
    assertTrue(session.snapshot().totalEstimated());
    assertEquals(
        21 + 13 * 10 / 2,
        session.snapshot().totalTiles(),
        "13 isometric tiles per 2 measured, over 10");

    session.addIsometricCoverage(8, isometric(30));
    assertFalse(
        session.snapshot().totalEstimated(), "every top-down tile measured, the total is exact");
    assertEquals(21 + 30 + 15 + 8 + 4 + 2, session.snapshot().totalTiles());
  }

  @Test
  @DisplayName("a canceled render takes no more tiles")
  void canceledSessionRefusesTiles() {
    final @NotNull ManualRenderSession session = started(topDown(2));
    session.cancel("stopped");

    assertTrue(session.addIsometricCoverage(2, isometric(4)).isEmpty());
    assertEquals("canceled", session.snapshot().phase());
  }

  @Test
  @DisplayName("a tile returned to the queue is not counted as done")
  void returnedTileIsNotComplete() {
    final @NotNull List<TileCoordinate> tiles = topDown(2);
    final @NotNull ManualRenderSession session = started(tiles);

    session.markDispatched(tiles.getFirst());
    assertEquals(1, session.snapshot().inFlightTiles());
    session.markReturned(tiles.getFirst());
    assertEquals(0, session.snapshot().inFlightTiles());
    assertEquals(
        6, session.snapshot().queuedTiles(), "both ground tiles and their four zoom levels");

    tiles.forEach(session::completeTile);
    assertEquals(2, session.snapshot().completedTiles());
  }
}

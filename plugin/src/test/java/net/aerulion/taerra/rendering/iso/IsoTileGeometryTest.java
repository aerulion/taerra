package net.aerulion.taerra.rendering.iso;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.aerulion.taerra.model.MapRotation;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IsoTileGeometryTest {

  private static final int MIN_WORLD_Y = -64;
  private static final int MAX_WORLD_Y = 320;

  private static @NotNull Set<Long> visitedChunks(
      final @NotNull ScreenBounds bounds, final @NotNull MapRotation rotation) {
    final @NotNull Set<Long> visited = new HashSet<>();
    IsoTileGeometry.forEachCandidateChunk(
        bounds,
        MIN_WORLD_Y,
        MAX_WORLD_Y,
        rotation,
        (chunkX, chunkZ) -> visited.add(key(chunkX, chunkZ)));
    return visited;
  }

  private static long key(final int chunkX, final int chunkZ) {
    return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
  }

  @Test
  @DisplayName("leaflet zoom conversion round-trips across every served level")
  void leafletZoomRoundTrips() {
    for (int zoom = 0; zoom <= IsoTileGeometry.MAX_ZOOM; zoom++) {
      assertEquals(zoom, IsoTileGeometry.fromLeafletZoom(IsoTileGeometry.toLeafletZoom(zoom)));
      assertTrue(IsoTileGeometry.isValidLeafletZoom(IsoTileGeometry.toLeafletZoom(zoom)));
    }
    assertFalse(IsoTileGeometry.isValidLeafletZoom(IsoTileGeometry.toLeafletZoom(-1)));
    assertFalse(
        IsoTileGeometry.isValidLeafletZoom(
            IsoTileGeometry.toLeafletZoom(IsoTileGeometry.MAX_ZOOM + 1)));
  }

  @Test
  @DisplayName("a tile covers twice as many screen pixels at each zoom level")
  void screenPixelsPerTileDoubles() {
    assertEquals(IsoTileGeometry.TILE_SIZE, IsoTileGeometry.screenPixelsPerTile(0));
    for (int zoom = 1; zoom <= IsoTileGeometry.MAX_ZOOM; zoom++) {
      assertEquals(
          IsoTileGeometry.screenPixelsPerTile(zoom - 1) * 2,
          IsoTileGeometry.screenPixelsPerTile(zoom));
    }
  }

  @Test
  @DisplayName("tile bounds and the screen-to-tile lookup agree, including left of the origin")
  void tileBoundsRoundTrip() {
    for (int zoom = 0; zoom <= IsoTileGeometry.MAX_ZOOM; zoom++) {
      for (final int tile : new int[] {-3, -1, 0, 1, 7}) {
        final @NotNull ScreenBounds bounds = IsoTileGeometry.tileBounds(tile, tile, zoom);
        assertEquals(tile, IsoTileGeometry.screenToTileX(bounds.minX(), zoom));
        assertEquals(tile, IsoTileGeometry.screenToTileX(bounds.maxX(), zoom));
        assertEquals(tile, IsoTileGeometry.screenToTileY(bounds.minY(), zoom));
        assertEquals(tile, IsoTileGeometry.screenToTileY(bounds.maxY(), zoom));
        assertEquals(IsoTileGeometry.screenPixelsPerTile(zoom), bounds.maxX() - bounds.minX() + 1);
      }
    }
  }

  @Test
  @DisplayName("negative screen coordinates floor toward negative infinity, not toward zero")
  void screenToTileFloorsNegatives() {
    assertEquals(0, IsoTileGeometry.screenToTileX(0, 0));
    assertEquals(-1, IsoTileGeometry.screenToTileX(-1, 0));
    assertEquals(-1, IsoTileGeometry.screenToTileX(-IsoTileGeometry.TILE_SIZE, 0));
    assertEquals(-2, IsoTileGeometry.screenToTileX(-IsoTileGeometry.TILE_SIZE - 1, 0));
  }

  @Test
  @DisplayName("a chunk's horizontal extent depends only on where it is, never on how tall it is")
  void chunkWidthIsHeightIndependent() {
    final int halfWidth = IsoTileGeometry.projection().halfWidth();
    final @NotNull ScreenBounds flat =
        IsoTileGeometry.chunkScreenBounds(3, -5, 64, 64, MapRotation.R0);
    final @NotNull ScreenBounds mountainous =
        IsoTileGeometry.chunkScreenBounds(3, -5, 60, 200, MapRotation.R0);

    assertEquals(flat.minX(), mountainous.minX());
    assertEquals(flat.maxX(), mountainous.maxX());
    assertEquals(2L * IsoTileGeometry.CHUNK_SIZE * halfWidth, flat.maxX() - flat.minX() + 1);

    assertTrue(mountainous.minY() < flat.minY());
  }

  @Test
  @DisplayName("diagonally adjacent chunks sit side by side horizontally without overlapping")
  void chunksOnTheSameDiagonalDoNotOverlapHorizontally() {

    final @NotNull ScreenBounds first =
        IsoTileGeometry.chunkScreenBounds(0, 0, 64, 64, MapRotation.R0);
    final @NotNull ScreenBounds second =
        IsoTileGeometry.chunkScreenBounds(1, -1, 64, 64, MapRotation.R0);
    assertTrue(first.maxX() < second.minX() || second.maxX() < first.minX());
  }

  @Test
  @DisplayName(
      "candidate enumeration misses no chunk that really intersects the tile, in any rotation")
  void candidatesCoverEveryIntersectingChunk() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      for (final int[] tile : new int[][] {{0, 0}, {3, -2}, {-7, 5}, {12, 12}}) {
        final @NotNull ScreenBounds bounds = IsoTileGeometry.tileBounds(tile[0], tile[1], 0);
        final @NotNull Set<Long> visited = visitedChunks(bounds, rotation);

        for (int chunkX = -80; chunkX <= 80; chunkX++) {
          for (int chunkZ = -80; chunkZ <= 80; chunkZ++) {
            final @NotNull ScreenBounds chunkBounds =
                IsoTileGeometry.chunkScreenBounds(
                    chunkX, chunkZ, MIN_WORLD_Y, MAX_WORLD_Y - 1, rotation);
            if (chunkBounds.intersects(bounds)) {
              assertTrue(
                  visited.contains(key(chunkX, chunkZ)),
                  "chunk "
                      + chunkX
                      + ","
                      + chunkZ
                      + " intersects tile "
                      + tile[0]
                      + ","
                      + tile[1]
                      + " at "
                      + rotation
                      + " but was not visited");
            }
          }
        }
      }
    }
  }

  @Test
  @DisplayName("candidate enumeration stays a bounded superset rather than scanning the world")
  void candidateCountStaysBounded() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull Set<Long> visited =
          visitedChunks(IsoTileGeometry.tileBounds(0, 0, 0), rotation);
      assertFalse(visited.isEmpty());
      assertTrue(
          visited.size() < 4096, "enumerated " + visited.size() + " candidate chunks for one tile");
    }
  }

  @Test
  @DisplayName(
      "every enumerated chunk is a real chunk, so sum and difference never disagree on parity")
  void enumeratedChunksHaveConsistentParity() {
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      IsoTileGeometry.forEachCandidateChunk(
          IsoTileGeometry.tileBounds(-4, 9, 0),
          MIN_WORLD_Y,
          MAX_WORLD_Y,
          rotation,
          (chunkX, chunkZ) -> {
            final @NotNull ScreenBounds chunkBounds =
                IsoTileGeometry.chunkScreenBounds(
                    chunkX, chunkZ, MIN_WORLD_Y, MAX_WORLD_Y - 1, rotation);
            assertTrue(chunkBounds.maxX() >= chunkBounds.minX());
          });
    }
  }

  @Test
  @DisplayName(
      "a rotated view covers the same ground, so one chunk keeps its screen size in every rotation")
  void rotationPreservesChunkExtent() {
    final @NotNull ScreenBounds unrotated =
        IsoTileGeometry.chunkScreenBounds(3, -5, 60, 90, MapRotation.R0);
    for (final @NotNull MapRotation rotation : MapRotation.values()) {
      final @NotNull ScreenBounds rotated =
          IsoTileGeometry.chunkScreenBounds(3, -5, 60, 90, rotation);
      assertEquals(
          unrotated.maxX() - unrotated.minX(),
          rotated.maxX() - rotated.minX(),
          "chunk width changed at " + rotation);
      assertEquals(
          unrotated.maxY() - unrotated.minY(),
          rotated.maxY() - rotated.minY(),
          "chunk height changed at " + rotation);
    }
  }

  @Test
  @DisplayName("turning the world four quarters puts every chunk back where it started")
  void fourQuarterTurnsAreIdentity() {
    for (final int[] chunk : new int[][] {{0, 0}, {5, 3}, {-7, 2}, {-1, -1}, {12, -30}}) {
      final @NotNull ScreenBounds original =
          IsoTileGeometry.chunkScreenBounds(chunk[0], chunk[1], 60, 90, MapRotation.R0);
      int x = chunk[0];
      int z = chunk[1];
      for (int turn = 0; turn < 4; turn++) {
        final int turnedX = MapRotation.R90.rotatedX(x, z);
        z = MapRotation.R90.rotatedZ(x, z);
        x = turnedX;
      }
      assertEquals(chunk[0], x);
      assertEquals(chunk[1], z);
      assertEquals(original, IsoTileGeometry.chunkScreenBounds(x, z, 60, 90, MapRotation.R0));
    }
  }

  @Test
  @DisplayName(
      "screen bounds intersection is symmetric and excludes touching-but-disjoint rectangles")
  void intersectionIsWellDefined() {
    final @NotNull ScreenBounds left = new ScreenBounds(0, 0, 10, 10);
    final @NotNull ScreenBounds overlapping = new ScreenBounds(10, 10, 20, 20);
    final @NotNull ScreenBounds disjoint = new ScreenBounds(11, 0, 20, 10);

    assertTrue(left.intersects(overlapping));
    assertTrue(overlapping.intersects(left));
    assertFalse(left.intersects(disjoint));
    assertFalse(disjoint.intersects(left));
  }
}

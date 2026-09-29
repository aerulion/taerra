package net.aerulion.taerra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TileGeometryTest {

  @Test
  @DisplayName("leaflet zoom conversion round-trips across every served level")
  void leafletZoomRoundTrips() {
    for (int zoom = 0; zoom <= TileGeometry.MAX_ZOOM; zoom++) {
      assertEquals(zoom, TileGeometry.fromLeafletZoom(TileGeometry.toLeafletZoom(zoom)));
      assertTrue(TileGeometry.isValidLeafletZoom(TileGeometry.toLeafletZoom(zoom)));
    }
    assertFalse(TileGeometry.isValidLeafletZoom(TileGeometry.toLeafletZoom(-1)));
    assertFalse(
        TileGeometry.isValidLeafletZoom(TileGeometry.toLeafletZoom(TileGeometry.MAX_ZOOM + 1)));
  }

  @Test
  @DisplayName("a tile covers twice as many blocks at each zoom level")
  void blocksPerTileDoubles() {
    assertEquals(TileGeometry.TILE_SIZE, TileGeometry.blocksPerTile(0));
    for (int zoom = 1; zoom <= TileGeometry.MAX_ZOOM; zoom++) {
      assertEquals(TileGeometry.blocksPerTile(zoom - 1) * 2, TileGeometry.blocksPerTile(zoom));
    }
  }

  @Test
  @DisplayName("negative block coordinates floor toward negative infinity, not toward zero")
  void blockToTileHandlesNegativeCoordinates() {
    assertEquals(0, TileGeometry.blockToTile(0, 0, 0, "w").x());
    assertEquals(-1, TileGeometry.blockToTile(-1, -1, 0, "w").x());
    assertEquals(-1, TileGeometry.blockToTile(-TileGeometry.TILE_SIZE, 0, 0, "w").x());
    assertEquals(-2, TileGeometry.blockToTile(-TileGeometry.TILE_SIZE - 1, 0, 0, "w").x());
  }

  @Test
  @DisplayName("a region is exactly one tile at the deepest zoom level")
  void regionAlignsWithTopZoomTile() {
    assertEquals(512, TileGeometry.blocksPerTile(3));
  }
}

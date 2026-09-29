package net.aerulion.taerra.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MapViewTest {

  @Test
  @DisplayName(
      "identifiers round-trip, so a persisted queue reloads into the view it was written from")
  void identifiersRoundTrip() {
    for (final @NotNull MapView view : MapView.values()) {
      assertSame(view, MapView.fromId(view.id(), MapView.TOP_DOWN));
    }
  }

  @Test
  @DisplayName("a queue written before the isometric view existed reloads as top-down")
  void unknownIdentifierFallsBack() {
    assertSame(MapView.TOP_DOWN, MapView.fromId(null, MapView.TOP_DOWN));
    assertSame(MapView.TOP_DOWN, MapView.fromId("something-else", MapView.TOP_DOWN));
  }

  @Test
  @DisplayName(
      "each view stores its tiles somewhere else, since the same coordinates mean different things")
  void directoriesAreDistinct() {
    final @NotNull Set<String> directories = new HashSet<>();
    for (final @NotNull MapView view : MapView.values()) {
      assertTrue(directories.add(view.directoryName()), "duplicate directory for " + view);
    }
  }

  @Test
  @DisplayName(
      "the view is part of a tile's identity, so the two views cannot collide in a map or cache")
  void viewSeparatesOtherwiseIdenticalTiles() {
    final @NotNull TileCoordinate topDown = TileCoordinate.topDown("world", 0, 3, 4);
    final @NotNull TileCoordinate isometric =
        TileCoordinate.isometric("world", MapRotation.R0, 0, 3, 4);

    assertNotEquals(topDown, isometric);
    assertEquals(2, new HashSet<>(Set.of(topDown, isometric)).size());
  }

  @Test
  @DisplayName("each view's declared tile size is the one its geometry actually uses")
  void tileSizesMatchTheGeometry() {

    assertEquals(TileGeometry.TILE_SIZE, MapView.TOP_DOWN.tilePixels());
    assertEquals(
        net.aerulion.taerra.rendering.iso.IsoTileGeometry.TILE_SIZE,
        MapView.ISOMETRIC.tilePixels());
    assertNotEquals(MapView.TOP_DOWN.tilePixels(), MapView.ISOMETRIC.tilePixels());
  }

  @Test
  @DisplayName("the factory methods produce the view they name")
  void factoriesTagTheView() {
    assertSame(MapView.TOP_DOWN, TileCoordinate.topDown("w", 1, 0, 0).view());
    assertSame(MapView.ISOMETRIC, TileCoordinate.isometric("w", MapRotation.R0, 1, 0, 0).view());
  }
}

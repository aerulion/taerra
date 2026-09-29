package net.aerulion.taerra.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.LineMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.PointMarker;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkerRegistryTest {

  private static final @NotNull NamespacedKey OVERWORLD = NamespacedKey.minecraft("overworld");
  private static final @NotNull NamespacedKey NETHER = NamespacedKey.minecraft("the_nether");

  private static @NotNull AreaMarker square(
      final @NotNull String id, final @NotNull NamespacedKey world, final int size) {
    return new AreaMarker(
        id,
        world,
        List.of(
            new Vector(0, 0, 0),
            new Vector(size, 0, 0),
            new Vector(size, 0, size),
            new Vector(0, 0, size)),
        60,
        80,
        false,
        id,
        Map.of(),
        Marker.DEFAULT_COLOR);
  }

  @Test
  @DisplayName("only markers that differ announce their world, so a wholesale resync is cheap")
  void setAllAnnouncesOnlyChanges() {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull MarkerLayer layer = registry.layer("regions", "Regions");

    layer.setAll(List.of(square("spawn", OVERWORLD, 16), square("fortress", NETHER, 32)));
    assertEquals(Set.of(OVERWORLD, NETHER), registry.drainChangedWorlds());

    layer.setAll(List.of(square("spawn", OVERWORLD, 16), square("fortress", NETHER, 32)));
    assertTrue(registry.drainChangedWorlds().isEmpty());

    layer.setAll(List.of(square("spawn", OVERWORLD, 20)));
    assertEquals(Set.of(OVERWORLD, NETHER), registry.drainChangedWorlds());
    assertEquals(1, layer.markers().size());

    layer.set(square("spawn", OVERWORLD, 20).withColor(Color.RED));
    assertEquals(Set.of(OVERWORLD), registry.drainChangedWorlds());
  }

  @Test
  @DisplayName("a marker keeps its own copy of the points it was given")
  void markersAreImmutable() {
    final @NotNull Vector corner = new Vector(1, 64, 1);
    final @NotNull PointMarker point =
        new PointMarker("a", OVERWORLD, corner, null, Map.of(), Marker.DEFAULT_COLOR);
    corner.setX(99);
    assertEquals(1, point.position().getX());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LineMarker(
                "l", OVERWORLD, List.of(corner), false, null, Map.of(), Marker.DEFAULT_COLOR));
  }

  @Test
  @DisplayName("removing a layer takes its markers off the map")
  void removeLayerClears() {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    registry.layer("regions", "Regions").set(square("spawn", OVERWORLD, 16));
    registry.drainChangedWorlds();

    registry.removeLayer("regions");
    assertEquals(Set.of(OVERWORLD), registry.drainChangedWorlds());
    assertTrue(registry.layers().isEmpty());
  }
}

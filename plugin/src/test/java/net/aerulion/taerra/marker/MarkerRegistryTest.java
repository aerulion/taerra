package net.aerulion.taerra.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.LineMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.PointMarker;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarkerRegistryTest {

  private static final @NotNull NamespacedKey OVERWORLD = NamespacedKey.minecraft("overworld");
  private static final @NotNull NamespacedKey NETHER = NamespacedKey.minecraft("the_nether");
  private static final @NotNull Plugin TAERRA = TestPlugins.named("Taerra");

  private static @NotNull AreaMarker square(
      final @NotNull String id, final @NotNull NamespacedKey world, final int size) {
    return Marker.polygon(
        id,
        world,
        List.of(
            new Vector(0, 0, 0),
            new Vector(size, 0, 0),
            new Vector(size, 0, size),
            new Vector(0, 0, size)),
        60,
        80,
        id);
  }

  @Test
  @DisplayName("only markers that differ announce their world, so a wholesale resync is cheap")
  void setAllAnnouncesOnlyChanges() {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull MarkerLayer layer = registry.layer(TAERRA, "regions", "Regions");

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
    final @NotNull PointMarker point = Marker.point("a", OVERWORLD, corner, null);
    corner.setX(99);
    assertEquals(1, point.position().getX());
    point.position().setX(99);
    assertEquals(1, point.position().getX());
    final @NotNull LineMarker line =
        Marker.line("l", OVERWORLD, List.of(corner, new Vector(2, 64, 2)), null);
    line.points().getFirst().setX(0);
    assertEquals(99, line.points().getFirst().getX());
    assertThrows(
        IllegalArgumentException.class, () -> Marker.line("l", OVERWORLD, List.of(corner), null));
  }

  @Test
  @DisplayName("removing a layer takes its markers off the map")
  void removeLayerClears() {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    registry.layer(TAERRA, "regions", "Regions").set(square("spawn", OVERWORLD, 16));
    registry.drainChangedWorlds();

    registry.removeLayer("regions");
    assertEquals(Set.of(OVERWORLD), registry.drainChangedWorlds());
    assertTrue(registry.layers().isEmpty());
  }

  @Test
  @DisplayName("a layer belongs to one plugin and goes when that plugin is disabled")
  void layersFollowTheirOwner() {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull Plugin shops = TestPlugins.named("Shops");
    registry.layer(shops, "shops", "Shops").set(square("market", OVERWORLD, 8));
    registry.layer(TAERRA, "regions", "Regions");

    assertEquals(shops, registry.layer(shops, "shops", "Shops").owner());
    assertThrows(IllegalArgumentException.class, () -> registry.layer(TAERRA, "shops", "Not mine"));

    registry.removeLayers(shops);
    assertNull(registry.existingLayer("shops"));
    assertNotNull(registry.existingLayer("regions"));
  }
}

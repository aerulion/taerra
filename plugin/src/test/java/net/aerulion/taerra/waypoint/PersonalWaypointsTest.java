package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.PlayerMarkerLayerImpl;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PersonalWaypointsTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static @NotNull Marker point(final @NotNull String name, final int x) {
    return Marker.point(
            PersonalWaypoints.markerId(name),
            NamespacedKey.minecraft("overworld"),
            new Vector(x + .5, 64, .5),
            name)
        .withColor(Color.fromRGB(0x3AB3DA));
  }

  @Test
  @DisplayName("each player keeps their own waypoints, read back from one file each")
  void keepsWaypointsPerPlayer(final @TempDir Path directory) {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull PersonalWaypoints waypoints =
        new PersonalWaypoints(TestPlugins.named("TAERRA"), registry, directory.toFile());

    assertEquals(PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("Home", 1), null));
    assertEquals(PersonalWaypoints.Result.TAKEN, waypoints.save(ALEX, point("home", 2), null));
    assertEquals(PersonalWaypoints.Result.SAVED, waypoints.save(STEVE, point("Home", 3), null));
    assertEquals(PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("Home", 4), "Home"));

    final @NotNull PlayerMarkerLayerImpl layer = registry.playerLayers().iterator().next();
    assertEquals(
        List.of(new Vector(4.5, 64, .5)),
        layer.markers(ALEX).stream().map(marker -> ((PointMarker) marker).position()).toList());
    assertEquals(0x3AB3DA, layer.markers(STEVE).getFirst().color().asRGB());
    assertEquals(Set.of(ALEX, STEVE), registry.drainChangedPlayers());

    assertTrue(waypoints.remove(ALEX, "HOME"));
    assertFalse(waypoints.remove(ALEX, "Home"));
    assertFalse(new File(directory.toFile(), ALEX + ".yml").exists(), "an empty file is deleted");
  }

  @Test
  @DisplayName("a player can keep only so many waypoints")
  void limitsWaypoints(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints =
        new PersonalWaypoints(
            TestPlugins.named("TAERRA"), new MarkerRegistryImpl(), directory.toFile());
    for (int i = 0; i < PersonalWaypoints.MAX; i++) {
      assertEquals(PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("W" + i, i), null));
    }
    assertEquals(PersonalWaypoints.Result.FULL, waypoints.save(ALEX, point("One more", 0), null));
    assertEquals(
        PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("W0", 9), "W0"), "moving fits");
  }
}

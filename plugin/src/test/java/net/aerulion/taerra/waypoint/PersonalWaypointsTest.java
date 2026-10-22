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
import net.aerulion.taerra.player.PlayerFiles;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  private static @NotNull PersonalWaypoints waypoints(
      final @NotNull Path directory, final @NotNull MarkerRegistryImpl registry) {
    final @NotNull Plugin plugin = TestPlugins.in(directory.toFile());
    return new PersonalWaypoints(plugin, registry, new PlayerFiles(plugin));
  }

  @Test
  @DisplayName("a point keeps its icon through its saved entry")
  void keepsIcons() {
    final @NotNull NamespacedKey icon = new NamespacedKey("towns", "spawn");
    final @NotNull Marker marker = ((PointMarker) point("Home", 1)).withIcon(icon);

    final @NotNull Marker read =
        PersonalWaypoints.read(
                List.of(
                    PersonalWaypoints.entries(List.of(new PersonalWaypoints.Saved(marker, false)))
                        .getFirst()))
            .getFirst()
            .marker();
    assertEquals(icon, ((PointMarker) read).icon());
  }

  @Test
  @DisplayName("each player keeps their own waypoints, read back from one file each")
  void keepsWaypointsPerPlayer(final @TempDir Path directory) {
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, registry);

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
    assertFalse(
        new File(directory.toFile(), "player-data/" + ALEX + ".yml").exists(),
        "an empty file is deleted");
  }

  @Test
  @DisplayName("a player can keep only so many waypoints")
  void limitsWaypoints(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, new MarkerRegistryImpl());
    for (int i = 0; i < PersonalWaypoints.MAX; i++) {
      assertEquals(PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("W" + i, i), null));
    }
    assertEquals(PersonalWaypoints.Result.FULL, waypoints.save(ALEX, point("One more", 0), null));
    assertEquals(
        PersonalWaypoints.Result.SAVED, waypoints.save(ALEX, point("W0", 9), "W0"), "moving fits");
  }

  @Test
  @DisplayName(
      "a hidden waypoint stays in the list but leaves the map, and keeps hiding when edited")
  void hidesWaypoints(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, new MarkerRegistryImpl());
    waypoints.save(ALEX, point("Home", 1), null);
    waypoints.save(ALEX, point("Mine", 2), null);

    assertEquals(1, waypoints.setHidden(ALEX, List.of("home", "Nowhere"), true));
    assertEquals(0, waypoints.setHidden(ALEX, List.of("Home"), true));
    assertEquals(List.of("Mine"), waypoints.markers(ALEX).stream().map(Marker::label).toList());
    assertEquals(2, waypoints.all(ALEX).size());

    waypoints.save(ALEX, point("Home", 5), "Home");
    assertTrue(waypoints.all(ALEX).stream().filter(saved -> saved.hidden()).count() == 1);
    assertEquals(1, waypoints.setHidden(ALEX, List.of("Home"), false));
    assertEquals(2, waypoints.markers(ALEX).size());
  }

  @Test
  @DisplayName("importing adds new names, skips taken ones and stops at the limit, or replaces all")
  void importsWaypoints(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, new MarkerRegistryImpl());
    waypoints.save(ALEX, point("Home", 1), null);

    final PersonalWaypoints.@Nullable Imported added =
        waypoints.importAll(
            ALEX,
            List.of(
                new PersonalWaypoints.Saved(point("home", 7), false),
                new PersonalWaypoints.Saved(point("Farm", 8), true),
                new PersonalWaypoints.Saved(point("FARM", 9), false)),
            false);
    assertEquals(new PersonalWaypoints.Imported(1, 2, 0), added);
    assertEquals(
        List.of(new Vector(1.5, 64, .5), new Vector(8.5, 64, .5)),
        waypoints.all(ALEX).stream()
            .map(saved -> ((PointMarker) saved.marker()).position())
            .toList());
    assertTrue(waypoints.all(ALEX).get(1).hidden());

    final @NotNull List<PersonalWaypoints.Saved> many =
        java.util.stream.IntStream.range(0, PersonalWaypoints.MAX + 5)
            .mapToObj(i -> new PersonalWaypoints.Saved(point("W" + i, i), false))
            .toList();
    assertEquals(
        new PersonalWaypoints.Imported(PersonalWaypoints.MAX - 2, 0, 7),
        waypoints.importAll(ALEX, many, false));
    assertEquals(
        new PersonalWaypoints.Imported(PersonalWaypoints.MAX, 0, 5),
        waypoints.importAll(ALEX, many, true));
    assertFalse(
        waypoints.all(ALEX).stream().anyMatch(saved -> saved.marker().label().equals("Home")));
  }

  @Test
  @DisplayName("a new death point replaces the last one, whatever language named it")
  void replacesTheLastDeath(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, new MarkerRegistryImpl());
    final @NotNull List<String> names = List.of("Last Death", "Letzter Tod");
    waypoints.save(ALEX, point("Home", 1), null);
    waypoints.save(ALEX, point("Letzter Tod", 2), null);

    assertEquals(
        PersonalWaypoints.Result.SAVED, waypoints.replace(ALEX, point("Last Death", 3), names));
    assertEquals(
        List.of("Home", "Last Death"),
        waypoints.all(ALEX).stream().map(saved -> saved.marker().label()).toList());
  }

  @Test
  @DisplayName("a hidden death point stays hidden when the next death replaces it")
  void keepsTheDeathHidden(final @TempDir Path directory) {
    final @NotNull PersonalWaypoints waypoints = waypoints(directory, new MarkerRegistryImpl());
    final @NotNull List<String> names = List.of("Last Death", "Letzter Tod");
    waypoints.replace(ALEX, point("Letzter Tod", 1), names);
    waypoints.setHidden(ALEX, List.of("Letzter Tod"), true);

    waypoints.replace(ALEX, point("Last Death", 2), names);
    assertTrue(waypoints.all(ALEX).getFirst().hidden());
    assertTrue(waypoints.markers(ALEX).isEmpty());
  }
}

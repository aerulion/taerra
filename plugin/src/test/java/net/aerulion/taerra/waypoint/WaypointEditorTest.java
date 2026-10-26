package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.TestPlugins;
import net.aerulion.taerra.player.PlayerFiles;
import net.aerulion.taerra.player.Preferences;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointEditorTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final WaypointEditor.Author AUTHOR =
      new WaypointEditor.Author(ALEX, _ -> true, false);

  @TempDir Path directory;

  private final @NotNull List<Event> fired = new ArrayList<>();
  private PersonalWaypoints personal;
  private WaypointsImpl waypoints;
  private WaypointEditor editor;

  @BeforeEach
  void setUp() {
    final @NotNull Plugin plugin = TestPlugins.in(this.directory.toFile(), this.fired);
    final @NotNull MarkerRegistryImpl registry = new MarkerRegistryImpl();
    final @NotNull TaerraConfig config =
        new TaerraConfig(
            1,
            1,
            1,
            1,
            new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(),
            new CopyOnWriteArrayList<>(),
            new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
    final @NotNull PlayerFiles files = new PlayerFiles(plugin);
    this.personal = new PersonalWaypoints(plugin, registry, files);
    this.waypoints = new WaypointsImpl(plugin, registry, config);
    this.editor =
        new WaypointEditor(plugin, config, this.waypoints, this.personal, new Preferences(files));
  }

  private static @NotNull Marker point(final @NotNull String id, final @NotNull String name) {
    return Marker.point(id, NamespacedKey.minecraft("overworld"), new Vector(.5, 64, .5), name)
        .withColor(Color.fromRGB(0x3AB3DA));
  }

  private static WaypointEditor.@NotNull Draft draft(
      final @NotNull String name, final boolean previousPersonal, final boolean personal) {
    return new WaypointEditor.Draft(
        name,
        0x3AB3DA,
        null,
        false,
        "minecraft:overworld",
        WaypointEditor.Kind.POINT,
        0,
        64,
        0,
        null,
        false,
        null,
        name,
        previousPersonal,
        personal,
        null);
  }

  private WaypointEditor.@NotNull Result move(
      final @NotNull String name, final boolean toPersonal) {
    return this.editor.store(
        point(toPersonal ? PersonalWaypoints.markerId(name) : Waypoints.markerId(name), name),
        draft(name, !toPersonal, toPersonal),
        null,
        AUTHOR);
  }

  @Test
  @DisplayName("a personal waypoint moved to a shared group leaves the player's own list")
  void personalToShared() {
    this.personal.save(ALEX, point(PersonalWaypoints.markerId("Home"), "Home"), null);

    assertEquals(WaypointEditor.Result.SAVED, move("Home", false));
    assertEquals(List.of("Home"), this.waypoints.names());
    assertTrue(this.personal.all(ALEX).isEmpty());
  }

  @Test
  @DisplayName("a shared waypoint moved to the player's own list leaves the shared ones")
  void sharedToPersonal() {
    this.waypoints.add(point(Waypoints.markerId("Base"), "Base"), null);

    assertEquals(WaypointEditor.Result.SAVED, move("Base", true));
    assertTrue(this.waypoints.names().isEmpty());
    assertEquals(1, this.personal.all(ALEX).size());
  }

  @Test
  @DisplayName(
      "a personal waypoint moved to the shared ones is created there and deleted for its owner")
  void moveFiresEvents() {
    this.personal.save(ALEX, point(PersonalWaypoints.markerId("Home"), "Home"), null);
    this.fired.clear();

    assertEquals(WaypointEditor.Result.SAVED, move("Home", false));
    assertEquals(
        List.of("create Home shared", "delete Home " + ALEX),
        PersonalWaypointsTest.describe(this.fired));
  }

  @Test
  @DisplayName("shared waypoints fire one event per change, none for a group move or a no-op")
  void sharedFiresEvents() {
    final @NotNull Marker base = point(Waypoints.markerId("Base"), "Base");
    this.waypoints.add(base, null);
    this.waypoints.add(base, "Builds");
    this.waypoints.add(base.withColor(Color.RED), "Builds");
    this.waypoints.remove("Base");
    this.waypoints.remove("Base");

    assertEquals(
        List.of("create Base shared", "change Base shared", "delete Base shared"),
        PersonalWaypointsTest.describe(this.fired));
  }

  @Test
  @DisplayName("a move onto a taken name keeps the waypoint where it was")
  void takenKeepsSource() {
    this.personal.save(ALEX, point(PersonalWaypoints.markerId("Home"), "Home"), null);
    this.waypoints.add(point(Waypoints.markerId("Home"), "Home"), null);

    assertEquals(WaypointEditor.Result.TAKEN, move("Home", false));
    assertEquals(1, this.personal.all(ALEX).size());
    assertEquals(List.of("Home"), this.waypoints.names());
  }
}

package net.aerulion.taerra.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.api.waypoint.Waypoints.Result;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.marker.MarkerRegistryImpl;
import net.aerulion.taerra.marker.TestPlugins;
import net.aerulion.taerra.player.PlayerFiles;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointsApiImplTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @TempDir Path directory;

  private WaypointsApiImpl api;

  private static @NotNull PointMarker point(final @NotNull String name) {
    return Marker.point(
        "anything", NamespacedKey.minecraft("overworld"), new Vector(.5, 64, .5), name);
  }

  @BeforeEach
  void setUp() {
    final @NotNull Plugin plugin = TestPlugins.in(this.directory.toFile());
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
    this.api =
        new WaypointsApiImpl(
            plugin,
            new WaypointsImpl(plugin, registry, config),
            new PersonalWaypoints(plugin, registry, new PlayerFiles(plugin)));
  }

  @Test
  @DisplayName("a plugin's waypoints get their ids from their names and replace the same name")
  void idsFollowNames() {
    this.api.setShared(point("Spawn"), null);
    this.api.setShared(point("spawn"), null);
    assertEquals(Result.SAVED, this.api.setPersonal(ALEX, point("Quest")));
    assertThrows(IllegalArgumentException.class, () -> this.api.setPersonal(ALEX, point("<b>")));
    assertThrows(IllegalArgumentException.class, () -> this.api.setShared(point("Town"), "  "));

    assertEquals(
        List.of(Waypoints.markerId("spawn")), this.api.shared().stream().map(Marker::id).toList());
    assertEquals(
        List.of(PersonalWaypoints.markerId("Quest")),
        this.api.personal(ALEX).stream().map(Marker::id).toList());
    assertTrue(this.api.removePersonal(ALEX, "QUEST"));
    assertFalse(this.api.removePersonal(ALEX, "Quest"));
    assertTrue(this.api.removeShared("Spawn"));
  }

  @Test
  @DisplayName("a player's personal waypoints stop at the limit")
  void stopsAtLimit() {
    for (int i = 0; i < PersonalWaypoints.MAX; i++) {
      assertEquals(Result.SAVED, this.api.setPersonal(ALEX, point("Point " + i)));
    }
    assertEquals(Result.FULL, this.api.setPersonal(ALEX, point("One more")));
    assertEquals(Result.SAVED, this.api.setPersonal(ALEX, point("Point 0")));
  }
}

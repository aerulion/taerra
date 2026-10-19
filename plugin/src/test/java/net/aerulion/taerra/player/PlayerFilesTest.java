package net.aerulion.taerra.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerFilesTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TOWN = UUID.fromString("5d0c8a52-7f3e-4b8e-9a51-0c3f6f1d2e47");

  @TempDir Path folder;

  @Test
  @DisplayName("personal waypoints from before move into player-data unchanged")
  void importsPersonalWaypoints() throws IOException {
    final @NotNull Path legacy = this.folder.resolve("personal-waypoints");
    Files.createDirectories(legacy);
    Files.writeString(
        legacy.resolve(ALEX + ".yml"),
        """
        waypoints:
        - name: Home
          world: minecraft:overworld
          x: 1.5
          y: 64.0
          z: 0.5
          color: '#3AB3DA'
          hidden: true
        """);
    Files.writeString(legacy.resolve(ALEX + ".yml.42.tmp"), "half a save");

    final @NotNull PlayerFiles files = new PlayerFiles(TestPlugins.in(this.folder.toFile()));

    assertFalse(Files.exists(legacy));
    assertTrue(Files.exists(this.folder.resolve("player-data").resolve(ALEX + ".yml")));
    assertEquals(1, files.get(ALEX).waypoints().size());
    assertEquals("Home", files.get(ALEX).waypoints().getFirst().marker().label());
    assertTrue(files.get(ALEX).waypoints().getFirst().hidden());
  }

  @Test
  @DisplayName("discoveries are written beside the waypoints and read back after a restart")
  void keepsDiscoveries() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    new PlayerFiles(plugin).update(ALEX, data -> data.withDiscoveries(Map.of(TOWN, 1234L)));

    assertEquals(Map.of(TOWN, 1234L), new PlayerFiles(plugin).get(ALEX).discoveries());
  }

  @Test
  @DisplayName("navigation and layer choices are read back after a restart")
  void keepsPreferences() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    final @NotNull Preferences preferences = new Preferences(new PlayerFiles(plugin));
    final @NotNull Navigation home =
        new Navigation("Home", "minecraft:overworld", 1.5, null, -3.5, "#3AB3DA", null);
    preferences.navigate(ALEX, home);
    preferences.changeLayers(
        ALEX, List.of("chunks", "waypoints.Shops"), List.of("players"), List.of());
    final @NotNull Map<String, Boolean> layers = Map.of("players", false, "waypoints.Shops", true);

    assertEquals(layers, preferences.changeLayers(ALEX, List.of(), List.of(), List.of("chunks")));
    assertNull(preferences.changeLayers(ALEX, List.of(""), List.of(), List.of()));

    final @NotNull PlayerData data = new PlayerFiles(plugin).get(ALEX);
    assertEquals(home, data.navigation());
    assertEquals(layers, data.layers());
  }
}

package net.aerulion.taerra.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NavigationImplTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @TempDir Path folder;

  @Test
  @DisplayName("a plugin's target is saved and sent to the player, a bad label refused")
  void setsAndClears() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    final @NotNull Preferences preferences = new Preferences(new PlayerFiles(plugin));
    final @NotNull List<Navigation> sent = new ArrayList<>();
    preferences.onNavigation((player, target) -> sent.add(target));
    final @NotNull NavigationImpl navigation = new NavigationImpl(preferences);
    final @NotNull World world = TestPlugins.world(NamespacedKey.minecraft("overworld"));

    navigation.set(ALEX, "Quest: the tower", new Location(world, 10.5, 70, -3.5), Color.RED);
    final @NotNull Navigation expected =
        new Navigation(
            "Quest: the tower", "minecraft:overworld", 10.5, 70.0, -3.5, "#ff0000", null);
    assertEquals(expected, preferences.navigation(ALEX));

    assertThrows(
        IllegalArgumentException.class,
        () -> navigation.set(ALEX, "<b>", new Location(world, 0, 0, 0), Color.RED));
    navigation.clear(ALEX);
    assertNull(preferences.navigation(ALEX));
    assertEquals(2, sent.size());
    assertEquals(expected, sent.getFirst());
  }
}

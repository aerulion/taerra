package net.aerulion.taerra.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.marker.TestPlugins;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaerraApiImplTest {

  @TempDir Path folder;

  @Test
  @DisplayName("map links open the public address on a world or a block, as the web map reads them")
  void mapLinks() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    plugin.getConfig().set("web.public-url", "https://map.example.com/");
    final @NotNull TaerraApiImpl api =
        new TaerraApiImpl(
            plugin, null, null, null, null, null, null, new WebSettings(plugin), null);
    final @NotNull World nether = TestPlugins.world(NamespacedKey.minecraft("the_nether"));

    assertEquals("https://map.example.com", api.mapUrl());
    assertEquals("https://map.example.com/#world=minecraft%3Athe_nether", api.mapUrl(nether));
    assertEquals(
        "https://map.example.com/#world=minecraft%3Athe_nether&x=-13&z=7&y=64",
        api.mapUrl(new Location(nether, -12.5, 64.2, 7.9)));
  }
}

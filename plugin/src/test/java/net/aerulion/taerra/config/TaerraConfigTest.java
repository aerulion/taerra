package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaerraConfigTest {

  private static final long GIB = 1L << 30;

  @Test
  @DisplayName("caches take a share of the heap, within their floor and ceiling")
  void cacheEntriesFollowTheHeap() {
    assertEquals(128, TaerraConfig.cacheEntries(2 * GIB, 64, 256L << 10, 1, 50_000));
    assertEquals(2207, TaerraConfig.cacheEntries(2 * GIB, 50, 19L << 10, 256, 131_072));
    assertEquals(256, TaerraConfig.cacheEntries(GIB / 8, 50, 19L << 10, 256, 131_072));
    assertEquals(50_000, TaerraConfig.cacheEntries(Long.MAX_VALUE, 10, 256L << 10, 1, 50_000));
  }

  @Test
  @DisplayName("0 sizes a cache automatically, a set size is held to its limit")
  void configuredCacheSizes() {
    assertEquals(300, TaerraConfig.cacheSize(0, 300, 256, 4000));
    assertEquals(1000, TaerraConfig.cacheSize(1000, 300, 256, 4000));
    assertEquals(4000, TaerraConfig.cacheSize(100_000, 300, 256, 4000));
    assertEquals(256, TaerraConfig.cacheSize(10, 300, 256, 4000));
  }

  @Test
  @DisplayName("layer icons are read back from config.yml as they were written")
  void layerIconsRoundTrip() throws InvalidConfigurationException {
    final Map<String, String> icons =
        Map.of("waypoints.shops", "taerra:shop", "towny", "towny:spawn");
    final YamlConfiguration written = new YamlConfiguration();
    written.set("markers.layer-icons", TaerraConfig.layerIconEntries(icons));
    final YamlConfiguration read = new YamlConfiguration();
    read.loadFromString(written.saveToString());

    assertEquals(icons, TaerraConfig.layerIcons(read.getMapList("markers.layer-icons")));
  }

  @Test
  @DisplayName("clearing a layer's icon falls back to the default, past a built-in layer icon too")
  void clearedLayerIcons() {
    final YamlConfiguration file = new YamlConfiguration();
    final Plugin plugin =
        (Plugin)
            Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getConfig" -> file;
                      case "saveConfig" -> null;
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    final TaerraConfig config =
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

    assertEquals("taerra:star", config.layerIcon("spawn"));
    config.setLayerIcon(plugin, "spawn", "");
    config.setLayerIcon(plugin, "waypoints.shops", "taerra:shop");
    config.setLayerIcon(plugin, "waypoints.shops", "");

    assertEquals("", config.layerIcon("spawn"));
    assertNull(config.layerIcon("waypoints.shops"));
    assertEquals(
        Map.of("spawn", ""), TaerraConfig.layerIcons(file.getMapList("markers.layer-icons")));
  }
}

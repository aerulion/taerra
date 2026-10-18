package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapRegionsTest {

  private static final String WORLD = "minecraft:overworld";

  @TempDir Path folder;

  private @NotNull Plugin plugin() {
    final @NotNull File dataFolder = this.folder.toFile();
    return (Plugin)
        Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "getDataFolder" -> dataFolder;
                  case "getLogger" -> Logger.getLogger("test");
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  @Test
  @DisplayName("hidden chunks in limits.yml become one region per connected group and style")
  void migratesHiddenChunks() throws IOException {
    Files.writeString(
        this.folder.resolve("limits.yml"),
        """
        hidden:
        - world: minecraft:overworld
          chunks: ['0,0', '1,0', '5,5', '9,9', '9,10']
        teased:
        - world: minecraft:overworld
          chunks: ['9,9', '9,10']
        """);

    final @NotNull MapRegions regions = new MapRegions(plugin());

    assertEquals(3, regions.in(WORLD).size());
    final MapRegion veiled = regions.at(WORLD, 9, 10);
    assertNotNull(veiled);
    assertEquals(RegionFog.VEIL, veiled.fog());
    assertEquals(2, veiled.chunks().size());
    assertEquals(RegionFog.HIDE, regions.at(WORLD, 1, 0).fog());
    assertFalse(regions.at(WORLD, 5, 5).discoverable());
    assertTrue(Files.exists(this.folder.resolve("regions.yml")));
  }

  @Test
  @DisplayName("regions survive a restart with their ids, and ids are never handed out twice")
  void roundTrips() {
    final @NotNull MapRegions regions = new MapRegions(plugin());
    final int first = regions.reserveId();
    regions.put(
        MapRegion.of(
            first,
            WORLD,
            "Town",
            RegionFog.HIDE,
            true,
            new LongOpenHashSet(Set.of(ChunkCoordinate.key(2, 3)))));
    final int second = regions.reserveId();
    regions.put(
        MapRegion.of(
            second,
            WORLD,
            "Gone",
            RegionFog.VEIL,
            false,
            new LongOpenHashSet(Set.of(ChunkCoordinate.key(3, 3)))));
    regions.remove(second);

    final @NotNull MapRegions reloaded = new MapRegions(plugin());

    assertEquals("Town", reloaded.at(WORLD, 2, 3).name());
    assertTrue(reloaded.at(WORLD, 2, 3).discoverable());
    assertNull(reloaded.at(WORLD, 3, 3));
    assertTrue(reloaded.reserveId() > second);
    assertEquals(List.of(), reloaded.in("minecraft:the_nether"));
  }
}

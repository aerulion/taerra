package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.UUID;
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
  @DisplayName("regions survive a restart with their ids and the time of their last reset")
  void roundTrips() {
    final @NotNull MapRegions regions = new MapRegions(plugin());
    final @NotNull UUID town = UUID.randomUUID();
    final @NotNull UUID gone = UUID.randomUUID();
    regions.put(region(town, "Town", RegionFog.HIDE, ChunkCoordinate.key(2, 3)).withReset(1234L));
    regions.put(region(gone, "Gone", RegionFog.VEIL, ChunkCoordinate.key(3, 3)));
    regions.remove(gone);

    final @NotNull MapRegions reloaded = new MapRegions(plugin());

    assertEquals(town, reloaded.at(WORLD, 2, 3).id());
    assertEquals("Town", reloaded.at(WORLD, 2, 3).name());
    assertEquals(1234L, reloaded.at(WORLD, 2, 3).reset());
    assertTrue(reloaded.at(WORLD, 2, 3).discoverable());
    assertNull(reloaded.at(WORLD, 3, 3));
    assertNull(reloaded.get(gone));
    assertEquals(List.of(), reloaded.in("minecraft:the_nether"));
  }

  @Test
  @DisplayName("fog reaches as far from a region's own chunks as it spills, not across its bounds")
  void reachFollowsChunks() {
    final @NotNull MapRegions regions = new MapRegions(plugin());
    final @NotNull LongOpenHashSet chunks = new LongOpenHashSet();
    for (int step = 0; step < 40; step++) {
      chunks.add(ChunkCoordinate.key(step, 0));
      chunks.add(ChunkCoordinate.key(0, step));
    }
    final @NotNull UUID id = UUID.randomUUID();
    regions.put(MapRegion.of(id, WORLD, "", 0, RegionFog.VEIL, true, 0L, chunks));
    regions.put(region(UUID.randomUUID(), "", RegionFog.NONE, ChunkCoordinate.key(100, 100)));

    assertArrayEquals(new UUID[] {id}, regions.foggedNear(WORLD, 20, 20 - 12));
    assertArrayEquals(new UUID[] {id}, regions.foggedNear(WORLD, 47, -8));
    assertArrayEquals(new UUID[0], regions.foggedNear(WORLD, 20, 20));
    assertArrayEquals(new UUID[0], regions.foggedNear(WORLD, 48, 0));
    assertArrayEquals(new UUID[0], regions.foggedClose(WORLD, 20, 20 - 12));
    assertArrayEquals(new UUID[] {id}, regions.foggedClose(WORLD, 5, 5));
    assertTrue(regions.anyFogged(WORLD));
    assertFalse(regions.anyFogged("minecraft:the_nether"));
    assertEquals(id, regions.indexed(regions.index(id)).id());
  }

  static @NotNull MapRegion region(
      final @NotNull UUID id,
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final long... chunks) {
    return MapRegion.of(id, WORLD, name, 0, fog, true, 0L, new LongOpenHashSet(chunks));
  }
}

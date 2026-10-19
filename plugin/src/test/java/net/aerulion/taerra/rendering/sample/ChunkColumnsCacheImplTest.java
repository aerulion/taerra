package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionFog;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChunkColumnsCacheImplTest {

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

  private static @NotNull ChunkColumnsCacheImpl cache(
      final @NotNull Plugin plugin, final @NotNull MapRegions regions) {
    return new ChunkColumnsCacheImpl(
        plugin,
        256,
        _ -> {
          throw new UnsupportedOperationException();
        },
        new WorldLimits(plugin),
        regions);
  }

  @Test
  @DisplayName("a spill layer never carries another region's own fog")
  void spillLeavesOtherRegionsBare() {
    final @NotNull Plugin plugin = plugin();
    final @NotNull MapRegions regions = new MapRegions(plugin);
    final @NotNull UUID own = UUID.randomUUID();
    regions.put(region(own, 0, 0));
    regions.put(region(UUID.randomUUID(), 100, 0));
    regions.put(region(UUID.randomUUID(), 3, 0));
    final @NotNull ChunkColumnsCacheImpl cache = cache(plugin, regions);
    final @NotNull FogView spill = FogView.spill(own);

    assertEquals(ChunkColumnsCacheImpl.SKY, cache.variant(WORLD, 0, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.BARE, cache.variant(WORLD, 100, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.PLAIN, cache.variant(WORLD, 50, 0, spill));
    assertEquals(regions.index(own), cache.variant(WORLD, 2, 0, spill));
    assertEquals(regions.index(own), cache.variant(WORLD, 3, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.FOGGED, cache.variant(WORLD, 100, 0, FogView.BASE));
  }

  @Test
  @DisplayName("new ground redraws the fog within two chunks of it, and nothing without fog")
  void groundReachesFogNearby() {
    final @NotNull Plugin plugin = plugin();
    final @NotNull MapRegions regions = new MapRegions(plugin);
    final @NotNull ChunkColumnsCacheImpl cache = cache(plugin, regions);
    final @NotNull Set<Long> edge = Set.of(ChunkCoordinate.key(10, 0));

    assertEquals(Set.of(), cache.groundDependents(WORLD, edge));

    regions.put(region(UUID.randomUUID(), 0, 0));
    final @NotNull Set<Long> fogged = new HashSet<>();
    for (int chunkZ = -2; chunkZ <= 2; chunkZ++) {
      fogged.add(ChunkCoordinate.key(8, chunkZ));
    }

    assertEquals(fogged, cache.groundDependents(WORLD, edge));
    assertEquals(Set.of(), cache.groundDependents(WORLD, Set.of(ChunkCoordinate.key(11, 0))));
  }

  private static @NotNull MapRegion region(
      final @NotNull UUID id, final int chunkX, final int chunkZ) {
    return MapRegion.of(
        id,
        WORLD,
        "",
        0,
        RegionFog.VEIL,
        true,
        0L,
        new LongOpenHashSet(Set.of(ChunkCoordinate.key(chunkX, chunkZ))));
  }
}

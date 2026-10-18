package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Set;
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

  @Test
  @DisplayName("a spill layer never carries another region's own fog")
  void spillLeavesOtherRegionsBare() {
    final @NotNull File dataFolder = this.folder.toFile();
    final @NotNull Plugin plugin =
        (Plugin)
            Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[] {Plugin.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getDataFolder" -> dataFolder;
                      case "getLogger" -> Logger.getLogger("test");
                      default -> throw new UnsupportedOperationException(method.getName());
                    });
    final @NotNull MapRegions regions = new MapRegions(plugin);
    regions.put(region(regions.reserveId(), 0, 0));
    final int far = regions.reserveId();
    regions.put(region(far, 100, 0));
    final int near = regions.reserveId();
    regions.put(region(near, 3, 0));
    final @NotNull ChunkColumnsCacheImpl cache =
        new ChunkColumnsCacheImpl(
            plugin,
            256,
            _ -> {
              throw new UnsupportedOperationException();
            },
            new WorldLimits(plugin),
            regions);
    final @NotNull FogView spill = FogView.spill(1);

    assertEquals(ChunkColumnsCacheImpl.SKY, cache.variant(WORLD, 0, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.BARE, cache.variant(WORLD, 100, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.PLAIN, cache.variant(WORLD, 50, 0, spill));
    assertEquals(1, cache.variant(WORLD, 2, 0, spill));
    assertEquals(1, cache.variant(WORLD, 3, 0, spill));
    assertEquals(ChunkColumnsCacheImpl.FOGGED, cache.variant(WORLD, 100, 0, FogView.BASE));
  }

  private static @NotNull MapRegion region(final int id, final int chunkX, final int chunkZ) {
    return MapRegion.of(
        id,
        WORLD,
        "",
        RegionFog.VEIL,
        true,
        new LongOpenHashSet(Set.of(ChunkCoordinate.key(chunkX, chunkZ))));
  }
}

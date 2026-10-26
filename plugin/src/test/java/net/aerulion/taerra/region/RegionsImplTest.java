package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import net.aerulion.taerra.api.region.Region;
import net.aerulion.taerra.marker.TestPlugins;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.player.PlayerFiles;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionsImplTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @TempDir Path folder;

  @Test
  @DisplayName("a discovery counts until the region is reset, asked with any snapshot of it")
  void discoveries() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    final @NotNull MapRegions regions = new MapRegions(plugin);
    final @NotNull RegionDiscoveries discoveries =
        new RegionDiscoveries(new PlayerFiles(plugin), regions);
    final @NotNull RegionsImpl api = new RegionsImpl(regions, discoveries);
    final @NotNull MapRegion town =
        MapRegionsTest.region(UUID.randomUUID(), "Town", RegionFog.HIDE, ChunkCoordinate.key(0, 0));
    regions.put(town);

    final @Nullable Region found = api.get(town.id());
    assertNotNull(found);
    assertEquals(1, api.all().size());
    assertFalse(api.discovered(ALEX, found));

    discoveries.discover(ALEX, town);
    final @Nullable Instant at = api.discoveredAt(ALEX, found);
    assertNotNull(at);
    assertTrue(api.discovered(ALEX, found));

    regions.put(town.withReset(at.toEpochMilli()));
    assertFalse(api.discovered(ALEX, found));

    regions.remove(town.id());
    assertNull(api.get(town.id()));
    assertNull(api.discoveredAt(ALEX, found));
    assertTrue(api.all().isEmpty());
  }
}

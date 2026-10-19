package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.UUID;
import net.aerulion.taerra.marker.TestPlugins;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.player.PlayerFiles;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionDiscoveriesTest {

  private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000a");

  @TempDir Path folder;

  @Test
  @DisplayName("a reset forgets every discovery made before it, and the next visit counts again")
  void resetForgetsEarlierDiscoveries() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    final @NotNull MapRegions regions = new MapRegions(plugin);
    final @NotNull RegionDiscoveries discoveries =
        new RegionDiscoveries(new PlayerFiles(plugin), regions);
    final @NotNull MapRegion town =
        MapRegionsTest.region(UUID.randomUUID(), "Town", RegionFog.HIDE, ChunkCoordinate.key(0, 0));
    regions.put(town);

    assertTrue(discoveries.discover(ALEX, town));
    assertFalse(discoveries.discover(ALEX, town));
    final long first = discoveries.discoveredAt(ALEX, town);
    assertTrue(first > 0L);

    final @NotNull MapRegion reset = town.withReset(first);
    regions.put(reset);
    assertEquals(0L, discoveries.discoveredAt(ALEX, reset));
    assertTrue(discoveries.discover(ALEX, reset));
    assertTrue(discoveries.discoveredAt(ALEX, reset) > first);

    assertEquals(
        discoveries.discoveredAt(ALEX, reset),
        new RegionDiscoveries(new PlayerFiles(plugin), regions).discoveredAt(ALEX, reset));
  }

  @Test
  @DisplayName("discoveries of a deleted region are dropped the next time the player discovers")
  void dropsDeletedRegions() {
    final @NotNull Plugin plugin = TestPlugins.in(this.folder.toFile());
    final @NotNull MapRegions regions = new MapRegions(plugin);
    final @NotNull PlayerFiles files = new PlayerFiles(plugin);
    final @NotNull RegionDiscoveries discoveries = new RegionDiscoveries(files, regions);
    final @NotNull MapRegion gone =
        MapRegionsTest.region(UUID.randomUUID(), "Gone", RegionFog.HIDE, ChunkCoordinate.key(0, 0));
    final @NotNull MapRegion town =
        MapRegionsTest.region(UUID.randomUUID(), "Town", RegionFog.VEIL, ChunkCoordinate.key(5, 5));
    regions.put(gone);
    regions.put(town);
    discoveries.discover(ALEX, gone);
    regions.remove(gone.id());

    discoveries.discover(ALEX, town);

    assertEquals(java.util.Set.of(town.id()), files.get(ALEX).discoveries().keySet());
  }
}

package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.aerulion.taerra.api.region.Region;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionImplTest {

  @Test
  @DisplayName("every fog, the world and the chunks reach the API as they are")
  void mapsToApi() {
    for (final @NotNull RegionFog fog : RegionFog.values()) {
      final @NotNull Region region =
          new RegionImpl(
              MapRegionsTest.region(UUID.randomUUID(), "Town", fog, ChunkCoordinate.key(2, 3)));
      assertEquals(fog.name(), region.fog().name());
      assertEquals(NamespacedKey.minecraft("overworld"), region.world());
      assertTrue(region.contains(2, 3));
      assertFalse(region.contains(3, 2));
    }
  }
}

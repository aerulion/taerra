package net.aerulion.taerra.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.rendering.style.Fog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RenderServiceTest {

  @Test
  @DisplayName("chunks land in the region file that holds them, negative ones too")
  void regions() {
    assertEquals(
        Set.of(ChunkCoordinate.key(0, 0), ChunkCoordinate.key(-1, -1)),
        RenderService.containing(
            Set.of(
                ChunkCoordinate.key(0, 0),
                ChunkCoordinate.key(31, 31),
                ChunkCoordinate.key(-1, -1),
                ChunkCoordinate.key(-32, -32)),
            RenderService.REGION_SIZE_BLOCKS));
    assertEquals(
        Set.of(ChunkCoordinate.key(1, -2)),
        RenderService.containing(
            Set.of(ChunkCoordinate.key(32, -33)), RenderService.REGION_SIZE_BLOCKS));
  }

  @Test
  @DisplayName("a region change re-renders the fog it spills onto its neighbours")
  void fogReach() {
    final Set<Long> affected = RenderService.withFogReach(Set.of(ChunkCoordinate.key(0, 0)));
    final int side = 2 * Fog.REACH_CHUNKS + 1;
    assertEquals(side * side, affected.size());
    assertTrue(affected.contains(ChunkCoordinate.key(-Fog.REACH_CHUNKS, Fog.REACH_CHUNKS)));
    assertTrue(!affected.contains(ChunkCoordinate.key(Fog.REACH_CHUNKS + 1, 0)));
  }
}

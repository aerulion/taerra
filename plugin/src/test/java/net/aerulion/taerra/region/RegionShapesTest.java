package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionShapesTest {

  @Test
  @DisplayName("an L of chunks is outlined by one ring with only its six corners")
  void lShapeIsOneRing() {
    final @NotNull List<List<long[]>> rings =
        RegionShapes.rings(
            Set.of(
                ChunkCoordinate.key(0, 0), ChunkCoordinate.key(1, 0), ChunkCoordinate.key(0, 1)));

    assertEquals(1, rings.size());
    assertEquals(6, rings.getFirst().size());
  }

  @Test
  @DisplayName("a ring of chunks around a revealed one gets the outer outline first, then the hole")
  void holeGetsItsOwnRing() {
    final @NotNull Set<Long> chunks = new HashSet<>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x != 0 || z != 0) {
          chunks.add(ChunkCoordinate.key(x, z));
        }
      }
    }

    final @NotNull List<List<long[]>> rings = RegionShapes.rings(chunks);

    assertEquals(2, rings.size());
    assertEquals(4, rings.get(0).size());
    assertEquals(4, rings.get(1).size());
    assertTrue(rings.get(0).stream().anyMatch(corner -> corner[0] == -1 && corner[1] == -1));
    assertTrue(rings.get(1).stream().anyMatch(corner -> corner[0] == 0 && corner[1] == 0));
  }

  @Test
  @DisplayName("chunks touching at a side share an area, chunks touching at a corner do not")
  void areasJoinOnlyAtSides() {
    final @NotNull List<Set<Long>> areas =
        RegionShapes.areas(
            Set.of(
                ChunkCoordinate.key(0, 0),
                ChunkCoordinate.key(1, 0),
                ChunkCoordinate.key(2, 1),
                ChunkCoordinate.key(-5, -5)));

    assertEquals(3, areas.size());
    assertEquals(
        Set.of(ChunkCoordinate.key(0, 0), ChunkCoordinate.key(1, 0)),
        areas.stream().filter(area -> area.size() == 2).findFirst().orElseThrow());
  }
}

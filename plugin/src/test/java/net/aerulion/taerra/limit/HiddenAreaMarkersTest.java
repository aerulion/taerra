package net.aerulion.taerra.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HiddenAreaMarkersTest {

  @Test
  @DisplayName("an L of chunks is outlined by one ring with only its six corners")
  void lShapeIsOneRing() {
    final @NotNull List<List<long[]>> rings =
        HiddenAreaMarkers.rings(
            Set.of(
                ChunkCoordinate.key(0, 0), ChunkCoordinate.key(1, 0), ChunkCoordinate.key(0, 1)));

    assertEquals(1, rings.size());
    assertEquals(6, rings.getFirst().size());
  }

  @Test
  @DisplayName("a ring of chunks around a revealed one gets an outer and an inner outline")
  void holeGetsItsOwnRing() {
    final @NotNull Set<Long> chunks = new HashSet<>();
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (x != 0 || z != 0) {
          chunks.add(ChunkCoordinate.key(x, z));
        }
      }
    }

    final @NotNull List<List<long[]>> rings = HiddenAreaMarkers.rings(chunks);

    assertEquals(2, rings.size());
    assertEquals(4, rings.get(0).size());
    assertEquals(4, rings.get(1).size());
  }
}

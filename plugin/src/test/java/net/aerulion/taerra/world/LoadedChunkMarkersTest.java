package net.aerulion.taerra.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoadedChunkMarkersTest {

  @Test
  @DisplayName("chunks touching at a side share an area, chunks touching at a corner do not")
  void areasJoinOnlyAtSides() {
    final @NotNull List<Set<Long>> areas =
        LoadedChunkMarkers.areas(
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

package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Set;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionEditorTest {

  private static @NotNull MapRegion region(final @NotNull RegionFog fog, final long... chunks) {
    return MapRegion.of(1, "w", "Town", fog, true, new LongOpenHashSet(chunks));
  }

  @Test
  @DisplayName("growing a fogged region renders only the chunks that changed")
  void rendersOnlyChanges() {
    final long a = ChunkCoordinate.key(0, 0);
    final long b = ChunkCoordinate.key(1, 0);
    final long c = ChunkCoordinate.key(2, 0);
    assertEquals(
        Set.of(a, c),
        RegionEditor.rendered(region(RegionFog.HIDE, a, b), region(RegionFog.HIDE, b, c)));
  }

  @Test
  @DisplayName("changing the fog renders the whole region, renaming renders nothing")
  void fogChangeRendersAll() {
    final long a = ChunkCoordinate.key(0, 0);
    final long b = ChunkCoordinate.key(1, 0);
    assertEquals(
        Set.of(a, b),
        RegionEditor.rendered(region(RegionFog.HIDE, a, b), region(RegionFog.VEIL, a, b)));
    assertEquals(
        Set.of(), RegionEditor.rendered(region(RegionFog.HIDE, a), region(RegionFog.HIDE, a)));
    assertEquals(
        Set.of(), RegionEditor.rendered(region(RegionFog.NONE, a), region(RegionFog.NONE, b)));
    assertEquals(Set.of(a), RegionEditor.rendered(null, region(RegionFog.VEIL, a)));
  }
}

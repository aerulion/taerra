package net.aerulion.taerra.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionEditorTest {

  private static final UUID TOWN = UUID.randomUUID();

  private static @NotNull MapRegion region(final @NotNull RegionFog fog, final long... chunks) {
    return MapRegion.of(TOWN, "w", "Town", 0, fog, true, 0L, new LongOpenHashSet(chunks));
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

  @Test
  @DisplayName(
      "a name another region already has is taken, whatever its case, and blank names never are")
  void namesAreUnique() {
    final @NotNull List<MapRegion> regions = List.of(region(RegionFog.HIDE, 0L));

    assertTrue(RegionEditor.nameTaken(regions, null, "town"));
    assertTrue(RegionEditor.nameTaken(regions, UUID.randomUUID(), "TOWN"));
    assertFalse(RegionEditor.nameTaken(regions, TOWN, "Town"));
    assertFalse(RegionEditor.nameTaken(regions, null, "Harbour"));
    assertFalse(
        RegionEditor.nameTaken(
            List.of(
                region(RegionFog.HIDE, 0L)
                    .with("", 0, RegionFog.HIDE, true, new LongOpenHashSet())),
            null,
            ""));
  }
}

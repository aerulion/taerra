package net.aerulion.taerra.rendering.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsBuilder;
import org.bukkit.block.BlockType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FogTest {

  private static final int GROUND = 0xFF336633;
  private static final int[] FLAT = {63, 63, 63, 63, 63, 63, 63, 63, 63};

  private static ChunkColumns flat(final int chunkX, final int chunkZ) {
    final ChunkColumnsBuilder builder = ChunkColumns.builder(chunkX, chunkZ);
    for (int column = 0; column < ChunkColumns.COLUMN_COUNT; column++) {
      builder
          .addRun(63, 0, BlockTypes.id(BlockType.GRASS_BLOCK))
          .completeColumn(null, BlockTypes.NONE);
    }
    return builder.build();
  }

  private static int density(final int mist) {
    return mist & 15;
  }

  private static int[] mistBeside(final int offsetX) {
    final byte[] hiddenAround = new byte[Fog.AROUND * Fog.AROUND];
    hiddenAround[Fog.around(offsetX, 0)] = Fog.HIDDEN;
    return Fog.mist(4, 7, flat(4, 7), FLAT, hiddenAround);
  }

  @Test
  @DisplayName("mist always covers the border of a hidden chunk")
  void mistCoversBorder() {
    final int[] mist = mistBeside(-1);
    for (int localZ = 0; localZ < 16; localZ++) {
      for (int localX = 0; localX < 4; localX++) {
        assertEquals(15, density(mist[(localX << 4) | localZ]));
      }
    }
  }

  @Test
  @DisplayName("mist is gone by the edge of its reach")
  void mistEndsWithinReach() {
    for (final int column : mistBeside(-Fog.REACH_CHUNKS)) {
      assertEquals(0, density(column));
    }
  }

  @Test
  @DisplayName("the veil hides low ground and leaves what rises through it clear")
  void veilCoversBelowLayer() {
    final int fog = Fog.hidden(0, 0, FLAT, false)[0];
    final int low = Fog.veil(GROUND, fog, 3.5f, 9.5f, 63);

    assertNotEquals(GROUND, low);
    assertEquals(GROUND, Fog.veil(GROUND, fog, 3.5f, 9.5f, Fog.layerY(fog)));
    assertEquals(GROUND, Fog.veil(GROUND, Fog.NO_MIST, 3.5f, 9.5f, 63));
  }

  @Test
  @DisplayName("the veil billows instead of lying flat")
  void veilBillows() {
    final int fog = Fog.hidden(0, 0, FLAT, false)[0];
    final Set<Integer> colors = new HashSet<>();
    for (int x = 0; x < 96; x++) {
      for (int z = 0; z < 96; z++) {
        colors.add(Fog.veil(GROUND, fog, x + 0.5f, z + 0.5f, 63));
      }
    }
    assertTrue(colors.size() >= 3);
  }

  @Test
  @DisplayName("opaque fog looks the same whatever lies beneath it")
  void fogHidesEverything() {
    final int fog = Fog.hidden(0, 0, FLAT, false)[0];
    for (int x = 0; x < 64; x++) {
      for (int z = 0; z < 64; z++) {
        assertEquals(
            Fog.veil(GROUND, fog, x + 0.5f, z + 0.5f, 63),
            Fog.veil(0xFFC03020, fog, x + 0.5f, z + 0.5f, 63));
      }
    }
  }

  @Test
  @DisplayName("a teaser lets the ground beneath show through")
  void teaserShowsThrough() {
    final int teaser = Fog.hidden(0, 0, FLAT, true)[0];
    assertTrue(Fog.teased(teaser));
    assertNotEquals(
        Fog.veil(GROUND, teaser, 3.5f, 9.5f, 63), Fog.veil(0xFFC03020, teaser, 3.5f, 9.5f, 63));
  }
}

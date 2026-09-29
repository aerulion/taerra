package net.aerulion.taerra.rendering.color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.aerulion.taerra.rendering.block.BlockShape;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlockDataStoreImplTest {

  private static final int WATER_RGB = 0xFFA9A9A9;

  @Test
  @DisplayName("a block the generator measured is never overridden, however the game renders it")
  void measuredBlocksWin() {

    final @NotNull BlockRenderData water =
        new BlockRenderData(WATER_RGB, WATER_RGB, BlockShape.FULL, true, true);
    assertNull(BlockDataStoreImpl.invisibleFallback(water, true));
    assertNull(BlockDataStoreImpl.invisibleFallback(water, false));
  }

  @Test
  @DisplayName("a block with no entry that the game draws nothing for becomes air")
  void invisibleBlocksWithoutAnEntryBecomeAir() {

    final @NotNull BlockRenderData filled = BlockDataStoreImpl.invisibleFallback(null, true);
    assertNotNull(filled);
    assertEquals(BlockShape.NONE, filled.shape());
  }

  @Test
  @DisplayName(
      "a block with no entry that the game does draw stays unknown, so it is still reported")
  void unknownVisibleBlocksAreLeftAlone() {

    assertNull(BlockDataStoreImpl.invisibleFallback(null, false));
  }
}

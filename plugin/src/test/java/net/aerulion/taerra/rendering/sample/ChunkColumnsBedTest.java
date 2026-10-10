package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.block.Fluids;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkColumnsBedTest {

  private static @NotNull ChunkColumns singleColumn(
      final Runnable ignored, final @NotNull ChunkColumnsBuilder builder) {
    for (int i = 1; i < ChunkColumns.COLUMN_COUNT; i++) {
      builder.completeColumn(null, null);
    }
    return builder.build();
  }

  @Test
  @DisplayName("dry ground reports its own top as the surface to follow")
  void dryGroundBedIsItsTop() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder.addRun(70, 40, BlockType.STONE).completeColumn(null, null);
    assertEquals(70, singleColumn(null, builder).bedY(0));
  }

  @Test
  @DisplayName("a lake reports the bed under it, which is what its shading has to follow")
  void fluidReportsTheBedBeneath() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder
        .addRun(62, 55, BlockType.WATER)
        .addRun(54, 0, BlockType.SAND)
        .completeColumn(null, null);
    final @NotNull ChunkColumns columns = singleColumn(null, builder);

    assertEquals(62, columns.topY(0), "the surface is still the water");
    assertEquals(54, columns.bedY(0), "but its shading follows the sand beneath");
  }

  @Test
  @DisplayName("fluid all the way down falls back to the bottom of the fluid")
  void bottomlessFluidFallsBack() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder.addRun(62, -64, BlockType.WATER).completeColumn(null, null);
    assertEquals(-64, singleColumn(null, builder).bedY(0));
  }

  @Test
  @DisplayName("an empty column has no bed to follow")
  void emptyColumnHasNoBed() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder.completeColumn(null, null);
    assertEquals(ChunkColumns.EMPTY_COLUMN, singleColumn(null, builder).bedY(0));
  }

  @Test
  @DisplayName(
      "a plant growing up through water is recorded as water, so the surface keeps its level")
  void submergedPlantsBecomeWater() {

    assertEquals(
        BlockType.WATER,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.KELP))));
    assertEquals(
        BlockType.WATER,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.KELP_PLANT))));
    assertEquals(
        BlockType.WATER,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.SEAGRASS))));
    assertEquals(
        BlockType.WATER,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.TALL_SEAGRASS))));
  }

  @Test
  @DisplayName("things that sit on the bed with water above them are left alone and stay visible")
  void bedDwellersAreLeftAlone() {
    assertEquals(
        BlockType.BRAIN_CORAL_FAN,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.BRAIN_CORAL_FAN))));
    assertEquals(
        BlockType.SEA_PICKLE,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.SEA_PICKLE))));
    assertEquals(
        BlockType.LILY_PAD,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.LILY_PAD))),
        "a lily pad floats on the surface and should still be drawn");
    assertEquals(
        BlockType.STONE,
        BlockTypes.byId(Fluids.substituteSubmerged(BlockTypes.id(BlockType.STONE))));
  }

  @Test
  @DisplayName("lava counts as fluid too, so its surface follows the rock under it")
  void lavaIsFluid() {
    assertTrue(Fluids.isFluid(BlockType.WATER));
    assertTrue(Fluids.isFluid(BlockType.LAVA));
    assertTrue(Fluids.isFluid(BlockType.BUBBLE_COLUMN));
    assertFalse(Fluids.isFluid(BlockType.STONE));
    assertFalse(
        Fluids.isFluid(BlockType.ICE), "frozen water is ground, not something to see through");
  }
}

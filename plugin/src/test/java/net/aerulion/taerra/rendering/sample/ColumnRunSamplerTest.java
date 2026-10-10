package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.function.IntFunction;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.block.BlockTypes;
import net.aerulion.taerra.rendering.source.ColumnSource;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColumnRunSamplerTest {

  private static final int MIN_HEIGHT = -64;
  private static final int MAX_HEIGHT = 320;
  private static final int VANILLA_NETHER_CEILING_Y = 122;

  private static final @NotNull BlockShapeResolver SHAPES =
      blockType ->
          Set.of(
                      BlockType.AIR,
                      BlockType.CAVE_AIR,
                      BlockType.VOID_AIR,
                      BlockType.BARRIER,
                      BlockType.LIGHT,
                      BlockType.STRUCTURE_VOID)
                  .contains(blockType)
              ? BlockShape.NONE
              : Set.of(BlockType.TORCH, BlockType.TALL_GRASS, BlockType.FIRE_CORAL)
                      .contains(blockType)
                  ? BlockShape.THIN
                  : BlockShape.FULL;

  private static @NotNull ChunkColumns sample(final @NotNull IntFunction<BlockType> column) {
    return sample((localX, localZ, y) -> column.apply(y));
  }

  private static @NotNull ChunkColumns sample(final Blocks blocks) {
    return ColumnRunSampler.sampleChunk(
        new TestChunk(blocks), ColumnRunSampler.NO_CEILING, SHAPES, 0, 0, null);
  }

  private static int runCount(final @NotNull ChunkColumns columns) {
    return columns.runEnd(0) - columns.runStart(0);
  }

  private static @NotNull ChunkColumns sampleNether(final Blocks blocks) {
    return ColumnRunSampler.sampleChunk(
        new TestChunk(blocks), VANILLA_NETHER_CEILING_Y, SHAPES, 0, 0, null);
  }

  @Test
  @DisplayName("columns outside the world limit are left empty, the rest keep their full depth")
  void limitClipsColumns() {
    final @NotNull ChunkColumns columns =
        ColumnRunSampler.sampleChunk(
            new TestChunk((localX, localZ, y) -> y <= 70 ? BlockType.STONE : BlockType.AIR),
            ColumnRunSampler.NO_CEILING,
            SHAPES,
            0,
            0,
            WorldLimit.rectangle(0, 0, 7, 15));

    assertEquals(70, columns.topY(ChunkColumns.columnIndex(7, 3)));
    assertEquals(
        MIN_HEIGHT, columns.runBottomY(columns.runEnd(ChunkColumns.columnIndex(7, 3)) - 1));
    assertEquals(ChunkColumns.EMPTY_COLUMN, columns.topY(ChunkColumns.columnIndex(8, 3)));
  }

  @Test
  @DisplayName("a hidden chunk is a flat striped plane and never reads the blocks underneath")
  void hiddenChunkIsStriped() {
    final @NotNull ChunkColumns columns =
        ColumnRunSampler.blockedChunk(
            new TestChunk(
                (localX, localZ, y) -> {
                  throw new AssertionError("read a hidden block");
                }),
            -1,
            0);

    assertEquals(63, columns.minTopY());
    assertEquals(63, columns.maxTopY());
    assertEquals(
        BlockType.YELLOW_CONCRETE,
        columns.runBlockType(columns.runStart(ChunkColumns.columnIndex(0, 0))));
    assertEquals(
        BlockType.BLACK_CONCRETE,
        columns.runBlockType(columns.runStart(ChunkColumns.columnIndex(4, 0))));
    assertEquals(
        BlockType.YELLOW_CONCRETE,
        columns.runBlockType(columns.runStart(ChunkColumns.columnIndex(4, 4))));
  }

  @Test
  @DisplayName("a solid column is one run down to the world floor")
  void solidColumnIsOneRun() {
    final @NotNull ChunkColumns columns = sample(y -> y <= 70 ? BlockType.STONE : BlockType.AIR);

    assertEquals(1, runCount(columns));
    assertEquals(70, columns.runTopY(columns.runStart(0)));
    assertEquals(MIN_HEIGHT, columns.runBottomY(columns.runStart(0)));
  }

  @Test
  @DisplayName("a tower keeps every storey down to its ground, however far below the roof that is")
  void everyStoreyIsKeptAtAnyDepth() {
    final @NotNull ChunkColumns columns =
        sample(
            y -> {
              if (y > 64 && y < 300) {
                return y % 8 == 0 ? BlockType.TORCH : BlockType.STONE_BRICKS;
              }
              return y <= 64 ? BlockType.STONE : BlockType.AIR;
            });

    final int lastRun = columns.runEnd(0) - 1;
    assertEquals(299, columns.runTopY(columns.runStart(0)));
    assertEquals(71, columns.runTopY(lastRun - 1), "the lowest storey, just above the street");
    assertEquals(65, columns.runBottomY(lastRun - 1));
    assertEquals(64, columns.runTopY(lastRun), "the street itself");
    assertEquals(MIN_HEIGHT, columns.runBottomY(lastRun));
    for (int run = columns.runStart(0); run < lastRun - 1; run++) {
      assertEquals(columns.runBottomY(run) - 2, columns.runTopY(run + 1), "no storey is skipped");
    }
  }

  @Test
  @DisplayName("a cave deep under the surface keeps its floor and ceiling")
  void deepCaveIsRecorded() {
    final @NotNull ChunkColumns columns =
        sample(y -> y <= 100 && (y < -20 || y > -10) ? BlockType.STONE : BlockType.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(-9, columns.runBottomY(columns.runStart(0)), "the cave ceiling");
    assertEquals(-21, columns.runTopY(columns.runStart(0) + 1), "the cave floor");
  }

  @Test
  @DisplayName(
      "water filling a whole section does not swallow the bed below it, inside the chunk or at its edge")
  void deepWaterKeepsItsBedEverywhere() {
    final @NotNull ChunkColumns columns =
        sample(y -> y <= 20 ? BlockType.STONE : y <= 62 ? BlockType.WATER : BlockType.AIR);

    for (final int column :
        new int[] {ChunkColumns.columnIndex(0, 0), ChunkColumns.columnIndex(7, 7)}) {
      assertEquals(20, columns.bedY(column), "the bed must be at the same depth in every column");
    }
  }

  @Test
  @DisplayName("a roof over nothing records no ground, rather than inventing one")
  void floatingColumnHasNoGround() {
    final @NotNull ChunkColumns columns =
        sample(y -> y == 200 ? BlockType.OAK_PLANKS : BlockType.AIR);

    assertEquals(1, runCount(columns));
    assertEquals(200, columns.runTopY(columns.runStart(0)));
    assertEquals(200, columns.runBottomY(columns.runStart(0)));
  }

  @Test
  @DisplayName("a thin block resting on a column caps it without forming a run")
  void thinBlockBecomesACap() {
    final @NotNull ChunkColumns columns =
        sample(
            y -> {
              if (y == 65) {
                return BlockType.TORCH;
              }
              return y <= 64 ? BlockType.STONE : BlockType.AIR;
            });

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(BlockType.TORCH, columns.capBlockType(0));
  }

  @Test
  @DisplayName("a double plant on the ground still only caps it")
  void doublePlantBecomesACap() {
    final @NotNull ChunkColumns columns =
        sample(
            y ->
                y == 65 || y == 66
                    ? BlockType.TALL_GRASS
                    : y <= 64 ? BlockType.STONE : BlockType.AIR);

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(BlockType.TALL_GRASS, columns.capBlockType(0));
  }

  @Test
  @DisplayName(
      "thin blocks floating in the air are drawn as blocks where they are, not on the ground")
  void floatingThinBlocksFormRuns() {
    final @NotNull ChunkColumns columns =
        sample(y -> y == 80 ? BlockType.FIRE_CORAL : y <= 64 ? BlockType.STONE : BlockType.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(80, columns.runTopY(columns.runStart(0)));
    assertEquals(BlockType.FIRE_CORAL, columns.runBlockType(columns.runStart(0)));
    assertNull(columns.capBlockType(0));
  }

  @Test
  @DisplayName("a stack of thin blocks taller than a double plant is drawn as blocks")
  void tallThinStackFormsARun() {
    final @NotNull ChunkColumns columns =
        sample(
            y ->
                y >= 65 && y <= 67
                    ? BlockType.FIRE_CORAL
                    : y <= 64 ? BlockType.STONE : BlockType.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(67, columns.runTopY(columns.runStart(0)));
    assertEquals(65, columns.runBottomY(columns.runStart(0)));
    assertNull(columns.capBlockType(0));
  }

  @Test
  @DisplayName("a block the game never draws forms no run and caps nothing")
  void invisibleBlockIsTreatedAsAir() {

    final @NotNull ChunkColumns columns =
        sample(
            y -> {
              if (y == 100) {
                return BlockType.BARRIER;
              }
              return y <= 64 ? BlockType.STONE : BlockType.AIR;
            });

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(BlockType.STONE, columns.runBlockType(columns.runStart(0)));
    assertNull(columns.capBlockType(0));
  }

  @Test
  @DisplayName("an empty column stays empty")
  void emptyColumnStaysEmpty() {
    final @NotNull ChunkColumns columns = sample(y -> BlockType.AIR);

    assertEquals(0, runCount(columns));
    assertTrue(columns.isEmpty());
  }

  @Test
  @DisplayName("nether columns start at the cave floor below the bedrock roof")
  void netherSkipsBedrockRoof() {
    final @NotNull ChunkColumns columns =
        sampleNether(
            (localX, localZ, y) -> {
              if (y >= 123 && y <= 127) {
                return BlockType.BEDROCK;
              }
              if (y > 127 || (localX == 0 && y > 70 && y < 123)) {
                return BlockType.AIR;
              }
              return BlockType.NETHERRACK;
            });

    assertEquals(70, columns.topY(ChunkColumns.columnIndex(0, 0)));
    final int solid = ChunkColumns.columnIndex(1, 0);
    assertEquals(122, columns.topY(solid));
    assertEquals(BlockType.NETHERRACK, columns.runBlockType(columns.runStart(solid)));
  }

  @FunctionalInterface
  private interface Blocks {

    BlockType at(int localX, int localZ, int y);
  }

  private record TestChunk(Blocks at) implements ColumnSource {

    @Override
    public int minHeight() {
      return MIN_HEIGHT;
    }

    @Override
    public int maxHeight() {
      return MAX_HEIGHT;
    }

    @Override
    public int highestNonAirY(final int localX, final int localZ) {
      for (int y = MAX_HEIGHT - 1; y >= MIN_HEIGHT; y--) {
        if (this.at.at(localX, localZ, y) != BlockType.AIR) {
          return y;
        }
      }
      return EMPTY_COLUMN;
    }

    @Override
    public boolean isSectionAllAir(final int y) {
      final int sectionBottom = y & ~15;
      for (int level = Math.max(MIN_HEIGHT, sectionBottom);
          level < Math.min(MAX_HEIGHT, sectionBottom + 16);
          level++) {
        for (int localX = 0; localX < 16; localX++) {
          for (int localZ = 0; localZ < 16; localZ++) {
            if (this.at.at(localX, localZ, level) != BlockType.AIR) {
              return false;
            }
          }
        }
      }
      return true;
    }

    @Override
    public boolean isSectionAllFull(final int y, final @NotNull BlockShapeResolver shapes) {
      final int sectionBottom = y & ~15;
      for (int level = sectionBottom; level < sectionBottom + 16; level++) {
        for (int localX = 0; localX < 16; localX++) {
          for (int localZ = 0; localZ < 16; localZ++) {
            if (shapes.shapeOf(this.at.at(localX, localZ, level)) != BlockShape.FULL) {
              return false;
            }
          }
        }
      }
      return true;
    }

    @Override
    public int blockIdAt(final int localX, final int y, final int localZ) {
      return BlockTypes.id(this.at.at(localX, localZ, y));
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public @NotNull Biome biomeAt(final int localX, final int y, final int localZ) {

      return null;
    }
  }
}

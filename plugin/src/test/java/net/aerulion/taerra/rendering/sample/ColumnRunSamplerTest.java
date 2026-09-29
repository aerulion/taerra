package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntFunction;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.block.BlockShape;
import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import net.aerulion.taerra.rendering.source.ColumnSource;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ColumnRunSamplerTest {

  private static final int MIN_HEIGHT = -64;
  private static final int MAX_HEIGHT = 320;
  private static final int VANILLA_NETHER_CEILING_Y = 122;

  private static final @NotNull BlockShapeResolver SHAPES =
      material ->
          switch (material) {
            case AIR, CAVE_AIR, VOID_AIR, BARRIER, LIGHT, STRUCTURE_VOID -> BlockShape.NONE;
            case TORCH, TALL_GRASS, FIRE_CORAL -> BlockShape.THIN;
            default -> BlockShape.FULL;
          };

  private static @NotNull ChunkColumns sample(final @NotNull IntFunction<Material> column) {
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
            new TestChunk((localX, localZ, y) -> y <= 70 ? Material.STONE : Material.AIR),
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
  @DisplayName("a solid column is one run down to the world floor")
  void solidColumnIsOneRun() {
    final @NotNull ChunkColumns columns = sample(y -> y <= 70 ? Material.STONE : Material.AIR);

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
                return y % 8 == 0 ? Material.TORCH : Material.STONE_BRICKS;
              }
              return y <= 64 ? Material.STONE : Material.AIR;
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
        sample(y -> y <= 100 && (y < -20 || y > -10) ? Material.STONE : Material.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(-9, columns.runBottomY(columns.runStart(0)), "the cave ceiling");
    assertEquals(-21, columns.runTopY(columns.runStart(0) + 1), "the cave floor");
  }

  @Test
  @DisplayName(
      "water filling a whole section does not swallow the bed below it, inside the chunk or at its edge")
  void deepWaterKeepsItsBedEverywhere() {
    final @NotNull ChunkColumns columns =
        sample(y -> y <= 20 ? Material.STONE : y <= 62 ? Material.WATER : Material.AIR);

    for (final int column :
        new int[] {ChunkColumns.columnIndex(0, 0), ChunkColumns.columnIndex(7, 7)}) {
      assertEquals(20, columns.bedY(column), "the bed must be at the same depth in every column");
    }
  }

  @Test
  @DisplayName("a roof over nothing records no ground, rather than inventing one")
  void floatingColumnHasNoGround() {
    final @NotNull ChunkColumns columns =
        sample(y -> y == 200 ? Material.OAK_PLANKS : Material.AIR);

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
                return Material.TORCH;
              }
              return y <= 64 ? Material.STONE : Material.AIR;
            });

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(Material.TORCH, columns.capMaterial(0));
  }

  @Test
  @DisplayName("a double plant on the ground still only caps it")
  void doublePlantBecomesACap() {
    final @NotNull ChunkColumns columns =
        sample(
            y ->
                y == 65 || y == 66 ? Material.TALL_GRASS : y <= 64 ? Material.STONE : Material.AIR);

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(Material.TALL_GRASS, columns.capMaterial(0));
  }

  @Test
  @DisplayName(
      "thin blocks floating in the air are drawn as blocks where they are, not on the ground")
  void floatingThinBlocksFormRuns() {
    final @NotNull ChunkColumns columns =
        sample(y -> y == 80 ? Material.FIRE_CORAL : y <= 64 ? Material.STONE : Material.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(80, columns.runTopY(columns.runStart(0)));
    assertEquals(Material.FIRE_CORAL, columns.runMaterial(columns.runStart(0)));
    assertNull(columns.capMaterial(0));
  }

  @Test
  @DisplayName("a stack of thin blocks taller than a double plant is drawn as blocks")
  void tallThinStackFormsARun() {
    final @NotNull ChunkColumns columns =
        sample(
            y ->
                y >= 65 && y <= 67 ? Material.FIRE_CORAL : y <= 64 ? Material.STONE : Material.AIR);

    assertEquals(2, runCount(columns));
    assertEquals(67, columns.runTopY(columns.runStart(0)));
    assertEquals(65, columns.runBottomY(columns.runStart(0)));
    assertNull(columns.capMaterial(0));
  }

  @Test
  @DisplayName("a block the game never draws forms no run and caps nothing")
  void invisibleBlockIsTreatedAsAir() {

    final @NotNull ChunkColumns columns =
        sample(
            y -> {
              if (y == 100) {
                return Material.BARRIER;
              }
              return y <= 64 ? Material.STONE : Material.AIR;
            });

    assertEquals(1, runCount(columns));
    assertEquals(64, columns.runTopY(columns.runStart(0)));
    assertEquals(Material.STONE, columns.runMaterial(columns.runStart(0)));
    assertNull(columns.capMaterial(0));
  }

  @Test
  @DisplayName("an empty column stays empty")
  void emptyColumnStaysEmpty() {
    final @NotNull ChunkColumns columns = sample(y -> Material.AIR);

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
                return Material.BEDROCK;
              }
              if (y > 127 || (localX == 0 && y > 70 && y < 123)) {
                return Material.AIR;
              }
              return Material.NETHERRACK;
            });

    assertEquals(70, columns.topY(ChunkColumns.columnIndex(0, 0)));
    final int solid = ChunkColumns.columnIndex(1, 0);
    assertEquals(122, columns.topY(solid));
    assertEquals(Material.NETHERRACK, columns.runMaterial(columns.runStart(solid)));
  }

  @FunctionalInterface
  private interface Blocks {

    Material at(int localX, int localZ, int y);
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
        if (this.at.at(localX, localZ, y) != Material.AIR) {
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
            if (this.at.at(localX, localZ, level) != Material.AIR) {
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
    public @NotNull Material materialAt(final int localX, final int y, final int localZ) {
      return this.at.at(localX, localZ, y);
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public @NotNull Biome biomeAt(final int localX, final int y, final int localZ) {

      return null;
    }
  }
}

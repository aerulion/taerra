package net.aerulion.taerra.rendering.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkColumnsTest {

  private static @NotNull ChunkColumnsBuilder builderWithLeadingEmptyColumns(
      final int columnsToFill) {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    for (int i = 0; i < columnsToFill; i++) {
      builder.completeColumn(null, null);
    }
    return builder;
  }

  private static @NotNull ChunkColumns padToFullChunk(
      final @NotNull ChunkColumnsBuilder builder, final int completedColumns) {
    for (int i = completedColumns; i < ChunkColumns.COLUMN_COUNT; i++) {
      builder.completeColumn(null, null);
    }
    return builder.build();
  }

  @Test
  @DisplayName("column indices are unique across the whole chunk")
  void columnIndicesAreUnique() {
    final boolean @NotNull [] seen = new boolean[ChunkColumns.COLUMN_COUNT];
    for (int localX = 0; localX < 16; localX++) {
      for (int localZ = 0; localZ < 16; localZ++) {
        final int index = ChunkColumns.columnIndex(localX, localZ);
        assertFalse(seen[index], "duplicate index for " + localX + "," + localZ);
        seen[index] = true;
      }
    }
  }

  @Test
  @DisplayName("runs are stored against the column that produced them")
  void runsBelongToTheirColumn() {
    final @NotNull ChunkColumnsBuilder builder = builderWithLeadingEmptyColumns(0);
    builder.addRun(70, 65, Material.OAK_PLANKS).completeColumn(null, null);
    builder.addRun(80, 80, Material.STONE).addRun(60, 50, Material.DIRT).completeColumn(null, null);
    final @NotNull ChunkColumns columns = padToFullChunk(builder, 2);

    assertEquals(1, columns.runEnd(0) - columns.runStart(0));
    assertEquals(2, columns.runEnd(1) - columns.runStart(1));
    assertEquals(70, columns.runTopY(columns.runStart(0)));
    assertEquals(65, columns.runBottomY(columns.runStart(0)));
    assertSame(Material.OAK_PLANKS, columns.runMaterial(columns.runStart(0)));
    assertSame(Material.DIRT, columns.runMaterial(columns.runStart(1) + 1));
  }

  @Test
  @DisplayName("a column reports solid only inside its runs, so overhangs keep their gaps")
  void solidityFollowsTheRuns() {
    final @NotNull ChunkColumnsBuilder builder = builderWithLeadingEmptyColumns(0);

    builder
        .addRun(70, 70, Material.OAK_PLANKS)
        .addRun(65, 65, Material.STONE)
        .completeColumn(null, null);
    final @NotNull ChunkColumns columns = padToFullChunk(builder, 1);

    assertTrue(columns.isSolidAt(0, 70));
    assertFalse(columns.isSolidAt(0, 69));
    assertFalse(columns.isSolidAt(0, 66));
    assertTrue(columns.isSolidAt(0, 65));
    assertFalse(columns.isSolidAt(0, 64));
    assertFalse(columns.isSolidAt(0, 71));
  }

  @Test
  @DisplayName("an empty column has no runs and no top")
  void emptyColumnHasNoTop() {
    final @NotNull ChunkColumns columns = padToFullChunk(ChunkColumns.builder(0, 0), 0);
    assertEquals(ChunkColumns.EMPTY_COLUMN, columns.topY(0));
    assertEquals(columns.runStart(0), columns.runEnd(0));
    assertFalse(columns.isSolidAt(0, 0));
    assertTrue(columns.isEmpty());
  }

  @Test
  @DisplayName("the top of a column is the top of its first run")
  void topYIsTheFirstRun() {
    final @NotNull ChunkColumnsBuilder builder = builderWithLeadingEmptyColumns(0);
    builder.addRun(100, 90, Material.STONE).completeColumn(null, null);
    assertEquals(100, padToFullChunk(builder, 1).topY(0));
  }

  @Test
  @DisplayName("the chunk's top range spans only columns that hold something")
  void topRangeIgnoresEmptyColumns() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder.completeColumn(null, null);
    builder.addRun(80, 70, Material.STONE).completeColumn(null, null);
    builder.completeColumn(null, null);
    builder.addRun(40, 30, Material.STONE).completeColumn(null, null);
    final @NotNull ChunkColumns columns = padToFullChunk(builder, 4);

    assertEquals(40, columns.minTopY());
    assertEquals(80, columns.maxTopY());
    assertFalse(columns.isEmpty());
  }

  @Test
  @DisplayName("a cap records the thin block resting on a column without becoming a run")
  void capIsSeparateFromRuns() {
    final @NotNull ChunkColumnsBuilder builder = builderWithLeadingEmptyColumns(0);
    builder.addRun(64, 60, Material.GRASS_BLOCK).completeColumn(null, Material.TORCH);
    final @NotNull ChunkColumns columns = padToFullChunk(builder, 1);

    assertSame(Material.TORCH, columns.capMaterial(0));
    assertEquals(1, columns.runEnd(0) - columns.runStart(0));
    assertEquals(64, columns.topY(0));
    assertNull(columns.capMaterial(1));
  }

  @Test
  @DisplayName("negative y levels survive the short encoding")
  void deepColumnsRoundTrip() {
    final @NotNull ChunkColumnsBuilder builder = builderWithLeadingEmptyColumns(0);
    builder.addRun(-40, -64, Material.DEEPSLATE).completeColumn(null, null);
    final @NotNull ChunkColumns columns = padToFullChunk(builder, 1);

    assertEquals(-40, columns.runTopY(columns.runStart(0)));
    assertEquals(-64, columns.runBottomY(columns.runStart(0)));
    assertTrue(columns.isSolidAt(0, -50));
    assertFalse(columns.isSolidAt(0, -65));
  }

  @Test
  @DisplayName("building a partial chunk is rejected rather than silently truncated")
  void partialChunkIsRejected() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    builder.completeColumn(null, null);
    assertThrows(IllegalStateException.class, builder::build);
  }

  @Test
  @DisplayName("the run arrays grow past their initial capacity")
  void runStorageGrows() {
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(3, -4);
    for (int column = 0; column < ChunkColumns.COLUMN_COUNT; column++) {
      for (int run = 0; run < 6; run++) {
        builder.addRun(100 - run * 4, 98 - run * 4, Material.STONE);
      }
      builder.completeColumn(null, null);
    }
    final @NotNull ChunkColumns columns = builder.build();

    assertEquals(3, columns.chunkX());
    assertEquals(-4, columns.chunkZ());
    for (int column = 0; column < ChunkColumns.COLUMN_COUNT; column++) {
      assertEquals(6, columns.runEnd(column) - columns.runStart(column));
    }
    assertEquals(100, columns.topY(ChunkColumns.COLUMN_COUNT - 1));
  }

  @Test
  @DisplayName(
      "a run buried where no rotation can see it folds into the run above, and one beside a cave does not")
  void hiddenRunsFold() {

    final int caveColumn = ChunkColumns.columnIndex(8, 9);
    final @NotNull ChunkColumnsBuilder builder = ChunkColumns.builder(0, 0);
    for (int column = 0; column < ChunkColumns.COLUMN_COUNT; column++) {
      builder.addRun(64, 64, Material.GRASS_BLOCK).addRun(63, 41, Material.STONE);
      if (column != caveColumn) {
        builder.addRun(40, 40, Material.IRON_ORE);
      }
      builder.addRun(39, -64, Material.STONE).completeColumn(null, null);
    }
    final @NotNull ChunkColumns columns = builder.build();

    final int buried = ChunkColumns.columnIndex(5, 5);
    assertEquals(
        1,
        columns.runEnd(buried) - columns.runStart(buried),
        "nothing below the grass can be seen");
    assertSame(Material.GRASS_BLOCK, columns.runMaterial(columns.runStart(buried)));
    assertEquals(
        -64, columns.runBottomY(columns.runStart(buried)), "solid all the way down, as before");

    final int besideCave = ChunkColumns.columnIndex(8, 8);
    assertEquals(2, columns.runEnd(besideCave) - columns.runStart(besideCave));
    assertSame(
        Material.IRON_ORE,
        columns.runMaterial(columns.runStart(besideCave) + 1),
        "the ore faces the pocket, so it keeps its own color");
    assertEquals(40, columns.runTopY(columns.runStart(besideCave) + 1));

    final int border = ChunkColumns.columnIndex(0, 5);
    assertEquals(
        4,
        columns.runEnd(border) - columns.runStart(border),
        "a border column's other neighbor is in the next chunk, which nothing here can vouch for");
  }
}

package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkSelectionTest {

  @Test
  @DisplayName("painting the same chunk twice selects it once, negative blocks round down")
  void brushSelectsEachChunkOnce() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.brush(-1, 0, 1);
    selection.brush(-16, 15, 1);
    selection.brush(0, 0, 1);
    selection.end();

    assertEquals(Set.of(ChunkSelection.key(-1, 0), ChunkSelection.key(0, 0)), selection.chunks());
  }

  @Test
  @DisplayName("a box selects every chunk between its corners and erases them again")
  void boxSelectsAndErases() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.box(40, -20, -5, 10);
    selection.end();

    assertEquals(4 * 3, selection.size());
    assertTrue(selection.contains(-5, -20));
    assertTrue(selection.contains(40, 10));

    selection.begin(true);
    selection.box(0, 0, 0, 0);
    selection.end();

    assertEquals(11, selection.size());
    assertFalse(selection.contains(0, 0));
  }

  @Test
  @DisplayName("a lasso selects the chunks whose centre lies inside it")
  void lassoSelectsChunkCentres() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.lasso(new double[][] {{0, 0}, {50, 0}, {0, 50}});
    selection.end();

    assertEquals(
        Set.of(
            ChunkSelection.key(0, 0),
            ChunkSelection.key(1, 0),
            ChunkSelection.key(0, 1),
            ChunkSelection.key(1, 1),
            ChunkSelection.key(2, 0),
            ChunkSelection.key(0, 2)),
        selection.chunks());
  }

  @Test
  @DisplayName("touching chunks merge into one outline, a gap inside gets its own ring")
  void ringsMergeChunks() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.box(0, 0, 47, 47);
    selection.end();
    assertEquals(1, selection.rings().size());
    assertEquals(12, selection.rings().getFirst().length);

    selection.begin(true);
    selection.brush(20, 20, 1);
    selection.end();

    assertEquals(2, selection.rings().size());
    assertEquals(16, selection.rings().get(0).length + selection.rings().get(1).length);
  }

  @Test
  @DisplayName("a chunk corner touches the four chunks around it, any other point only its own")
  void touchesChunksAroundCorners() {
    assertArrayEquals(new int[] {-2, -1}, ChunkSelection.touching(-16));
    assertArrayEquals(new int[] {-1, 0}, ChunkSelection.touching(0));
    assertArrayEquals(new int[] {-1}, ChunkSelection.touching(-.5));
    assertArrayEquals(new int[] {1}, ChunkSelection.touching(31.5));
  }

  @Test
  @DisplayName("a chunk's level ignores a few tall columns")
  void levelIgnoresTallColumns() {
    assertEquals(64, ChunkSelection.median(new int[] {64, 64, 90, 64, 120, 63, 0}, 6));
  }

  @Test
  @DisplayName("a wide brush paints a square around the cursor and one undo takes the stroke back")
  void strokesUndoAsOne() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.brush(8, 8, 3);
    selection.brush(24, 8, 3);
    final ChunkSelection.Change change = selection.end();

    assertEquals(12, selection.size());
    assertEquals(12, change.added().size());

    selection.revert(change);
    assertTrue(selection.isEmpty());
    selection.replay(change);
    assertEquals(12, selection.size());
  }

  @Test
  @DisplayName("chunks that only touch at a corner are detached from the biggest piece")
  void cornersDoNotConnect() {
    final @NotNull ChunkSelection selection = new ChunkSelection();
    selection.begin(false);
    selection.box(0, 0, 31, 31);
    selection.brush(32, 32, 1);
    selection.end();

    assertFalse(selection.connected());
    assertEquals(java.util.List.of(ChunkSelection.key(2, 2)), selection.detached());
  }

  @Test
  @DisplayName("a region brush skips chunks another region owns")
  void skipsTakenChunks() {
    final @NotNull ChunkSelection selection =
        new ChunkSelection(100, chunk -> chunk == ChunkSelection.key(1, 0));
    selection.begin(false);
    selection.brush(16, 0, 3);
    selection.end();

    assertEquals(8, selection.size());
    assertTrue(selection.taken(1, 0));
  }
}

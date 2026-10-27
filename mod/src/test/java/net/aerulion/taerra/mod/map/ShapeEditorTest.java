package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.aerulion.taerra.mod.map.ShapeEditor.Kind;
import net.aerulion.taerra.mod.map.ShapeEditor.Tool;
import net.aerulion.taerra.mod.web.model.Regions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShapeEditorTest {

  private static final Regions OTHER =
      new Regions(
          List.of(),
          List.of(
              new Regions.Region(
                  "other",
                  "Other",
                  new int[] {0, 0},
                  "#3ab3da",
                  "hide",
                  true,
                  1,
                  List.of(new int[] {0, 0}))),
          true);

  private static Place at(final int x, final int z) {
    return new Place(x, 64, z);
  }

  private static void addPoint(final ShapeEditor editor, final int x, final int z) {
    editor.pressPoint(at(x, z), false, -1, -1);
    editor.releaseVertex();
  }

  private static void paint(final ShapeEditor editor, final int x, final int z) {
    editor.beginStroke(at(x, z), false);
    editor.endStroke();
  }

  @Test
  @DisplayName("points are added, undone and redone one at a time")
  void points() {
    final ShapeEditor editor = new ShapeEditor(() -> null);
    editor.start(Kind.LINE, null, null);
    assertEquals(Tool.POINTS, editor.tool());
    addPoint(editor, 0, 0);
    assertNull(editor.shape());
    addPoint(editor, 10, 0);
    assertEquals(2, editor.pointCount());
    assertTrue(editor.ready());

    assertTrue(editor.undo());
    assertEquals(1, editor.pointCount());
    assertTrue(editor.redo());
    assertEquals(2, editor.pointCount());
    assertFalse(editor.redo());

    assertTrue(editor.removeLastPoint());
    assertEquals(1, editor.pointCount());
  }

  @Test
  @DisplayName("clicking a point without moving it removes it, dragging it moves it")
  void vertices() {
    final ShapeEditor editor = new ShapeEditor(() -> null);
    editor.start(Kind.LINE, null, null);
    addPoint(editor, 0, 0);
    addPoint(editor, 10, 0);

    editor.pressPoint(at(10, 0), false, 1, -1);
    assertTrue(editor.moveVertex(at(20, 5)));
    assertFalse(editor.moveVertex(at(20, 5)));
    editor.releaseVertex();
    assertEquals(2, editor.pointCount());
    assertEquals(20.5, editor.shape().get(1)[0]);

    editor.pressPoint(at(0, 0), false, 0, -1);
    editor.releaseVertex();
    assertEquals(1, editor.pointCount());
    assertFalse(editor.movingVertex());
  }

  @Test
  @DisplayName("a brush stroke selects chunks as one undoable step")
  void brush() {
    final ShapeEditor editor = new ShapeEditor(() -> null);
    editor.start(Kind.CHUNKS, null, null);
    editor.beginStroke(at(0, 0), false);
    editor.stroke(at(40, 0));
    assertTrue(editor.stroking());
    assertTrue(editor.endStroke());
    assertEquals(2, editor.selection().size());
    assertTrue(editor.undo());
    assertTrue(editor.selection().isEmpty());
  }

  @Test
  @DisplayName("an area painted as one piece turns into its corner points, two pieces refuse")
  void brushToPoints() {
    final ShapeEditor editor = new ShapeEditor(() -> null);
    editor.start(Kind.AREA, null, null);
    paint(editor, 0, 0);
    assertTrue(editor.offers(Tool.POINTS));
    assertTrue(editor.setTool(Tool.POINTS));
    assertEquals(4, editor.pointCount());
    assertTrue(editor.selection().isEmpty());
    assertFalse(editor.canUndo());

    final ShapeEditor split = new ShapeEditor(() -> null);
    split.start(Kind.AREA, null, null);
    paint(split, 0, 0);
    paint(split, 100, 100);
    assertTrue(split.split());
    assertFalse(split.setTool(Tool.POINTS));
    assertEquals(Tool.BRUSH, split.tool());
  }

  @Test
  @DisplayName("a new region skips chunks of other regions and picks an unused colour")
  void regions() {
    final ShapeEditor editor = new ShapeEditor(() -> OTHER);
    editor.startRegion(null);
    assertEquals("#b02e26", editor.color());
    assertEquals("other", editor.owner(0, 0).id());
    paint(editor, 0, 0);
    assertTrue(editor.selection().isEmpty());
    paint(editor, 16, 0);
    assertEquals(1, editor.selection().size());
    assertTrue(editor.regionReady());
    assertNotNull(editor.drawing());
    assertEquals(1, editor.drawing().others().size());
  }

  @Test
  @DisplayName("the hover footprint only changes when the brush covers other chunks")
  void hover() {
    final ShapeEditor editor = new ShapeEditor(() -> null);
    editor.start(Kind.CHUNKS, null, null);
    assertTrue(editor.hover(at(0, 0), false));
    assertFalse(editor.hover(at(5, 5), false));
    assertTrue(editor.hover(at(5, 5), true));
    assertTrue(editor.hover(null, true));
  }
}

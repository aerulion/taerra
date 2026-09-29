package net.aerulion.taerra.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldLimitTest {

  @Test
  @DisplayName("a circle keeps its round edge where a square keeps the corner")
  void circleAndSquare() {
    final @NotNull WorldLimit circle = WorldLimit.circle(100, -50, 10);
    final @NotNull WorldLimit square = WorldLimit.square(100, -50, 10);

    assertTrue(circle.contains(110, -50));
    assertFalse(circle.contains(111, -50));
    assertFalse(circle.contains(108, -42), "corner of the bounding box");
    assertTrue(square.contains(110, -40));
    assertFalse(square.contains(110, -39));
  }

  @Test
  @DisplayName("areas touch the limit if any block lies inside, and are covered only if all do")
  void intersectsAndCovers() {
    final @NotNull WorldLimit circle = WorldLimit.circle(0, 0, 20);
    final @NotNull WorldLimit rectangle = WorldLimit.rectangle(15, 7, -3, -4);

    assertTrue(circle.intersects(10, 10, 40, 40));
    assertFalse(circle.intersects(15, 15, 40, 40), "only the bounding box overlaps");
    assertTrue(circle.covers(-5, -5, 5, 5));
    assertFalse(circle.covers(0, 0, 15, 15));
    assertTrue(rectangle.covers(-3, -4, 15, 7));
    assertFalse(rectangle.intersects(16, 0, 20, 0));
  }
}

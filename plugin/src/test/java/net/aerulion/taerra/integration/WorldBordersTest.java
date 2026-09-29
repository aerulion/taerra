package net.aerulion.taerra.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorldBordersTest {

  @Test
  @DisplayName("ellipse outlines stay within half a block of the curve at any radius")
  void ellipseSegmentsBoundTheError() {
    for (final double radius : new double[] {0.1, 5, 100, 10_000, 1_000_000, 29_999_984}) {
      final int segments = WorldBorders.ellipseSegments(radius);
      assertTrue(radius * (1 - Math.cos(Math.PI / segments)) <= 0.5, "radius " + radius);
      assertTrue(segments >= 8, "radius " + radius);
    }
  }
}

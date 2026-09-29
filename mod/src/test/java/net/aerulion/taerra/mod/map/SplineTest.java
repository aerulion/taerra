package net.aerulion.taerra.mod.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class SplineTest {

  @Test
  void runsThroughItsPoints() {
    final @NotNull List<double[]> points =
        Spline.through(List.of(new double[] {0, 64, 0}, new double[] {10, 64, 0}));
    assertArrayEquals(new double[] {0, 64, 0}, points.getFirst());
    assertArrayEquals(new double[] {10, 64, 0}, points.getLast(), 1e-9);
    assertEquals(6, points.size());
    assertArrayEquals(new double[] {3.76, 64, 0}, points.get(2), 1e-9);
  }
}

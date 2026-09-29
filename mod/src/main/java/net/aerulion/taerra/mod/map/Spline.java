package net.aerulion.taerra.mod.map;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class Spline {

  private static final double BLOCKS_PER_STEP = 2;
  private static final int MAX_STEPS = 32;

  private Spline() {}

  public static @NotNull List<double[]> through(final @NotNull List<double[]> points) {
    final @NotNull List<double[]> out = new ArrayList<>();
    out.add(points.getFirst());
    for (int i = 0; i < points.size() - 1; i++) {
      final double[] p0 = points.get(Math.max(0, i - 1));
      final double[] p1 = points.get(i);
      final double[] p2 = points.get(i + 1);
      final double[] p3 = points.get(Math.min(points.size() - 1, i + 2));
      final int steps =
          Math.min(MAX_STEPS, Math.max(1, (int) Math.ceil(distance(p1, p2) / BLOCKS_PER_STEP)));
      for (int step = 1; step <= steps; step++) {
        final double s = (double) step / steps;
        final double @NotNull [] point = new double[3];
        for (int k = 0; k < 3; k++) {
          point[k] =
              .5
                  * (2 * p1[k]
                      + (p2[k] - p0[k]) * s
                      + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * s * s
                      + (3 * p1[k] - p0[k] - 3 * p2[k] + p3[k]) * s * s * s);
        }
        out.add(point);
      }
    }
    return out;
  }

  public static double distance(final double @NotNull [] a, final double @NotNull [] b) {
    return Math.sqrt(
        Math.pow(b[0] - a[0], 2) + Math.pow(b[1] - a[1], 2) + Math.pow(b[2] - a[2], 2));
  }
}

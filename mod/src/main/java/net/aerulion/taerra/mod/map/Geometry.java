package net.aerulion.taerra.mod.map;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2fStack;

final class Geometry {

  private Geometry() {}

  static void line(
      final @NotNull GuiGraphicsExtractor graphics,
      final double @NotNull [] from,
      final double @NotNull [] to,
      final int width,
      final int color) {
    final double dx = to[0] - from[0];
    final double dy = to[1] - from[1];
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate((float) from[0], (float) from[1]);
    pose.rotate((float) Math.atan2(dy, dx));
    pose.scale((float) Math.hypot(dx, dy), 1);
    graphics.fill(0, -width / 2, 1, width - width / 2, color);
    pose.popMatrix();
  }

  static void ring(
      final @NotNull GuiGraphicsExtractor graphics,
      final double @NotNull [][] points,
      final int width,
      final int color) {
    for (int i = 0; i < points.length; i++) {
      line(graphics, points[i], points[(i + 1) % points.length], width, color);
    }
  }

  static void fillPolygon(
      final @NotNull GuiGraphicsExtractor graphics,
      final double @NotNull [][] points,
      final int top,
      final int bottom,
      final int color,
      final double shiftX,
      final double shiftY,
      final double scale) {
    final double @NotNull [][] scaled = new double[points.length][];
    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    for (int i = 0; i < points.length; i++) {
      scaled[i] = new double[] {(points[i][0] + shiftX) * scale, (points[i][1] + shiftY) * scale};
      min = Math.min(min, scaled[i][1]);
      max = Math.max(max, scaled[i][1]);
    }
    final @NotNull Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate((float) -shiftX, (float) -shiftY);
    pose.scale((float) (1 / scale));
    final double @NotNull [] crossings = new double[points.length];
    int @NotNull [] run = new int[0];
    int runTop = 0;
    final int last = (int) Math.min(Math.ceil((bottom + shiftY) * scale), Math.ceil(max));
    final int first = (int) Math.max(Math.floor((top + shiftY) * scale), Math.floor(min));
    for (int y = first; y <= last; y++) {
      int @NotNull [] spans = new int[0];
      if (y < last) {
        final int count = crossings(scaled, y + .5, crossings);
        Arrays.sort(crossings, 0, count);
        spans = new int[count & ~1];
        for (int i = 0; i < spans.length; i++) {
          spans[i] = (int) Math.round(crossings[i]);
        }
      }
      if (!Arrays.equals(spans, run)) {
        for (int i = 0; i + 1 < run.length; i += 2) {
          graphics.fill(run[i], runTop, run[i + 1], y, color);
        }
        run = spans;
        runTop = y;
      }
    }
    pose.popMatrix();
  }

  static boolean contains(final double @NotNull [][] points, final double x, final double y) {
    final double @NotNull [] crossings = new double[points.length];
    final int count = crossings(points, y, crossings);
    int left = 0;
    for (int i = 0; i < count; i++) {
      if (crossings[i] < x) {
        left++;
      }
    }
    return left % 2 == 1;
  }

  static boolean near(
      final double @NotNull [] a,
      final double @NotNull [] b,
      final double x,
      final double y,
      final double reach) {
    final double dx = b[0] - a[0];
    final double dy = b[1] - a[1];
    final double length = dx * dx + dy * dy;
    final double t =
        length == 0 ? 0 : Math.clamp(((x - a[0]) * dx + (y - a[1]) * dy) / length, 0, 1);
    return Math.hypot(a[0] + t * dx - x, a[1] + t * dy - y) <= reach;
  }

  static boolean nearPath(
      final double @NotNull [][] points, final double x, final double y, final double reach) {
    for (int i = 0; i + 1 < points.length; i++) {
      if (near(points[i], points[i + 1], x, y, reach)) {
        return true;
      }
    }
    return false;
  }

  static boolean nearRing(
      final double @NotNull [][] points, final double x, final double y, final double reach) {
    for (int i = 0; i < points.length; i++) {
      if (near(points[i], points[(i + 1) % points.length], x, y, reach)) {
        return true;
      }
    }
    return false;
  }

  static double signedArea(final double @NotNull [][] ring) {
    double sum = 0;
    for (int i = 0; i < ring.length; i++) {
      final double[] next = ring[(i + 1) % ring.length];
      sum += ring[i][0] * next[1] - next[0] * ring[i][1];
    }
    return sum;
  }

  static int crossings(final double @NotNull [][] points, final double y, final double[] into) {
    int count = 0;
    for (int i = 0, j = points.length - 1; i < points.length; j = i++) {
      final double[] a = points[i];
      final double[] b = points[j];
      if ((a[1] > y) != (b[1] > y)) {
        into[count] = a[0] + (y - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
        count++;
      }
    }
    return count;
  }
}

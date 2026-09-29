package net.aerulion.taerra.mod.map;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import net.aerulion.taerra.mod.util.Colors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class OverlayRaster {

  static final int SIZE = 256;
  static final int GROUND_LINE = 0xE6;
  static final int FAR_LINE = 0xB3;
  static final int DETAIL_ZOOM_RANGE = 2;
  static final double TINY = 3;
  private static final int MAX_LINE = 3;

  private OverlayRaster() {}

  static boolean far(final int maxZoom, final int level) {
    return maxZoom - level >= DETAIL_ZOOM_RANGE;
  }

  static double guiPerUnit(final int level, final double pixels) {
    return Math.scalb(1.0, level) / pixels;
  }

  static @Nullable NativeImage render(
      final @NotNull OverlayScene scene, final @NotNull OverlayKey key) {
    final int @Nullable [] pixels = pixels(scene, key);
    if (pixels == null) {
      return null;
    }
    final @NotNull NativeImage image = new NativeImage(SIZE, SIZE, true);
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        final int pixel = pixels[y * SIZE + x];
        if (pixel != 0) {
          image.setPixel(x, y, pixel);
        }
      }
    }
    return image;
  }

  static int @Nullable [] pixels(final @NotNull OverlayScene scene, final @NotNull OverlayKey key) {
    final double units = Projection.tileUnits(key.level());
    final @NotNull Canvas canvas =
        new Canvas(key.x() * units, key.y() * units, SIZE / units, scene.pixels());
    final double margin = MAX_LINE * scene.pixels() / canvas.scale;
    final double gui = guiPerUnit(key.level(), scene.pixels());
    final boolean far = far(scene.maxZoom(), key.level());
    for (final @NotNull OverlayItem item : scene.items()) {
      final double[] bounds = item.bounds();
      if (bounds[2] < canvas.originX - margin
          || bounds[0] > canvas.originX + units + margin
          || bounds[3] < canvas.originY - margin
          || bounds[1] > canvas.originY + units + margin) {
        continue;
      }
      draw(canvas, scene, item, far, item.extent() * gui < TINY);
    }
    return canvas.drawn ? canvas.argb : null;
  }

  private static void draw(
      final @NotNull Canvas canvas,
      final @NotNull OverlayScene scene,
      final @NotNull OverlayItem item,
      final boolean far,
      final boolean tiny) {
    final int color = item.color();
    if (item.path() != null) {
      canvas.lines(item.path(), false, 2, color);
      return;
    }
    if (tiny) {
      canvas.dot(item.bounds(), Colors.withAlpha(FAR_LINE, color));
      return;
    }
    final Look look = scene.look();
    if (!scene.projection().isometric()) {
      region(canvas, look, item, item.face(), far);
      return;
    }
    final double @Nullable [][] ground = item.ground();
    if (ground == null) {
      return;
    }
    if (far || item.fullHeight()) {
      region(canvas, look, item, ground, far);
      return;
    }
    if (!item.hollow()) {
      canvas.fill(ground, Colors.withAlpha(look.ground(), color));
    }
    canvas.lines(ground, true, 2, Colors.withAlpha(GROUND_LINE, color));
    for (int i = 0; i < item.walls().size(); i++) {
      final double[][] wall = item.walls().get(i);
      canvas.fill(wall, Colors.withAlpha(look.wall(), item.wallColors()[i]));
      canvas.lines(wall, true, 1, Colors.withAlpha(look.wallLine(), color));
    }
    region(canvas, look, item, item.face(), false);
  }

  private static void region(
      final @NotNull Canvas canvas,
      final @NotNull Look look,
      final @NotNull OverlayItem item,
      final double @NotNull [][] outline,
      final boolean far) {
    final int color = item.color();
    if (!item.hollow()) {
      canvas.fill(outline, Colors.withAlpha(look.fill(), color));
    }
    canvas.lines(outline, true, far ? 1 : 2, far ? Colors.withAlpha(FAR_LINE, color) : color);
  }

  private static final class Canvas {

    private final int[] argb = new int[SIZE * SIZE];
    private final double originX;
    private final double originY;
    private final double scale;
    private final double pixels;
    private boolean drawn;

    private Canvas(
        final double originX, final double originY, final double scale, final double pixels) {
      this.originX = originX;
      this.originY = originY;
      this.scale = scale;
      this.pixels = pixels;
    }

    private double[] texel(final double @NotNull [] point) {
      return new double[] {
        (point[0] - this.originX) * this.scale, (point[1] - this.originY) * this.scale
      };
    }

    private void fill(final double @NotNull [][] outline, final int color) {
      final double @NotNull [][] texels = new double[outline.length][];
      for (int i = 0; i < outline.length; i++) {
        texels[i] = texel(outline[i]);
      }
      fillTexels(texels, color);
    }

    private void lines(
        final double @NotNull [][] points, final boolean closed, final int width, final int color) {
      final int count = closed ? points.length : points.length - 1;
      final double low = -(width / 2) * this.pixels;
      final double high = (width - width / 2) * this.pixels;
      for (int i = 0; i < count; i++) {
        final double @NotNull [] a = texel(points[i]);
        final double @NotNull [] b = texel(points[(i + 1) % points.length]);
        final double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        if (length == 0) {
          continue;
        }
        final double normalX = -(b[1] - a[1]) / length;
        final double normalY = (b[0] - a[0]) / length;
        fillTexels(
            new double[][] {
              {a[0] + normalX * low, a[1] + normalY * low},
              {b[0] + normalX * low, b[1] + normalY * low},
              {b[0] + normalX * high, b[1] + normalY * high},
              {a[0] + normalX * high, a[1] + normalY * high}
            },
            color);
      }
    }

    private void dot(final double @NotNull [] bounds, final int color) {
      final double @NotNull [] from = texel(new double[] {bounds[0], bounds[1]});
      final double @NotNull [] to = texel(new double[] {bounds[2], bounds[3]});
      final double right = Math.max(to[0], from[0] + 1);
      final double bottom = Math.max(to[1], from[1] + 1);
      fillTexels(
          new double[][] {{from[0], from[1]}, {right, from[1]}, {right, bottom}, {from[0], bottom}},
          color);
    }

    private void fillTexels(final double @NotNull [][] points, final int color) {
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;
      for (final double[] point : points) {
        min = Math.min(min, point[1]);
        max = Math.max(max, point[1]);
      }
      final int first = (int) Math.max(0, Math.floor(min));
      final int last = (int) Math.min(SIZE, Math.ceil(max));
      final double @NotNull [] crossings = new double[points.length];
      for (int y = first; y < last; y++) {
        final int count = Geometry.crossings(points, y + .5, crossings);
        Arrays.sort(crossings, 0, count);
        for (int i = 0; i + 1 < count; i += 2) {
          final int from = (int) Math.max(0, Math.round(crossings[i]));
          final int to = (int) Math.min(SIZE, Math.round(crossings[i + 1]));
          for (int x = from; x < to; x++) {
            blend(y * SIZE + x, color);
          }
        }
      }
    }

    private void blend(final int index, final int source) {
      final int sourceAlpha = source >>> 24;
      if (sourceAlpha == 0) {
        return;
      }
      this.drawn = true;
      final int target = this.argb[index];
      final int targetAlpha = target >>> 24;
      if (sourceAlpha == 255 || targetAlpha == 0) {
        this.argb[index] = source;
        return;
      }
      final double a = sourceAlpha / 255.0;
      final double b = targetAlpha / 255.0 * (1 - a);
      final double alpha = a + b;
      int result = (int) Math.round(alpha * 255) << 24;
      for (int shift = 0; shift <= 16; shift += 8) {
        final double channel =
            (((source >> shift) & 0xFF) * a + ((target >> shift) & 0xFF) * b) / alpha;
        result |= (int) Math.round(channel) << shift;
      }
      this.argb[index] = result;
    }
  }
}

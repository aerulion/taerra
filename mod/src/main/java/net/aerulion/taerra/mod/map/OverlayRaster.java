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
  private static final int MUTED = 0xB3;
  static final int DETAIL_ZOOM_RANGE = 2;
  static final double TINY = 3;
  private static final int MAX_LINE = 3;
  private static final double DASH = 5;
  private static final double GAP = 3;

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
      canvas.lines(item.path(), false, 2, faded(item, color), item.dashed());
      return;
    }
    if (tiny) {
      canvas.dot(item.bounds(), faded(item, Colors.withAlpha(FAR_LINE, color)));
      return;
    }
    final Look look = scene.look();
    if (!scene.projection().isometric()) {
      region(canvas, look, item, Geometry.rings(item.face(), item.faceHoles()), far);
      return;
    }
    final double @Nullable [][] ground = item.ground();
    if (ground == null) {
      return;
    }
    final double @NotNull [][][] grounds = Geometry.rings(ground, item.groundHoles());
    if (far || item.fullHeight()) {
      region(canvas, look, item, grounds, far);
      return;
    }
    if (!item.hollow()) {
      canvas.fill(grounds, faded(item, Colors.withAlpha(look.ground(), color)));
    }
    for (final double @NotNull [][] ring : grounds) {
      canvas.lines(ring, true, 2, faded(item, Colors.withAlpha(GROUND_LINE, color)), item.dashed());
    }
    for (int i = 0; i < item.walls().size(); i++) {
      final double[][] wall = item.walls().get(i);
      canvas.fill(wall, faded(item, Colors.withAlpha(look.wall(), item.wallColors()[i])));
      canvas.lines(wall, true, 1, faded(item, Colors.withAlpha(look.wallLine(), color)), false);
    }
    region(canvas, look, item, Geometry.rings(item.face(), item.faceHoles()), false);
  }

  private static void region(
      final @NotNull Canvas canvas,
      final @NotNull Look look,
      final @NotNull OverlayItem item,
      final double @NotNull [][][] rings,
      final boolean far) {
    final int color = item.color();
    if (!item.hollow()) {
      canvas.fill(rings, faded(item, Colors.withAlpha(look.fill(), color)));
    }
    for (final double @NotNull [][] ring : rings) {
      canvas.lines(
          ring,
          true,
          far ? 1 : 2,
          faded(item, far ? Colors.withAlpha(FAR_LINE, color) : color),
          item.dashed());
    }
  }

  private static int faded(final @NotNull OverlayItem item, final int color) {
    return item.muted() ? Colors.withAlpha((color >>> 24) * MUTED / 0xFF, color) : color;
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
      fill(new double[][][] {outline}, color);
    }

    private void fill(final double @NotNull [][][] rings, final int color) {
      final double @NotNull [][][] texels = new double[rings.length][][];
      for (int ring = 0; ring < rings.length; ring++) {
        texels[ring] = new double[rings[ring].length][];
        for (int i = 0; i < rings[ring].length; i++) {
          texels[ring][i] = texel(rings[ring][i]);
        }
      }
      fillTexels(texels, color);
    }

    private void lines(
        final double @NotNull [][] points,
        final boolean closed,
        final int width,
        final int color,
        final boolean dashed) {
      final int count = closed ? points.length : points.length - 1;
      final double low = -(width / 2) * this.pixels;
      final double high = (width - width / 2) * this.pixels;
      final double dash = DASH * this.pixels;
      final double period = (DASH + GAP) * this.pixels;
      double along = 0;
      for (int i = 0; i < count; i++) {
        final double @NotNull [] a = texel(points[i]);
        final double @NotNull [] b = texel(points[(i + 1) % points.length]);
        final double length = Math.hypot(b[0] - a[0], b[1] - a[1]);
        if (length == 0) {
          continue;
        }
        final double dirX = (b[0] - a[0]) / length;
        final double dirY = (b[1] - a[1]) / length;
        double from = 0;
        while (from < length) {
          final double phase = dashed ? (along + from) % period : 0;
          final double to =
              dashed ? Math.min(length, from + (phase < dash ? dash : period) - phase) : length;
          if (phase < dash) {
            final double startX = a[0] + dirX * from;
            final double startY = a[1] + dirY * from;
            final double endX = a[0] + dirX * to;
            final double endY = a[1] + dirY * to;
            fillTexels(
                new double[][] {
                  {startX - dirY * low, startY + dirX * low},
                  {endX - dirY * low, endY + dirX * low},
                  {endX - dirY * high, endY + dirX * high},
                  {startX - dirY * high, startY + dirX * high}
                },
                color);
          }
          from = to;
        }
        along += length;
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
      fillTexels(new double[][][] {points}, color);
    }

    private void fillTexels(final double @NotNull [][][] rings, final int color) {
      double min = Double.MAX_VALUE;
      double max = -Double.MAX_VALUE;
      for (final double[][] points : rings) {
        for (final double[] point : points) {
          min = Math.min(min, point[1]);
          max = Math.max(max, point[1]);
        }
      }
      final int first = (int) Math.max(0, Math.floor(min));
      final int last = (int) Math.min(SIZE, Math.ceil(max));
      final double @NotNull [] crossings = new double[Geometry.corners(rings)];
      for (int y = first; y < last; y++) {
        final int count = Geometry.crossings(rings, y + .5, crossings);
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

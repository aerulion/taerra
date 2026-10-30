package net.aerulion.taerra.mod.map;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.Marker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MarkerShape {

  private static final double FALLBACK_GROUND = 64;

  final int color;
  final List<double[][]> walls = new ArrayList<>();
  double @Nullable [] point;
  double @Nullable [][] path;
  double @Nullable [][] face;
  List<double[][]> faceHoles = List.of();
  double @Nullable [][] ground;
  List<double[][]> groundHoles = List.of();
  @Nullable String groundKey;
  int[] wallColors = new int[0];
  private double minX = Double.MAX_VALUE;
  private double minY = Double.MAX_VALUE;
  private double maxX = -Double.MAX_VALUE;
  private double maxY = -Double.MAX_VALUE;
  private double length;
  private @Nullable List<Component> tooltip;

  MarkerShape(final @NotNull Marker marker, final @NotNull Projection projection) {
    this.color = Colors.argb(marker.color());
    switch (marker.type()) {
      case "point" -> {
        final @Nullable List<Double> at = marker.position();
        if (at != null && at.size() >= 3) {
          this.point = include(projection.toMap(at.get(0), at.get(1), at.get(2)));
        }
      }
      case "line" -> {
        if (marker.points() != null && marker.points().size() >= 2) {
          @NotNull
          List<double[]> points =
              marker.points().stream()
                  .map(point -> new double[] {point.get(0), point.get(1), point.get(2)})
                  .toList();
          if (marker.smooth()) {
            points = Spline.through(points);
          }
          for (int i = 1; i < points.size(); i++) {
            this.length += Spline.distance(points.get(i - 1), points.get(i));
          }
          this.path =
              points.stream()
                  .map(point -> include(projection.toMap(point[0], point[1], point[2])))
                  .toArray(double[][]::new);
        }
      }
      case "area" -> {
        final @Nullable List<List<Double>> outline = marker.points();
        if (outline != null && outline.size() >= 3) {
          final double y = projection.isometric() ? marker.maxY() : 0;
          if (projection.isometric()) {
            outline(projection, outline, marker.minY());
          }
          this.face = outline(projection, outline, y);
          this.faceHoles =
              holes(marker).stream().map(hole -> outline(projection, hole, y)).toList();
        }
      }
      default -> {}
    }
  }

  private static double standing(final @NotNull Marker marker, final double ground) {
    final double fallback = marker.fullHeight() ? FALLBACK_GROUND : marker.minY();
    return Math.min(
        marker.maxY(), Math.max(marker.minY(), Double.isNaN(ground) ? fallback : ground));
  }

  private static @Nullable Component facts(
      final @NotNull Marker marker, final double length, final boolean flat) {
    return switch (marker.type()) {
      case "point" ->
          marker.position() == null || marker.position().size() < 3
              ? null
              : Component.literal(
                  (long) Math.floor(marker.position().get(0))
                      + ", "
                      + (long) Math.floor(marker.position().get(1))
                      + ", "
                      + (long) Math.floor(marker.position().get(2)));
      case "line" ->
          Component.translatable(
              Math.round(length) == 1 ? "taerra.marker.block" : "taerra.marker.blocks",
              Math.round(length));
      case "area" -> {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final @NotNull List<Double> point : marker.points()) {
          minX = Math.min(minX, point.get(0));
          maxX = Math.max(maxX, point.get(0));
          minZ = Math.min(minZ, point.get(1));
          maxZ = Math.max(maxZ, point.get(1));
        }
        if (flat) {
          yield Component.literal(Math.round(maxX - minX) + " × " + Math.round(maxZ - minZ));
        }
        final double top =
            marker.maxY() == Math.rint(marker.maxY())
                ? marker.maxY() - 1
                : Math.floor(marker.maxY());
        yield Component.literal(
            Math.round(maxX - minX)
                + " × "
                + Math.round(marker.maxY() - marker.minY())
                + " × "
                + Math.round(maxZ - minZ)
                + "\nY "
                + Math.round(marker.minY())
                + " – "
                + Math.round(top));
      }
      default -> null;
    };
  }

  double extent() {
    return Math.max(this.maxX - this.minX, this.maxY - this.minY);
  }

  double[] bounds() {
    return new double[] {this.minX, this.minY, this.maxX, this.maxY};
  }

  boolean near(final double x, final double y, final double reach) {
    return x >= this.minX - reach
        && x <= this.maxX + reach
        && y >= this.minY - reach
        && y <= this.maxY + reach;
  }

  boolean reaches(final double @NotNull [] view) {
    return this.maxX >= view[0]
        && this.minX <= view[2]
        && this.maxY >= view[1]
        && this.minY <= view[3];
  }

  @NotNull
  List<Component> tooltip(final @NotNull Layer layer, final @NotNull Marker marker) {
    if (this.tooltip == null) {
      final @NotNull List<Component> lines = new ArrayList<>();
      lines.add(Component.literal(marker.label() == null ? marker.id() : marker.label()));
      final @Nullable Component facts = facts(marker, this.length, "regions".equals(layer.id()));
      if (facts != null) {
        for (final @NotNull String line : facts.getString().split("\n")) {
          lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
      }
      if (marker.discovered() != null) {
        lines.add(
            Component.literal("\u2726 ")
                .append(
                    Component.translatable("taerra.region.discovered", date(marker.discovered())))
                .withStyle(ChatFormatting.GOLD));
      }
      if (marker.description() != null) {
        for (final @NotNull String line : marker.description().split("\n")) {
          lines.add(Component.literal(line));
        }
      }
      if (marker.details() != null) {
        for (final @NotNull List<String> row : marker.details()) {
          if (row.size() >= 2) {
            lines.add(
                Component.literal(row.get(0) + ": " + row.get(1)).withStyle(ChatFormatting.GRAY));
          }
        }
      }
      final @NotNull MutableComponent where =
          layer.displayName().copy().withStyle(ChatFormatting.DARK_GRAY);
      if (layer.personal()) {
        where.append(Component.literal(" " + MapIcons.PERSONAL).withStyle(ChatFormatting.GOLD));
      }
      lines.add(where);
      this.tooltip = lines;
    }
    return this.tooltip;
  }

  private static @NotNull String date(final long epochMillis) {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
        .withLocale(
            Locale.forLanguageTag(Minecraft.getInstance().options.languageCode.replace('_', '-')))
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()));
  }

  static @NotNull List<List<List<Double>>> holes(final @NotNull Marker marker) {
    return marker.holes() == null ? List.of() : marker.holes();
  }

  void stand(
      final @NotNull Marker marker,
      final @NotNull List<List<List<double[]>>> rings,
      final @NotNull Projection projection) {
    final @NotNull List<List<double[][]>> bases = new ArrayList<>();
    for (final @NotNull List<List<double[]>> sides : rings) {
      final @NotNull List<double[][]> base = new ArrayList<>();
      for (final @NotNull List<double[]> side : sides) {
        base.add(
            side.stream()
                .map(p -> projection.toMap(p[0], standing(marker, p[1]), p[2]))
                .toArray(double[][]::new));
      }
      bases.add(base);
    }
    this.minX = Double.MAX_VALUE;
    this.minY = Double.MAX_VALUE;
    this.maxX = -Double.MAX_VALUE;
    this.maxY = -Double.MAX_VALUE;
    this.ground =
        bases.getFirst().stream()
            .flatMap(Arrays::stream)
            .map(this::include)
            .toArray(double[][]::new);
    this.groundHoles =
        bases.subList(1, bases.size()).stream()
            .map(base -> base.stream().flatMap(Arrays::stream).toArray(double[][]::new))
            .toList();
    if (marker.fullHeight()) {
      return;
    }
    Arrays.stream(this.face).forEach(this::include);
    final @NotNull List<List<List<Double>>> outlines = new ArrayList<>();
    outlines.add(marker.points());
    outlines.addAll(holes(marker));
    final @NotNull List<Integer> colors = new ArrayList<>();
    for (int ring = 0; ring < rings.size(); ring++) {
      final List<List<double[]>> sides = rings.get(ring);
      final double @NotNull [][] flat =
          outlines.get(ring).stream()
              .map(point -> projection.toMap(point.get(0), 0, point.get(1)))
              .toArray(double[][]::new);
      final double winding = Math.signum(Geometry.signedArea(flat));
      for (int i = 0; i < sides.size(); i++) {
        final int next = (i + 1) % flat.length;
        final double[] first = sides.get(i).getFirst();
        final double[] last = sides.get(i).getLast();
        final double @NotNull [][] slice = {
          flat[i],
          flat[next],
          projection.toMap(last[0], 1, last[2]),
          projection.toMap(first[0], 1, first[2])
        };
        if ((Math.signum(Geometry.signedArea(slice)) == winding) == ring > 0) {
          continue;
        }
        final boolean facesLeft = (flat[next][1] - flat[i][1]) * (flat[next][0] - flat[i][0]) > 0;
        final @NotNull List<double[]> crest = new ArrayList<>(sides.get(i)).reversed();
        this.walls.add(
            Stream.concat(
                    Arrays.stream(bases.get(ring).get(i)),
                    crest.stream().map(p -> projection.toMap(p[0], marker.maxY(), p[2])))
                .toArray(double[][]::new));
        colors.add(Colors.shade(this.color, facesLeft ? .55 : .8));
      }
    }
    this.wallColors = colors.stream().mapToInt(Integer::intValue).toArray();
  }

  private double[] include(final double @NotNull [] point) {
    this.minX = Math.min(this.minX, point[0]);
    this.minY = Math.min(this.minY, point[1]);
    this.maxX = Math.max(this.maxX, point[0]);
    this.maxY = Math.max(this.maxY, point[1]);
    return point;
  }

  private double[][] outline(
      final @NotNull Projection projection,
      final @NotNull List<List<Double>> points,
      final double y) {
    return points.stream()
        .map(point -> include(projection.toMap(point.get(0), y, point.get(1))))
        .toArray(double[][]::new);
  }
}

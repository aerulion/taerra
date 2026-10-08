package net.aerulion.taerra.mod.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.aerulion.taerra.mod.util.Colors;
import net.aerulion.taerra.mod.web.model.Layer;
import net.aerulion.taerra.mod.web.model.Marker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MarkerShape {

  private static final double FALLBACK_GROUND = 64;

  final int color;
  final List<double[][]> walls = new ArrayList<>();
  double @Nullable [] point;
  double @Nullable [][] path;
  double @Nullable [][] face;
  double @Nullable [][] ground;
  @Nullable String groundKey;
  int[] wallColors = new int[0];
  private double minX = Double.MAX_VALUE;
  private double minY = Double.MAX_VALUE;
  private double maxX = -Double.MAX_VALUE;
  private double maxY = -Double.MAX_VALUE;
  private double length;
  private @Nullable TextureAtlasSprite banner;
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
          if (projection.isometric()) {
            outline(projection, outline, marker.minY());
            this.face = outline(projection, outline, marker.maxY());
          } else {
            this.face = outline(projection, outline, 0);
          }
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

  private static @Nullable Component facts(final @NotNull Marker marker, final double length) {
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
                + " · Y "
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
  TextureAtlasSprite banner() {
    if (this.banner == null) {
      this.banner = MapIcons.banner(this.color);
    }
    return this.banner;
  }

  @NotNull
  List<Component> tooltip(final @NotNull Layer layer, final @NotNull Marker marker) {
    if (this.tooltip == null) {
      final @NotNull List<Component> lines = new ArrayList<>();
      lines.add(Component.literal(marker.label() == null ? marker.id() : marker.label()));
      final @Nullable Component facts = facts(marker, this.length);
      if (facts != null) {
        lines.add(facts.copy().withStyle(ChatFormatting.GRAY));
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
      lines.add(layer.displayName().copy().withStyle(ChatFormatting.DARK_GRAY));
      this.tooltip = lines;
    }
    return this.tooltip;
  }

  void stand(
      final @NotNull Marker marker,
      final @NotNull List<List<double[]>> sides,
      final @NotNull Projection projection) {
    final @NotNull List<double[][]> base = new ArrayList<>();
    for (final @NotNull List<double[]> side : sides) {
      base.add(
          side.stream()
              .map(p -> projection.toMap(p[0], standing(marker, p[1]), p[2]))
              .toArray(double[][]::new));
    }
    this.minX = Double.MAX_VALUE;
    this.minY = Double.MAX_VALUE;
    this.maxX = -Double.MAX_VALUE;
    this.maxY = -Double.MAX_VALUE;
    this.ground = base.stream().flatMap(Arrays::stream).map(this::include).toArray(double[][]::new);
    if (marker.fullHeight()) {
      return;
    }
    Arrays.stream(this.face).forEach(this::include);
    final double @NotNull [][] flat =
        marker.points().stream()
            .map(point -> projection.toMap(point.get(0), 0, point.get(1)))
            .toArray(double[][]::new);
    final double winding = Math.signum(Geometry.signedArea(flat));
    final @NotNull List<Integer> colors = new ArrayList<>();
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
      if (Math.signum(Geometry.signedArea(slice)) != winding) {
        continue;
      }
      final boolean facesLeft = (flat[next][1] - flat[i][1]) * (flat[next][0] - flat[i][0]) > 0;
      final @NotNull List<double[]> crest = new ArrayList<>(sides.get(i)).reversed();
      this.walls.add(
          Stream.concat(
                  Arrays.stream(base.get(i)),
                  crest.stream().map(p -> projection.toMap(p[0], marker.maxY(), p[2])))
              .toArray(double[][]::new));
      colors.add(Colors.shade(this.color, facesLeft ? .55 : .8));
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

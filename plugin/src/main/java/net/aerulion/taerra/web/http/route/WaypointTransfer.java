package net.aerulion.taerra.web.http.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.LineMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.util.HexColors;
import net.aerulion.taerra.util.Names;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import net.aerulion.taerra.waypoint.Waypoints;
import net.aerulion.taerra.world.WorldPositions;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class WaypointTransfer {

  static final int MAX_SHAPE_POINTS = 4096;
  private static final @NotNull Color DEFAULT_COLOR = Color.fromRGB(0x3AB3DA);

  private WaypointTransfer() {
    super();
  }

  static @NotNull Map<String, Object> json(final PersonalWaypoints.@NotNull Saved saved) {
    final @NotNull Marker marker = saved.marker();
    final @NotNull Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("name", marker.label());
    entry.put("world", marker.world().asString());
    switch (marker) {
      case final @NotNull PointMarker point -> {
        entry.put("type", "point");
        entry.put("x", (int) Math.floor(point.position().getX()));
        entry.put("y", (int) Math.floor(point.position().getY()));
        entry.put("z", (int) Math.floor(point.position().getZ()));
        if (point.icon() != null) {
          entry.put("icon", point.icon().asString());
        }
      }
      case final @NotNull AreaMarker area -> {
        entry.put("type", "area");
        entry.put(
            "points", area.outline().stream().map(at -> List.of(at.getX(), at.getZ())).toList());
      }
      case final @NotNull LineMarker line -> {
        entry.put("type", "line");
        entry.put(
            "points",
            line.points().stream().map(at -> List.of(at.getX(), at.getY(), at.getZ())).toList());
        entry.put("smooth", line.smooth());
      }
    }
    entry.put("color", HexColors.format(marker.color().asRGB()));
    if (marker.description() != null) {
      entry.put("description", marker.description());
    }
    if (saved.hidden()) {
      entry.put("hidden", true);
    }
    return entry;
  }

  static @Nullable String world(final @NotNull Map<?, ?> entry) {
    return entry.get("world") instanceof final @NotNull String world ? world : null;
  }

  static PersonalWaypoints.@Nullable Saved saved(
      final @NotNull Map<?, ?> entry, final @NotNull World world) {
    final @Nullable String name =
        entry.get("name") instanceof final @NotNull String text ? Names.of(text) : null;
    if (name == null) {
      return null;
    }
    final @NotNull String id = PersonalWaypoints.markerId(name);
    final @Nullable Marker shape =
        switch (entry.get("type") instanceof final @NotNull String type ? type : "point") {
          case "point" -> point(entry, world, id, name);
          case "area" -> area(entry, world, id, name);
          case "line" -> line(entry, world, id, name);
          default -> null;
        };
    if (shape == null) {
      return null;
    }
    final @NotNull OptionalInt rgb =
        HexColors.parseRgb(
            entry.get("color") instanceof final @NotNull String color ? color : null);
    final @Nullable NamespacedKey icon =
        entry.get("icon") instanceof final @NotNull String key
            ? NamespacedKey.fromString(key)
            : null;
    return new PersonalWaypoints.Saved(
        (shape instanceof final @NotNull PointMarker point ? point.withIcon(icon) : shape)
            .withColor(rgb.isPresent() ? Color.fromRGB(rgb.getAsInt()) : DEFAULT_COLOR)
            .withDescription(
                Names.description(
                    entry.get("description") instanceof final @NotNull String text ? text : null)),
        Boolean.TRUE.equals(entry.get("hidden")));
  }

  private static @Nullable Marker point(
      final @NotNull Map<?, ?> entry,
      final @NotNull World world,
      final @NotNull String id,
      final @NotNull String name) {
    if (!(entry.get("x") instanceof final @NotNull Number x)
        || !(entry.get("y") instanceof final @NotNull Number y)
        || !(entry.get("z") instanceof final @NotNull Number z)
        || !Double.isFinite(x.doubleValue())
        || !Double.isFinite(y.doubleValue())
        || !Double.isFinite(z.doubleValue())) {
      return null;
    }
    final int blockX = (int) Math.floor(x.doubleValue());
    final int blockZ = (int) Math.floor(z.doubleValue());
    if (!WorldPositions.insideBorder(world, blockX, blockZ)) {
      return null;
    }
    final int blockY =
        Math.clamp((long) Math.floor(y.doubleValue()), world.getMinHeight(), world.getMaxHeight());
    return Marker.point(id, world.getKey(), new Vector(blockX + .5, blockY, blockZ + .5), name);
  }

  private static @Nullable Marker area(
      final @NotNull Map<?, ?> entry,
      final @NotNull World world,
      final @NotNull String id,
      final @NotNull String name) {
    final @Nullable List<Vector> points = points(entry, world, 2);
    if (points == null || points.size() < 3) {
      return null;
    }
    final @NotNull List<Vector> outline = Waypoints.outline(points);
    return outline.size() < 3
        ? null
        : Marker.polygon(
            id, world.getKey(), outline, world.getMinHeight(), world.getMaxHeight(), name);
  }

  private static @Nullable Marker line(
      final @NotNull Map<?, ?> entry,
      final @NotNull World world,
      final @NotNull String id,
      final @NotNull String name) {
    final @Nullable List<Vector> points = points(entry, world, 3);
    if (points == null || points.size() < 2) {
      return null;
    }
    final @NotNull LineMarker line = Marker.line(id, world.getKey(), points, name);
    return Boolean.TRUE.equals(entry.get("smooth")) ? line.smoothed() : line;
  }

  private static @Nullable List<Vector> points(
      final @NotNull Map<?, ?> entry, final @NotNull World world, final int size) {
    if (!(entry.get("points") instanceof final @NotNull List<?> list)
        || list.size() > MAX_SHAPE_POINTS) {
      return null;
    }
    final @NotNull List<Vector> points = new ArrayList<>();
    for (final @Nullable Object item : list) {
      if (!(item instanceof final @NotNull List<?> point) || point.size() != size) {
        return null;
      }
      final double[] values = new double[size];
      for (int i = 0; i < size; i++) {
        if (!(point.get(i) instanceof final @NotNull Number number)
            || !Double.isFinite(number.doubleValue())) {
          return null;
        }
        values[i] = number.doubleValue();
      }
      final double x = values[0];
      final double z = values[size - 1];
      if (!WorldPositions.nearBorder(world, x, z)) {
        return null;
      }
      points.add(
          new Vector(
              x,
              size == 3 ? Math.clamp(values[1], world.getMinHeight(), world.getMaxHeight()) : 0,
              z));
    }
    return points;
  }

  static @Nullable World resolve(final @Nullable String world) {
    return world == null ? null : WorldKeys.resolve(world);
  }
}

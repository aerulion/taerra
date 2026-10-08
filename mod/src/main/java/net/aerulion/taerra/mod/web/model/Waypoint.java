package net.aerulion.taerra.mod.web.model;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Waypoint(
    String world,
    int x,
    @Nullable Integer y,
    int z,
    String name,
    String color,
    String group,
    @Nullable String previous,
    String type,
    @Nullable List<List<Double>> points,
    boolean smooth,
    String description) {

  public static final String DEFAULT_COLOR = "#3ab3da";
  public static final String PERSONAL = "\u0000personal";

  public static @NotNull Waypoint create(
      final String world, final int x, final @Nullable Integer y, final int z) {
    return new Waypoint(world, x, y, z, "", DEFAULT_COLOR, "", null, "point", null, false, "");
  }

  public static @NotNull Waypoint of(
      final String world, final @NotNull Layer layer, final @NotNull Marker marker) {
    final @Nullable List<Double> at = marker.position();
    final boolean area = marker.type().equals("area");
    return new Waypoint(
        world,
        at == null ? 0 : (int) Math.floor(at.get(0)),
        at == null ? null : (int) Math.floor(at.get(1)),
        at == null ? 0 : (int) Math.floor(at.get(2)),
        marker.label(),
        marker.color(),
        layer.id().equals(Layer.PERSONAL)
            ? PERSONAL
            : layer.id().equals("waypoints") ? "" : layer.label(),
        marker.label(),
        marker.type(),
        marker.points() == null
            ? null
            : marker.points().stream()
                .map(point -> area ? Arrays.asList(point.get(0), null, point.get(1)) : point)
                .toList(),
        marker.smooth(),
        marker.description() == null ? "" : marker.description());
  }

  public boolean personal() {
    return this.group.equals(PERSONAL);
  }

  public @NotNull Waypoint at(final int x, final @Nullable Integer y, final int z) {
    return new Waypoint(
        this.world,
        x,
        y,
        z,
        this.name,
        this.color,
        this.group,
        this.previous,
        this.type,
        this.points,
        this.smooth,
        this.description);
  }

  public @NotNull Waypoint with(
      final String name, final String color, final String group, final String description) {
    return new Waypoint(
        this.world,
        this.x,
        this.y,
        this.z,
        name,
        color,
        group,
        this.previous,
        this.type,
        this.points,
        this.smooth,
        this.description);
  }

  public @NotNull Waypoint shaped(
      final String type, final List<List<Double>> points, final boolean smooth) {
    return new Waypoint(
        this.world,
        this.x,
        this.y,
        this.z,
        this.name,
        this.color,
        this.group,
        this.previous,
        type,
        points,
        smooth,
        this.description);
  }
}

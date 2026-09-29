package net.aerulion.taerra.mod.web.model;

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
    @Nullable String previous) {

  public static final String DEFAULT_COLOR = "#3ab3da";

  public static @NotNull Waypoint create(
      final String world, final int x, final @Nullable Integer y, final int z) {
    return new Waypoint(world, x, y, z, "", DEFAULT_COLOR, "", null);
  }

  public static @NotNull Waypoint of(
      final String world, final @NotNull Layer layer, final @NotNull Marker marker) {
    final @Nullable List<Double> at = marker.position();
    return new Waypoint(
        world,
        (int) Math.floor(at.get(0)),
        (int) Math.floor(at.get(1)),
        (int) Math.floor(at.get(2)),
        marker.label(),
        marker.color(),
        layer.id().equals("waypoints") ? "" : layer.label(),
        marker.label());
  }

  public @NotNull Waypoint at(final int x, final @Nullable Integer y, final int z) {
    return new Waypoint(this.world, x, y, z, this.name, this.color, this.group, this.previous);
  }

  public @NotNull Waypoint with(final String name, final String color, final String group) {
    return new Waypoint(this.world, this.x, this.y, this.z, name, color, group, this.previous);
  }
}

package net.aerulion.taerra.mod.web.model;

import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PersonalWaypoint(
    String name,
    String world,
    @Nullable String type,
    @Nullable Integer x,
    @Nullable Integer y,
    @Nullable Integer z,
    @Nullable List<List<Double>> points,
    boolean smooth,
    @Nullable String color,
    @Nullable String description,
    boolean hidden,
    @Nullable String icon) {

  public boolean point() {
    return this.type == null || this.type.equals("point");
  }

  public double @NotNull [] center() {
    if (point() || this.points == null || this.points.isEmpty()) {
      return new double[] {
        (this.x == null ? 0 : this.x) + .5,
        this.y == null ? Double.NaN : this.y,
        (this.z == null ? 0 : this.z) + .5
      };
    }
    final double minX = this.points.stream().mapToDouble(List::getFirst).min().orElse(0);
    final double maxX = this.points.stream().mapToDouble(List::getFirst).max().orElse(0);
    final double minZ = this.points.stream().mapToDouble(List::getLast).min().orElse(0);
    final double maxZ = this.points.stream().mapToDouble(List::getLast).max().orElse(0);
    return new double[] {(minX + maxX) / 2, Double.NaN, (minZ + maxZ) / 2};
  }

  public @NotNull Waypoint editable() {
    final boolean area = "area".equals(this.type);
    return new Waypoint(
        this.world,
        this.x == null ? 0 : this.x,
        this.y,
        this.z == null ? 0 : this.z,
        this.name,
        this.color == null ? Waypoint.DEFAULT_COLOR : this.color,
        Waypoint.PERSONAL,
        this.name,
        point() ? "point" : this.type,
        this.points == null
            ? null
            : this.points.stream()
                .map(at -> area ? Arrays.asList(at.getFirst(), null, at.getLast()) : at)
                .toList(),
        this.smooth,
        this.description == null ? "" : this.description,
        this.icon);
  }
}

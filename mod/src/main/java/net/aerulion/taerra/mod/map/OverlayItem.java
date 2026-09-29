package net.aerulion.taerra.mod.map;

import java.util.List;
import net.aerulion.taerra.mod.web.model.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record OverlayItem(
    int color,
    double[] bounds,
    double @Nullable [][] face,
    double @Nullable [][] ground,
    List<double[][]> walls,
    int[] wallColors,
    double @Nullable [][] path,
    boolean hollow,
    boolean fullHeight) {

  static @Nullable OverlayItem of(final @NotNull Marker marker, final @NotNull MarkerShape shape) {
    if (shape.face == null && shape.path == null) {
      return null;
    }
    return new OverlayItem(
        shape.color,
        shape.bounds(),
        shape.face,
        shape.ground,
        List.copyOf(shape.walls),
        shape.wallColors.clone(),
        shape.path,
        marker.hollow(),
        marker.fullHeight());
  }

  double extent() {
    return Math.max(this.bounds[2] - this.bounds[0], this.bounds[3] - this.bounds[1]);
  }
}

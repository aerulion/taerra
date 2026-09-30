package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record LineMarkerImpl(
    @NotNull String id,
    @NotNull NamespacedKey world,
    @NotNull List<Vector> points,
    boolean smooth,
    @Nullable String label,
    @NotNull Map<String, String> details,
    @NotNull Color color)
    implements LineMarker {

  LineMarkerImpl {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(world, "world");
    points = MarkerArguments.copy(points, 2, "A line");
    details = MarkerArguments.orderedCopy(details);
    Objects.requireNonNull(color, "color");
  }

  @Override
  public @NotNull List<Vector> points() {
    return MarkerArguments.copy(this.points, 0, "A line");
  }

  @Override
  public @NotNull LineMarker smoothed() {
    return new LineMarkerImpl(
        this.id, this.world, this.points, true, this.label, this.details, this.color);
  }

  @Override
  public @NotNull LineMarker withColor(final @NotNull Color color) {
    return new LineMarkerImpl(
        this.id, this.world, this.points, this.smooth, this.label, this.details, color);
  }

  @Override
  public @NotNull LineMarker withDetails(final @NotNull Map<String, String> details) {
    return new LineMarkerImpl(
        this.id, this.world, this.points, this.smooth, this.label, details, this.color);
  }
}

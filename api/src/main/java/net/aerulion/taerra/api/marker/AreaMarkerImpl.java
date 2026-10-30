package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record AreaMarkerImpl(
    @NotNull String id,
    @NotNull NamespacedKey world,
    @NotNull List<Vector> outline,
    @NotNull List<List<Vector>> holes,
    double minY,
    double maxY,
    boolean hollow,
    @Nullable String label,
    @NotNull Map<String, String> details,
    @Nullable String description,
    @NotNull Color color)
    implements AreaMarker {

  AreaMarkerImpl {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(world, "world");
    outline = MarkerArguments.copy(outline, 3, "An area");
    holes = holes.stream().map(hole -> MarkerArguments.copy(hole, 3, "A hole")).toList();
    if (maxY < minY) {
      throw new IllegalArgumentException("maxY " + maxY + " is below minY " + minY);
    }
    details = MarkerArguments.orderedCopy(details);
    Objects.requireNonNull(color, "color");
  }

  @Override
  public @NotNull List<Vector> outline() {
    return MarkerArguments.copy(this.outline, 0, "An area");
  }

  @Override
  public @NotNull List<List<Vector>> holes() {
    return this.holes.stream().map(hole -> MarkerArguments.copy(hole, 0, "A hole")).toList();
  }

  @Override
  public @NotNull AreaMarker withHoles(final @NotNull List<List<Vector>> holes) {
    return new AreaMarkerImpl(
        this.id,
        this.world,
        this.outline,
        holes,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        this.details,
        this.description,
        this.color);
  }

  @Override
  public @NotNull AreaMarker hollowed() {
    return new AreaMarkerImpl(
        this.id,
        this.world,
        this.outline,
        this.holes,
        this.minY,
        this.maxY,
        true,
        this.label,
        this.details,
        this.description,
        this.color);
  }

  @Override
  public @NotNull AreaMarker withColor(final @NotNull Color color) {
    return new AreaMarkerImpl(
        this.id,
        this.world,
        this.outline,
        this.holes,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        this.details,
        this.description,
        color);
  }

  @Override
  public @NotNull AreaMarker withDetails(final @NotNull Map<String, String> details) {
    return new AreaMarkerImpl(
        this.id,
        this.world,
        this.outline,
        this.holes,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        details,
        this.description,
        this.color);
  }

  @Override
  public @NotNull AreaMarker withDescription(final @Nullable String description) {
    return new AreaMarkerImpl(
        this.id,
        this.world,
        this.outline,
        this.holes,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        this.details,
        description,
        this.color);
  }
}

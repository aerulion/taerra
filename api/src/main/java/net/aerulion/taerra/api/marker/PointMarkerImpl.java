package net.aerulion.taerra.api.marker;

import java.util.Map;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record PointMarkerImpl(
    @NotNull String id,
    @NotNull NamespacedKey world,
    @NotNull Vector position,
    @Nullable String label,
    @NotNull Map<String, String> details,
    @Nullable String description,
    @NotNull Color color)
    implements PointMarker {

  PointMarkerImpl {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(world, "world");
    position = position.clone();
    details = MarkerArguments.orderedCopy(details);
    Objects.requireNonNull(color, "color");
  }

  @Override
  public @NotNull Vector position() {
    return this.position.clone();
  }

  @Override
  public @NotNull PointMarker withColor(final @NotNull Color color) {
    return new PointMarkerImpl(
        this.id, this.world, this.position, this.label, this.details, this.description, color);
  }

  @Override
  public @NotNull PointMarker withDetails(final @NotNull Map<String, String> details) {
    return new PointMarkerImpl(
        this.id, this.world, this.position, this.label, details, this.description, this.color);
  }

  @Override
  public @NotNull PointMarker withDescription(final @Nullable String description) {
    return new PointMarkerImpl(
        this.id, this.world, this.position, this.label, this.details, description, this.color);
  }
}

package net.aerulion.taerra.api.marker;

import java.util.Map;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PointMarker(
    @NotNull String id,
    @NotNull NamespacedKey world,
    @NotNull Vector position,
    @Nullable String label,
    @NotNull Map<String, String> details,
    @NotNull Color color)
    implements Marker {

  public PointMarker {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(world, "world");
    position = position.clone();
    details = MarkerArguments.orderedCopy(details);
    Objects.requireNonNull(color, "color");
  }

  @Override
  public @NotNull PointMarker withColor(final @NotNull Color color) {
    return new PointMarker(this.id, this.world, this.position, this.label, this.details, color);
  }

  @Override
  public @NotNull PointMarker withDetails(final @NotNull Map<String, String> details) {
    return new PointMarker(this.id, this.world, this.position, this.label, details, this.color);
  }
}

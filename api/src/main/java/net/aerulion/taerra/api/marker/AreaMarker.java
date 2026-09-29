package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record AreaMarker(
    @NotNull String id,
    @NotNull NamespacedKey world,
    @NotNull List<Vector> outline,
    double minY,
    double maxY,
    boolean hollow,
    @Nullable String label,
    @NotNull Map<String, String> details,
    @NotNull Color color)
    implements Marker {

  public AreaMarker {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(world, "world");
    outline = MarkerArguments.copy(outline, 3, "An area");
    if (maxY < minY) {
      throw new IllegalArgumentException("maxY " + maxY + " is below minY " + minY);
    }
    details = MarkerArguments.orderedCopy(details);
    Objects.requireNonNull(color, "color");
  }

  public @NotNull AreaMarker hollowed() {
    return new AreaMarker(
        this.id,
        this.world,
        this.outline,
        this.minY,
        this.maxY,
        true,
        this.label,
        this.details,
        this.color);
  }

  @Override
  public @NotNull AreaMarker withColor(final @NotNull Color color) {
    return new AreaMarker(
        this.id,
        this.world,
        this.outline,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        this.details,
        color);
  }

  @Override
  public @NotNull AreaMarker withDetails(final @NotNull Map<String, String> details) {
    return new AreaMarker(
        this.id,
        this.world,
        this.outline,
        this.minY,
        this.maxY,
        this.hollow,
        this.label,
        details,
        this.color);
  }
}

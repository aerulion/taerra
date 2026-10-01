package net.aerulion.taerra.api.marker;

import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A single position, drawn as a pin. Create one with {@link Marker#point}. */
@ApiStatus.NonExtendable
public sealed interface PointMarker extends Marker permits PointMarkerImpl {

  /**
   * Returns the point's position.
   *
   * @return a copy of the position
   */
  @NotNull
  Vector position();

  /**
   * Returns a copy with another color.
   *
   * @param color the new color; alpha is ignored
   * @return the copy
   */
  @Override
  @NotNull
  PointMarker withColor(@NotNull Color color);

  /**
   * Returns a copy with other popup rows, replacing all current ones.
   *
   * @param details the rows as name to value, in the map's iteration order
   * @return the copy
   */
  @Override
  @NotNull
  PointMarker withDetails(@NotNull Map<String, String> details);
}

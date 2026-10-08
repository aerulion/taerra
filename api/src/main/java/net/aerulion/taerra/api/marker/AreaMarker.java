package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An outline extruded between two heights. Create one with {@link Marker#cuboid} or {@link
 * Marker#polygon}.
 *
 * <p>The isometric view draws it as a solid from {@link #minY()} to {@link #maxY()}. An area
 * spanning the world's full height is drawn flat on the terrain instead.
 */
@ApiStatus.NonExtendable
public sealed interface AreaMarker extends Marker permits AreaMarkerImpl {

  /**
   * Returns the area's corners. Only X and Z are used.
   *
   * @return a copy of the corners
   */
  @NotNull
  List<Vector> outline();

  /**
   * Returns the area's bottom.
   *
   * @return the lowest Y
   */
  double minY();

  /**
   * Returns the area's top.
   *
   * @return the highest Y
   */
  double maxY();

  /**
   * Returns whether only the outline is drawn, without fill.
   *
   * @return {@code true} if the area is hollow
   */
  boolean hollow();

  /**
   * Returns a copy drawn as an outline without fill.
   *
   * @return the copy
   */
  @NotNull
  AreaMarker hollowed();

  /**
   * Returns a copy with another color.
   *
   * @param color the new color; alpha is ignored
   * @return the copy
   */
  @Override
  @NotNull
  AreaMarker withColor(@NotNull Color color);

  /**
   * Returns a copy with other popup rows, replacing all current ones.
   *
   * @param details the rows as name to value, in the map's iteration order
   * @return the copy
   */
  @Override
  @NotNull
  AreaMarker withDetails(@NotNull Map<String, String> details);

  /**
   * Returns a copy with another description.
   *
   * @param description the text shown in the popup, or {@code null} for none
   * @return the copy
   */
  @Override
  @NotNull
  AreaMarker withDescription(@Nullable String description);
}

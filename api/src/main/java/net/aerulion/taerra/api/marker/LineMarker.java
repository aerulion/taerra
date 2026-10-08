package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A line through two or more points. Create one with {@link Marker#line}. */
@ApiStatus.NonExtendable
public sealed interface LineMarker extends Marker permits LineMarkerImpl {

  /**
   * Returns the line's points.
   *
   * @return a copy of the points
   */
  @NotNull
  List<Vector> points();

  /**
   * Returns whether the line is drawn as a smooth curve through its points rather than straight
   * segments.
   *
   * @return {@code true} if the line is smooth
   */
  boolean smooth();

  /**
   * Returns a copy drawn as a smooth curve through its points.
   *
   * @return the copy
   */
  @NotNull
  LineMarker smoothed();

  /**
   * Returns a copy with another color.
   *
   * @param color the new color; alpha is ignored
   * @return the copy
   */
  @Override
  @NotNull
  LineMarker withColor(@NotNull Color color);

  /**
   * Returns a copy with other popup rows, replacing all current ones.
   *
   * @param details the rows as name to value, in the map's iteration order
   * @return the copy
   */
  @Override
  @NotNull
  LineMarker withDetails(@NotNull Map<String, String> details);

  /**
   * Returns a copy with another description.
   *
   * @param description the text shown in the popup, or {@code null} for none
   * @return the copy
   */
  @Override
  @NotNull
  LineMarker withDescription(@Nullable String description);
}

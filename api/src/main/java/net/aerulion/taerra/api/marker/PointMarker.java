package net.aerulion.taerra.api.marker;

import java.util.Map;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single position, drawn as its icon on a badge in its color. Create one with {@link
 * Marker#point}.
 */
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
   * Returns the key of the icon on the point's badge.
   *
   * @return the icon's key, or {@code null} for the layer's icon or the default one
   * @see MarkerIcons
   */
  @Nullable
  NamespacedKey icon();

  /**
   * Returns a copy with another icon. The key need not be registered yet; until it is, the marker
   * shows its layer's icon or the default one.
   *
   * @param icon the icon's key, or {@code null} for the layer's icon or the default one
   * @return the copy
   * @see MarkerIcons
   */
  @NotNull
  PointMarker withIcon(@Nullable NamespacedKey icon);

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

  /**
   * Returns a copy with another description.
   *
   * @param description the text shown in the popup, or {@code null} for none
   * @return the copy
   */
  @Override
  @NotNull
  PointMarker withDescription(@Nullable String description);
}

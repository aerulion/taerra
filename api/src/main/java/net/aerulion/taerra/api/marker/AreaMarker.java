package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A region: an outline on the ground, extruded from {@link #minY()} to {@link #maxY()}. Made with
 * {@link Marker#cuboid} or {@link Marker#polygon}.
 */
@ApiStatus.NonExtendable
public sealed interface AreaMarker extends Marker permits AreaMarkerImpl {

  /** The corners in order, copies. Only x and z count. */
  @NotNull
  List<Vector> outline();

  double minY();

  double maxY();

  /** Whether only the outline is drawn, without a fill. */
  boolean hollow();

  /** This area with only its outline drawn. */
  @NotNull
  AreaMarker hollowed();

  @Override
  @NotNull
  AreaMarker withColor(@NotNull Color color);

  @Override
  @NotNull
  AreaMarker withDetails(@NotNull Map<String, String> details);
}

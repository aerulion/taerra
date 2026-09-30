package net.aerulion.taerra.api.marker;

import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A single spot, drawn as a pin. Made with {@link Marker#point}. */
@ApiStatus.NonExtendable
public sealed interface PointMarker extends Marker permits PointMarkerImpl {

  /** Where the pin stands, a copy. */
  @NotNull
  Vector position();

  @Override
  @NotNull
  PointMarker withColor(@NotNull Color color);

  @Override
  @NotNull
  PointMarker withDetails(@NotNull Map<String, String> details);
}

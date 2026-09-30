package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public sealed interface AreaMarker extends Marker permits AreaMarkerImpl {

  @NotNull
  List<Vector> outline();

  double minY();

  double maxY();

  boolean hollow();

  @NotNull
  AreaMarker hollowed();

  @Override
  @NotNull
  AreaMarker withColor(@NotNull Color color);

  @Override
  @NotNull
  AreaMarker withDetails(@NotNull Map<String, String> details);
}

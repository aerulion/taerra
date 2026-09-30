package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public sealed interface LineMarker extends Marker permits LineMarkerImpl {

  @NotNull
  List<Vector> points();

  boolean smooth();

  @NotNull
  LineMarker smoothed();

  @Override
  @NotNull
  LineMarker withColor(@NotNull Color color);

  @Override
  @NotNull
  LineMarker withDetails(@NotNull Map<String, String> details);
}

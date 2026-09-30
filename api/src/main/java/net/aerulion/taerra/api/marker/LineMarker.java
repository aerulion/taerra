package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** A path through two or more points. Made with {@link Marker#line}. */
@ApiStatus.NonExtendable
public sealed interface LineMarker extends Marker permits LineMarkerImpl {

  /** The points in order, copies. */
  @NotNull
  List<Vector> points();

  /** Whether the path is drawn as a curve through its points rather than straight segments. */
  boolean smooth();

  /** This line, drawn as a curve through its points. */
  @NotNull
  LineMarker smoothed();

  @Override
  @NotNull
  LineMarker withColor(@NotNull Color color);

  @Override
  @NotNull
  LineMarker withDetails(@NotNull Map<String, String> details);
}

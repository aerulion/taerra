package net.aerulion.taerra.web.marker;

import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.RegionShapes;
import net.aerulion.taerra.region.RegionVisibility;
import net.aerulion.taerra.web.auth.Viewer;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RegionMarkers {

  public static final @NotNull String LAYER = "regions";
  public static final @NotNull String LABEL = "Map regions";

  private static final @NotNull Color COLOR = Color.WHITE;

  private RegionMarkers() {
    super();
  }

  public static @NotNull List<Marker> markers(
      final @NotNull RegionVisibility visibility,
      final @NotNull Viewer viewer,
      final @NotNull String worldName) {
    final @Nullable NamespacedKey world = NamespacedKey.fromString(worldName);
    if (world == null) {
      return List.of();
    }
    final @NotNull List<Marker> markers = new ArrayList<>();
    for (final @NotNull MapRegion region : visibility.regions().in(worldName)) {
      if (!viewer.canEditRegions()
          && (region.name().isEmpty()
              || !visibility.revealed(viewer.uuid(), viewer.canSeeHidden(), region))) {
        continue;
      }
      final @NotNull List<List<long[]>> rings = RegionShapes.rings(region.chunks());
      for (int i = 0; i < rings.size(); i++) {
        markers.add(
            Marker.polygon(
                    "region:" + region.id() + "/" + i,
                    world,
                    rings.get(i).stream()
                        .map(corner -> new Vector(corner[0] << 4, 0, corner[1] << 4))
                        .toList(),
                    63,
                    64,
                    region.name())
                .hollowed()
                .withColor(COLOR));
      }
    }
    return markers;
  }
}

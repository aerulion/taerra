package net.aerulion.taerra.web.marker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.aerulion.taerra.api.marker.AreaMarker;
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

  private RegionMarkers() {
    super();
  }

  public static @NotNull List<Map<String, Object>> layer(
      final @NotNull RegionVisibility visibility,
      final @NotNull Viewer viewer,
      final @NotNull String worldName,
      final @NotNull String label,
      final boolean shown,
      final @NotNull Predicate<String> layerPrivate) {
    final @Nullable NamespacedKey world = NamespacedKey.fromString(worldName);
    if (world == null) {
      return List.of();
    }
    final @NotNull List<Marker> markers = new ArrayList<>();
    final @NotNull Set<String> unrevealed = new HashSet<>();
    for (final @NotNull MapRegion region : visibility.regions().in(worldName)) {
      final boolean revealed = visibility.revealed(viewer.uuid(), viewer.canSeeHidden(), region);
      final @NotNull List<List<long[]>> rings = RegionShapes.rings(region.chunks());
      for (int i = 0; i < rings.size(); i++) {
        final @NotNull String id = "region:" + region.id() + "/" + i;
        if (!revealed) {
          unrevealed.add(id);
        }
        final @NotNull AreaMarker marker =
            Marker.polygon(
                    id,
                    world,
                    rings.get(i).stream()
                        .map(corner -> new Vector(corner[0] << 4, 0, corner[1] << 4))
                        .toList(),
                    63,
                    64,
                    revealed ? region.name() : "")
                .withColor(Color.fromRGB(region.color()));
        markers.add(revealed ? marker : marker.hollowed());
      }
    }
    return MarkerPayloadSerializer.layer(
        LAYER, label, shown, markers, layerPrivate, marker -> unrevealed.contains(marker.id()));
  }
}

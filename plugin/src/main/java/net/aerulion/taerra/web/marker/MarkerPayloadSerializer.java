package net.aerulion.taerra.web.marker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import net.aerulion.taerra.api.marker.AreaMarker;
import net.aerulion.taerra.api.marker.LineMarker;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.MarkerLayer;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.marker.PlayerMarkerLayerImpl;
import net.aerulion.taerra.util.HexColors;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MarkerPayloadSerializer {

  private MarkerPayloadSerializer() {
    super();
  }

  public static @NotNull List<Map<String, Object>> world(
      final @NotNull MarkerRegistry registry,
      final @NotNull String worldId,
      final @NotNull Predicate<String> layerVisible,
      final @NotNull Predicate<String> layerPrivate,
      final @NotNull Function<String, String> layerIcon) {
    final @NotNull List<Map<String, Object>> layers = new ArrayList<>();
    for (final @NotNull MarkerLayer layer : registry.layers()) {
      if (!layerVisible.test(layer.id())) {
        continue;
      }
      final @NotNull List<Map<String, Object>> markers =
          layer.markers().stream()
              .filter(marker -> marker.world().asString().equals(worldId))
              .map(MarkerPayloadSerializer::marker)
              .toList();
      if (!markers.isEmpty()) {
        layers.add(
            layer(
                layer.id(),
                layer.label(),
                layer.shownByDefault(),
                false,
                layerPrivate,
                layerIcon.apply(layer.id()),
                markers));
      }
    }
    return layers;
  }

  public static @NotNull List<Map<String, Object>> player(
      final @NotNull Collection<PlayerMarkerLayerImpl> playerLayers,
      final @NotNull UUID player,
      final @NotNull String worldId,
      final @NotNull Predicate<String> layerVisible,
      final @NotNull Predicate<String> layerPrivate,
      final @NotNull Function<String, String> layerIcon) {
    final @NotNull List<Map<String, Object>> layers = new ArrayList<>();
    for (final @NotNull PlayerMarkerLayerImpl layer : playerLayers) {
      if (!layerVisible.test(layer.id())) {
        continue;
      }
      final @NotNull List<Map<String, Object>> markers =
          layer.markers(player).stream()
              .filter(marker -> marker.world().asString().equals(worldId))
              .map(MarkerPayloadSerializer::marker)
              .toList();
      if (!markers.isEmpty()) {
        layers.add(
            layer(
                layer.id(),
                layer.label(),
                layer.shownByDefault(),
                true,
                layerPrivate,
                layerIcon.apply(layer.id()),
                markers));
      }
    }
    return layers;
  }

  public static @NotNull List<Map<String, Object>> layer(
      final @NotNull String id,
      final @NotNull String label,
      final boolean shown,
      final @NotNull Collection<? extends Marker> markers,
      final @NotNull Predicate<String> layerPrivate,
      final @NotNull BiConsumer<Marker, Map<String, Object>> extend) {
    return markers.isEmpty()
        ? List.of()
        : List.of(
            layer(
                id,
                label,
                shown,
                false,
                layerPrivate,
                null,
                markers.stream()
                    .map(
                        marker -> {
                          final @NotNull Map<String, Object> data = marker(marker);
                          extend.accept(marker, data);
                          return data;
                        })
                    .toList()));
  }

  private static @NotNull Map<String, Object> layer(
      final @NotNull String id,
      final @NotNull String label,
      final boolean shown,
      final boolean personal,
      final @NotNull Predicate<String> layerPrivate,
      final @Nullable String icon,
      final @NotNull List<Map<String, Object>> markers) {
    final @NotNull Map<String, Object> layer = new HashMap<>();
    layer.put("id", id);
    layer.put("label", label);
    layer.put("shown", shown);
    layer.put("personal", personal);
    layer.put("private", layerPrivate.test(id));
    layer.put("markers", markers);
    if (icon != null) {
      layer.put("icon", icon);
    }
    return layer;
  }

  static @NotNull Map<String, Object> marker(final @NotNull Marker marker) {
    final @NotNull Map<String, Object> data = new HashMap<>();
    data.put("id", marker.id());
    data.put("label", marker.label());
    data.put(
        "details",
        marker.details().entrySet().stream()
            .map(row -> List.of(row.getKey(), row.getValue()))
            .toList());
    data.put("color", HexColors.format(marker.color().asRGB()));
    data.put("description", marker.description());
    switch (marker) {
      case final @NotNull PointMarker point -> {
        data.put("type", "point");
        data.put("position", xyz(point.position()));
        if (point.icon() != null) {
          data.put("icon", point.icon().asString());
        }
      }
      case final @NotNull LineMarker line -> {
        data.put("type", "line");
        data.put("points", line.points().stream().map(MarkerPayloadSerializer::xyz).toList());
        data.put("smooth", line.smooth());
      }
      case final @NotNull AreaMarker area -> {
        data.put("type", "area");
        data.put(
            "points",
            area.outline().stream().map(point -> List.of(point.getX(), point.getZ())).toList());
        data.put("minY", area.minY());
        data.put("maxY", area.maxY());
        data.put("hollow", area.hollow());
        final @Nullable World world = Bukkit.getWorld(area.world());
        data.put(
            "fullHeight",
            world != null
                && area.minY() <= world.getMinHeight()
                && area.maxY() >= world.getMaxHeight());
      }
    }
    return data;
  }

  private static @NotNull List<Double> xyz(final @NotNull Vector vector) {
    return List.of(vector.getX(), vector.getY(), vector.getZ());
  }
}

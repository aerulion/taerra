package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Something drawn on the map, placed on a {@link MarkerLayer}. Markers are immutable: the {@code
 * with…} methods return a changed copy. Worlds are given by key, so a marker may name a world that
 * is not loaded.
 */
@ApiStatus.NonExtendable
public sealed interface Marker permits PointMarker, LineMarker, AreaMarker {

  @NotNull Color DEFAULT_COLOR = Color.fromRGB(0xFFCF3F);

  static @NotNull PointMarker point(
      final @NotNull String id, final @NotNull Location location, final @Nullable String label) {
    return point(id, location.getWorld().getKey(), location.toVector(), label);
  }

  static @NotNull PointMarker point(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull Vector position,
      final @Nullable String label) {
    return new PointMarkerImpl(id, world, position, label, Map.of(), DEFAULT_COLOR);
  }

  /** A line through at least two points. */
  static @NotNull LineMarker line(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull List<Vector> points,
      final @Nullable String label) {
    return new LineMarkerImpl(id, world, points, false, label, Map.of(), DEFAULT_COLOR);
  }

  static @NotNull AreaMarker cuboid(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull BoundingBox box,
      final @Nullable String label) {
    return new AreaMarkerImpl(
        id,
        world,
        List.of(
            new Vector(box.getMinX(), 0, box.getMinZ()),
            new Vector(box.getMaxX(), 0, box.getMinZ()),
            new Vector(box.getMaxX(), 0, box.getMaxZ()),
            new Vector(box.getMinX(), 0, box.getMaxZ())),
        box.getMinY(),
        box.getMaxY(),
        false,
        label,
        Map.of(),
        DEFAULT_COLOR);
  }

  /** An area with at least three corners, from {@code minY} up to {@code maxY}. */
  static @NotNull AreaMarker polygon(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull List<Vector> outline,
      final double minY,
      final double maxY,
      final @Nullable String label) {
    return new AreaMarkerImpl(
        id, world, outline, minY, maxY, false, label, Map.of(), DEFAULT_COLOR);
  }

  /** Unique within its layer; setting a marker with the same id replaces it. */
  @NotNull
  String id();

  @NotNull
  NamespacedKey world();

  /** Shown on hover and beside pins, or {@code null} for none. */
  @Nullable
  String label();

  /** Extra rows shown when the marker is clicked, in order. */
  @NotNull
  Map<String, String> details();

  @NotNull
  Color color();

  @NotNull
  Marker withColor(@NotNull Color color);

  @NotNull
  Marker withDetails(@NotNull Map<String, String> details);
}

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
 * An immutable shape on the map, created with the factories below and added to a {@link
 * MarkerLayer}.
 *
 * <p>Coordinates are block coordinates in the marker's world. Clicking a marker opens a popup with
 * its label, or its id if it has none, its description and its details, all as plain text.
 */
@ApiStatus.NonExtendable
public sealed interface Marker permits PointMarker, LineMarker, AreaMarker {

  /** The color of a new marker, {@code #FFCF3F}. */
  @NotNull Color DEFAULT_COLOR = Color.fromRGB(0xFFCF3F);

  /**
   * Creates a point at a location.
   *
   * @param id the marker's id, unique within its layer
   * @param location the point's world and position
   * @param label the name shown on the map, or {@code null} for none
   * @return the marker
   */
  static @NotNull PointMarker point(
      final @NotNull String id, final @NotNull Location location, final @Nullable String label) {
    return point(id, location.getWorld().getKey(), location.toVector(), label);
  }

  /**
   * Creates a point.
   *
   * @param id the marker's id, unique within its layer
   * @param world the key of the point's world
   * @param position the point's position
   * @param label the name shown on the map, or {@code null} for none
   * @return the marker
   */
  static @NotNull PointMarker point(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull Vector position,
      final @Nullable String label) {
    return new PointMarkerImpl(id, world, position, label, Map.of(), null, DEFAULT_COLOR, null);
  }

  /**
   * Creates a line of straight segments through the given points.
   *
   * @param id the marker's id, unique within its layer
   * @param world the key of the line's world
   * @param points the line's points, at least two
   * @param label the name shown on the map, or {@code null} for none
   * @return the marker
   * @throws IllegalArgumentException if there are fewer than two points
   */
  static @NotNull LineMarker line(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull List<Vector> points,
      final @Nullable String label) {
    return new LineMarkerImpl(id, world, points, false, label, Map.of(), null, DEFAULT_COLOR);
  }

  /**
   * Creates a box-shaped area.
   *
   * @param id the marker's id, unique within its layer
   * @param world the key of the area's world
   * @param box the area's bounds
   * @param label the name shown on the map, or {@code null} for none
   * @return the marker
   */
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
        null,
        DEFAULT_COLOR);
  }

  /**
   * Creates an area from an outline extruded from {@code minY} to {@code maxY}.
   *
   * @param id the marker's id, unique within its layer
   * @param world the key of the area's world
   * @param outline the area's corners in order, at least three; only X and Z are used
   * @param minY the area's bottom
   * @param maxY the area's top
   * @param label the name shown on the map, or {@code null} for none
   * @return the marker
   * @throws IllegalArgumentException if there are fewer than three corners or {@code maxY} is below
   *     {@code minY}
   */
  static @NotNull AreaMarker polygon(
      final @NotNull String id,
      final @NotNull NamespacedKey world,
      final @NotNull List<Vector> outline,
      final double minY,
      final double maxY,
      final @Nullable String label) {
    return new AreaMarkerImpl(
        id, world, outline, minY, maxY, false, label, Map.of(), null, DEFAULT_COLOR);
  }

  /**
   * Returns the marker's id, unique within its layer.
   *
   * @return the marker's id
   */
  @NotNull
  String id();

  /**
   * Returns the key of the marker's world.
   *
   * @return the world's key
   */
  @NotNull
  NamespacedKey world();

  /**
   * Returns the name shown on the map.
   *
   * @return the label, or {@code null} for none
   */
  @Nullable
  String label();

  /**
   * Returns the rows shown in the marker's popup, as name to value, in order.
   *
   * @return the details
   */
  @NotNull
  Map<String, String> details();

  /**
   * Returns the text shown in the marker's popup above its details.
   *
   * @return the description, or {@code null} for none
   */
  @Nullable
  String description();

  /**
   * Returns the marker's color.
   *
   * @return the color
   */
  @NotNull
  Color color();

  /**
   * Returns a copy with another color.
   *
   * @param color the new color; alpha is ignored
   * @return the copy
   */
  @NotNull
  Marker withColor(@NotNull Color color);

  /**
   * Returns a copy with other popup rows, replacing all current ones.
   *
   * @param details the rows as name to value, in the map's iteration order
   * @return the copy
   */
  @NotNull
  Marker withDetails(@NotNull Map<String, String> details);

  /**
   * Returns a copy with another description.
   *
   * @param description the text shown in the popup, or {@code null} for none
   * @return the copy
   */
  @NotNull
  Marker withDescription(@Nullable String description);
}

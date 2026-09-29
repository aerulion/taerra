package net.aerulion.taerra.api.marker;

import java.util.List;
import java.util.Map;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Marker permits PointMarker, LineMarker, AreaMarker {

  @NotNull Color DEFAULT_COLOR = Color.fromRGB(0xFFCF3F);

  static @NotNull PointMarker point(
      final @NotNull String id, final @NotNull Location location, final @Nullable String label) {
    return new PointMarker(
        id, location.getWorld().getKey(), location.toVector(), label, Map.of(), DEFAULT_COLOR);
  }

  static @NotNull LineMarker line(
      final @NotNull String id,
      final @NotNull World world,
      final @NotNull List<Vector> points,
      final @Nullable String label) {
    return new LineMarker(id, world.getKey(), points, false, label, Map.of(), DEFAULT_COLOR);
  }

  static @NotNull AreaMarker cuboid(
      final @NotNull String id,
      final @NotNull World world,
      final @NotNull BoundingBox box,
      final @Nullable String label) {
    return new AreaMarker(
        id,
        world.getKey(),
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

  static @NotNull AreaMarker polygon(
      final @NotNull String id,
      final @NotNull World world,
      final @NotNull List<Vector> outline,
      final double minY,
      final double maxY,
      final @Nullable String label) {
    return new AreaMarker(
        id, world.getKey(), outline, minY, maxY, false, label, Map.of(), DEFAULT_COLOR);
  }

  @NotNull
  String id();

  @NotNull
  NamespacedKey world();

  @Nullable
  String label();

  @NotNull
  Map<String, String> details();

  @NotNull
  Color color();

  @NotNull
  Marker withColor(@NotNull Color color);

  @NotNull
  Marker withDetails(@NotNull Map<String, String> details);
}

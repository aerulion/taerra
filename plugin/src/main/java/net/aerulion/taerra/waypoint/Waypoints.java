package net.aerulion.taerra.waypoint;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.aerulion.taerra.api.marker.Marker;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Waypoints permits WaypointsImpl {

  String LAYER = "waypoints";
  String GROUP_LAYER = "waypoints.";
  String PLACED = "waypoint:";

  static @NotNull String markerId(final @NotNull String name) {
    return PLACED + name.toLowerCase(Locale.ROOT);
  }

  static @NotNull List<Vector> outline(final @NotNull List<Vector> points) {
    final @NotNull List<Vector> kept = new ArrayList<>(points);
    boolean changed = true;
    while (changed && kept.size() >= 3) {
      changed = false;
      for (int i = 0; i < kept.size() && kept.size() >= 3; i++) {
        final @NotNull Vector previous = kept.get(Math.floorMod(i - 1, kept.size()));
        final @NotNull Vector point = kept.get(i);
        final @NotNull Vector next = kept.get((i + 1) % kept.size());
        final double cross =
            (point.getX() - previous.getX()) * (next.getZ() - point.getZ())
                - (point.getZ() - previous.getZ()) * (next.getX() - point.getX());
        if (cross == 0) {
          kept.remove(i);
          changed = true;
          i--;
        }
      }
    }
    return List.copyOf(kept);
  }

  static @Nullable String groupLayerId(final @Nullable String group) {
    if (group == null) {
      return null;
    }
    final @NotNull String slug =
        Normalizer.normalize(group.strip().replace("ß", "ss"), Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_|_$", "");
    return slug.isEmpty() ? null : GROUP_LAYER + slug;
  }

  void load();

  void add(@NotNull String name, @NotNull Location location, @NotNull Color color);

  void add(
      @NotNull String name,
      @NotNull Location location,
      @NotNull Color color,
      @Nullable String group);

  void add(@NotNull Marker marker, @Nullable String group);

  void hideByDefault(@NotNull String group);

  void addAll(@NotNull Collection<Waypoint> waypoints);

  boolean remove(@NotNull String name);

  @Nullable
  String layerOf(@NotNull String name);

  @NotNull
  List<String> names();

  @NotNull
  List<Group> groups();

  record Group(@NotNull String id, @NotNull String label) {}

  record Waypoint(
      @NotNull String name,
      @NotNull Location location,
      @NotNull Color color,
      @Nullable NamespacedKey icon,
      @Nullable String group) {}
}

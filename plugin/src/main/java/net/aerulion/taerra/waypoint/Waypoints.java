package net.aerulion.taerra.waypoint;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Color;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface Waypoints permits WaypointsImpl {

  String LAYER = "waypoints";
  String GROUP_LAYER = "waypoints.";

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

  void hideByDefault(@NotNull String group);

  void addAll(@NotNull Collection<Waypoint> waypoints);

  boolean remove(@NotNull String name);

  @NotNull
  List<String> names();

  @NotNull
  List<Group> groups();

  record Group(@NotNull String id, @NotNull String label) {}

  record Waypoint(
      @NotNull String name,
      @NotNull Location location,
      @NotNull Color color,
      @Nullable String group) {}
}

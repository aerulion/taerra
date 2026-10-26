package net.aerulion.taerra.waypoint;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.aerulion.taerra.api.marker.Marker;
import net.aerulion.taerra.api.marker.PointMarker;
import net.aerulion.taerra.util.Names;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record WaypointsApiImpl(
    @NotNull Plugin plugin, @NotNull Waypoints waypoints, @NotNull PersonalWaypoints personal)
    implements net.aerulion.taerra.api.waypoint.Waypoints {

  private static @NotNull PointMarker named(
      final @NotNull PointMarker waypoint, final @NotNull String id, final @NotNull String name) {
    return Marker.point(id, waypoint.world(), waypoint.position(), name)
        .withIcon(waypoint.icon())
        .withColor(waypoint.color())
        .withDetails(waypoint.details())
        .withDescription(Names.description(waypoint.description()));
  }

  @Override
  public @NotNull Collection<Marker> shared() {
    return this.waypoints.all();
  }

  @Override
  public @NotNull List<Marker> personal(final @NotNull UUID player) {
    return this.personal.all(player).stream().map(PersonalWaypoints.Saved::marker).toList();
  }

  private static @NotNull String name(final @Nullable String name) {
    final @Nullable String valid = Names.of(name);
    if (valid == null) {
      throw new IllegalArgumentException("Not a valid waypoint or group name: " + name);
    }
    return valid;
  }

  @Override
  public void setShared(final @NotNull PointMarker waypoint, final @Nullable String group) {
    requireServerThread();
    final @NotNull String name = name(waypoint.label());
    this.waypoints.add(
        named(waypoint, Waypoints.markerId(name), name), group == null ? null : name(group));
  }

  @Override
  public boolean removeShared(final @NotNull String name) {
    requireServerThread();
    return this.waypoints.remove(name);
  }

  @Override
  public @NotNull Result setPersonal(
      final @NotNull UUID player, final @NotNull PointMarker waypoint) {
    requireServerThread();
    final @NotNull String name = name(waypoint.label());
    return switch (this.personal.save(
        player, named(waypoint, PersonalWaypoints.markerId(name), name), name)) {
      case SAVED -> Result.SAVED;
      case FULL -> Result.FULL;
      case TAKEN, FAILED -> Result.FAILED;
    };
  }

  @Override
  public boolean removePersonal(final @NotNull UUID player, final @NotNull String name) {
    requireServerThread();
    return this.personal.remove(player, name);
  }

  private void requireServerThread() {
    if (!this.plugin.getServer().isPrimaryThread()) {
      throw new IllegalStateException("Waypoints are set and removed on the server thread");
    }
  }
}

package net.aerulion.taerra.player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import org.jetbrains.annotations.NotNull;

public record PlayerData(
    @NotNull List<PersonalWaypoints.Saved> waypoints, @NotNull Map<UUID, Long> discoveries) {

  public static final @NotNull PlayerData EMPTY = new PlayerData(List.of(), Map.of());

  public PlayerData {
    waypoints = List.copyOf(waypoints);
    discoveries = Map.copyOf(discoveries);
  }

  public @NotNull PlayerData withWaypoints(final @NotNull List<PersonalWaypoints.Saved> waypoints) {
    return new PlayerData(waypoints, this.discoveries);
  }

  public @NotNull PlayerData withDiscoveries(final @NotNull Map<UUID, Long> discoveries) {
    return new PlayerData(this.waypoints, discoveries);
  }

  public boolean isEmpty() {
    return this.waypoints.isEmpty() && this.discoveries.isEmpty();
  }
}

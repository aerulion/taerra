package net.aerulion.taerra.player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.aerulion.taerra.waypoint.PersonalWaypoints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlayerData(
    @NotNull List<PersonalWaypoints.Saved> waypoints,
    @NotNull Map<UUID, Long> discoveries,
    @Nullable Navigation navigation,
    @NotNull Map<String, Boolean> layers) {

  public static final @NotNull PlayerData EMPTY =
      new PlayerData(List.of(), Map.of(), null, Map.of());

  public PlayerData {
    waypoints = List.copyOf(waypoints);
    discoveries = Map.copyOf(discoveries);
    layers = Map.copyOf(layers);
  }

  public @NotNull PlayerData withWaypoints(final @NotNull List<PersonalWaypoints.Saved> waypoints) {
    return new PlayerData(waypoints, this.discoveries, this.navigation, this.layers);
  }

  public @NotNull PlayerData withDiscoveries(final @NotNull Map<UUID, Long> discoveries) {
    return new PlayerData(this.waypoints, discoveries, this.navigation, this.layers);
  }

  public @NotNull PlayerData withNavigation(final @Nullable Navigation navigation) {
    return new PlayerData(this.waypoints, this.discoveries, navigation, this.layers);
  }

  public @NotNull PlayerData withLayers(final @NotNull Map<String, Boolean> layers) {
    return new PlayerData(this.waypoints, this.discoveries, this.navigation, layers);
  }

  public boolean isEmpty() {
    return this.waypoints.isEmpty()
        && this.discoveries.isEmpty()
        && this.navigation == null
        && this.layers.isEmpty();
  }
}

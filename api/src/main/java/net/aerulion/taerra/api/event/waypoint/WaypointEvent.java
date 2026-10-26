package net.aerulion.taerra.api.event.waypoint;

import net.aerulion.taerra.api.event.TaerraEvent;
import net.aerulion.taerra.api.marker.Marker;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An event about one waypoint. */
@ApiStatus.NonExtendable
public abstract class WaypointEvent extends TaerraEvent {

  private final @NotNull Marker waypoint;
  private final @Nullable OfflinePlayer owner;

  /**
   * Creates an event about a waypoint.
   *
   * @param waypoint the waypoint
   * @param owner the player whose personal waypoint it is, or {@code null} for a shared one
   */
  @ApiStatus.Internal
  protected WaypointEvent(final @NotNull Marker waypoint, final @Nullable OfflinePlayer owner) {
    super();
    this.waypoint = waypoint;
    this.owner = owner;
  }

  /**
   * Returns the waypoint, as it is after the change this event reports, or as it was before it was
   * deleted. A waypoint is a marker: a point, a line or an area.
   *
   * @return the waypoint
   */
  public @NotNull Marker waypoint() {
    return this.waypoint;
  }

  /**
   * Returns the player whose personal waypoint this is.
   *
   * @return the owner, or {@code null} for a shared waypoint
   */
  public @Nullable OfflinePlayer owner() {
    return this.owner;
  }
}

package net.aerulion.taerra.api.event.waypoint;

import net.aerulion.taerra.api.marker.Marker;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Fired when a waypoint is created, after it is saved. */
public final class WaypointCreateEvent extends WaypointEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  /**
   * Creates the event.
   *
   * @param waypoint the waypoint
   * @param owner the player whose personal waypoint it is, or {@code null} for a shared one
   */
  @ApiStatus.Internal
  public WaypointCreateEvent(final @NotNull Marker waypoint, final @Nullable OfflinePlayer owner) {
    super(waypoint, owner);
  }

  /**
   * Returns the listeners of this event.
   *
   * @return the handler list
   */
  public static @NotNull HandlerList getHandlerList() {
    return HANDLERS;
  }

  @Override
  public @NotNull HandlerList getHandlers() {
    return HANDLERS;
  }
}

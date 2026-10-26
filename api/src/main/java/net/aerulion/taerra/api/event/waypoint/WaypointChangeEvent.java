package net.aerulion.taerra.api.event.waypoint;

import net.aerulion.taerra.api.marker.Marker;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a waypoint is changed and saved under the same name: moved, recolored, or given
 * another icon, description or shape. Moving it to another group or hiding a personal waypoint from
 * the map leaves the waypoint itself as it was and fires nothing.
 */
public final class WaypointChangeEvent extends WaypointEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  private final @NotNull Marker previous;

  /**
   * Creates the event.
   *
   * @param previous the waypoint before the change
   * @param waypoint the waypoint
   * @param owner the player whose personal waypoint it is, or {@code null} for a shared one
   */
  @ApiStatus.Internal
  public WaypointChangeEvent(
      final @NotNull Marker previous,
      final @NotNull Marker waypoint,
      final @Nullable OfflinePlayer owner) {
    super(waypoint, owner);
    this.previous = previous;
  }

  /**
   * Returns the waypoint as it was before the change.
   *
   * @return the previous waypoint
   */
  public @NotNull Marker previous() {
    return this.previous;
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

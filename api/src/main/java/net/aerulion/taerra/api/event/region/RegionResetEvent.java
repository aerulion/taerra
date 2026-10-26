package net.aerulion.taerra.api.event.region;

import net.aerulion.taerra.api.region.Region;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a region's discoveries are reset in the region editor: every player who discovered it
 * sees it under fog again, until they walk back in.
 */
public final class RegionResetEvent extends RegionEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  /**
   * Creates the event.
   *
   * @param region the region
   */
  @ApiStatus.Internal
  public RegionResetEvent(final @NotNull Region region) {
    super(region);
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

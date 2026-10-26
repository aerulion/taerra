package net.aerulion.taerra.api.event.region;

import net.aerulion.taerra.api.region.Region;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Fired when a region is drawn and saved in the region editor. */
public final class RegionCreateEvent extends RegionEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  /**
   * Creates the event.
   *
   * @param region the region
   */
  @ApiStatus.Internal
  public RegionCreateEvent(final @NotNull Region region) {
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

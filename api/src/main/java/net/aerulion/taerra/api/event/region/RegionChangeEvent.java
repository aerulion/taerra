package net.aerulion.taerra.api.event.region;

import net.aerulion.taerra.api.region.Region;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a region is edited and saved in the region editor: its name, color, fog, chunks or
 * whether it is discoverable. Saving a region without changing it fires nothing.
 */
public final class RegionChangeEvent extends RegionEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  private final @NotNull Region previous;

  /**
   * Creates the event.
   *
   * @param previous the region before the change
   * @param region the region
   */
  @ApiStatus.Internal
  public RegionChangeEvent(final @NotNull Region previous, final @NotNull Region region) {
    super(region);
    this.previous = previous;
  }

  /**
   * Returns the region as it was before the change.
   *
   * @return the previous region
   */
  public @NotNull Region previous() {
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

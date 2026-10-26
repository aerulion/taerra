package net.aerulion.taerra.api.event.region;

import net.aerulion.taerra.api.event.TaerraEvent;
import net.aerulion.taerra.api.region.Region;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** An event about one region. */
@ApiStatus.NonExtendable
public abstract class RegionEvent extends TaerraEvent {

  private final @NotNull Region region;

  /**
   * Creates an event about a region.
   *
   * @param region the region
   */
  @ApiStatus.Internal
  protected RegionEvent(final @NotNull Region region) {
    super();
    this.region = region;
  }

  /**
   * Returns the region, as it is after the change this event reports, or as it was before it was
   * removed.
   *
   * @return the region
   */
  public @NotNull Region region() {
    return this.region;
  }
}

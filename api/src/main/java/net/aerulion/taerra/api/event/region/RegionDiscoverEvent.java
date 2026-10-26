package net.aerulion.taerra.api.event.region;

import net.aerulion.taerra.api.region.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player discovers a {@link Region#discoverable() discoverable} region, by walking
 * into it or through {@link net.aerulion.taerra.api.region.Regions#discover(java.util.UUID,
 * Region)}, once per player and region, after the discovery is saved. The region clears for that
 * player on the map, and for a named region TAERRA plays its sound and shows its toast or chat
 * line, so a listener only adds what it needs on top, like a reward.
 *
 * <p>Resetting a region in the editor lets every player discover it again, firing this event again.
 * It only fires for a player who is online, so a plugin discovering a region for an offline player
 * fires none, and neither does a player leaving in the moment between saving and firing.
 */
public final class RegionDiscoverEvent extends RegionEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  private final @NotNull Player player;
  private final @NotNull Cause cause;

  /**
   * Creates the event.
   *
   * @param player the player who discovered the region
   * @param region the region
   * @param cause how the player discovered it
   */
  @ApiStatus.Internal
  public RegionDiscoverEvent(
      final @NotNull Player player, final @NotNull Region region, final @NotNull Cause cause) {
    super(region);
    this.player = player;
    this.cause = cause;
  }

  /**
   * Returns how the player discovered the region.
   *
   * @return the cause
   */
  public @NotNull Cause cause() {
    return this.cause;
  }

  /**
   * Returns the player who discovered the region.
   *
   * @return the player
   */
  public @NotNull Player player() {
    return this.player;
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

  /** How a player discovered a region. */
  public enum Cause {
    /** The player walked into the region. */
    ENTERED,
    /** A plugin let the player discover it. */
    PLUGIN
  }
}

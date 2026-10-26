package net.aerulion.taerra.api.event.web;

import net.aerulion.taerra.api.event.TaerraEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player teleports by clicking the web map or the mod's map, before the teleport.
 * Cancelling it keeps the player where they are and the map tells them the teleport failed, so a
 * listener can charge for teleports, add a cooldown or block them in combat.
 *
 * <p>The teleport that follows also fires Bukkit's {@link
 * org.bukkit.event.player.PlayerTeleportEvent} with the cause {@code PLUGIN}.
 */
public final class WebTeleportEvent extends TaerraEvent implements Cancellable {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  private final @NotNull Player player;
  private final @NotNull Location to;
  private boolean cancelled;

  /**
   * Creates the event.
   *
   * @param player the player who teleports
   * @param to where the player lands
   */
  @ApiStatus.Internal
  public WebTeleportEvent(final @NotNull Player player, final @NotNull Location to) {
    super();
    this.player = player;
    this.to = to;
  }

  /**
   * Returns the player who teleports.
   *
   * @return the player
   */
  public @NotNull Player player() {
    return this.player;
  }

  /**
   * Returns where the player lands.
   *
   * @return a copy of the destination
   */
  public @NotNull Location to() {
    return this.to.clone();
  }

  @Override
  public boolean isCancelled() {
    return this.cancelled;
  }

  @Override
  public void setCancelled(final boolean cancel) {
    this.cancelled = cancel;
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

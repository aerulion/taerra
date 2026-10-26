package net.aerulion.taerra.api.event.web;

import org.bukkit.OfflinePlayer;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player signs in to the web map on a device, or joins the server with the mod, after
 * the session is saved.
 */
public final class WebLoginEvent extends WebSessionEvent {

  private static final @NotNull HandlerList HANDLERS = new HandlerList();

  /**
   * Creates the event.
   *
   * @param player the player the session belongs to
   * @param inGame whether it is the session the mod holds while its player is in game
   * @param browser the browser the session was opened in, if known
   * @param os the operating system the session was opened on, if known
   */
  @ApiStatus.Internal
  public WebLoginEvent(
      final @NotNull OfflinePlayer player,
      final boolean inGame,
      final @Nullable String browser,
      final @Nullable String os) {
    super(player, inGame, browser, os);
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

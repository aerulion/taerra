package net.aerulion.taerra.api.event.web;

import net.aerulion.taerra.api.event.TaerraEvent;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An event about one web map session: a player signed in on one device, through a {@code /taerra
 * login} link, a pairing code, or the mod while its player is in game.
 */
@ApiStatus.NonExtendable
public abstract class WebSessionEvent extends TaerraEvent {

  private final @NotNull OfflinePlayer player;
  private final boolean inGame;
  private final @Nullable String browser;
  private final @Nullable String os;

  /**
   * Creates an event about a session.
   *
   * @param player the player the session belongs to
   * @param inGame whether it is the session the mod holds while its player is in game
   * @param browser the browser the session was opened in, if known
   * @param os the operating system the session was opened on, if known
   */
  @ApiStatus.Internal
  protected WebSessionEvent(
      final @NotNull OfflinePlayer player,
      final boolean inGame,
      final @Nullable String browser,
      final @Nullable String os) {
    super();
    this.player = player;
    this.inGame = inGame;
    this.browser = browser;
    this.os = os;
  }

  /**
   * Returns the player the session belongs to.
   *
   * @return the player
   */
  public @NotNull OfflinePlayer player() {
    return this.player;
  }

  /**
   * Returns whether this is the session the mod holds while its player is in game, which opens when
   * they join with the mod and ends when they leave.
   *
   * @return {@code true} for the mod's in-game session
   */
  public boolean inGame() {
    return this.inGame;
  }

  /**
   * Returns the browser the session was opened in, like {@code Firefox}.
   *
   * @return the browser, or {@code null} if unknown or the session is the mod's
   */
  public @Nullable String browser() {
    return this.browser;
  }

  /**
   * Returns the operating system the session was opened on, like {@code Windows}.
   *
   * @return the operating system, or {@code null} if unknown or the session is the mod's
   */
  public @Nullable String os() {
    return this.os;
  }
}

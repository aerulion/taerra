package net.aerulion.taerra.api.navigation;

import java.util.UUID;
import org.bukkit.Color;
import org.bukkit.Location;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Sets and clears where players navigate to, like the target a player picks on the map. Get it from
 * {@link net.aerulion.taerra.api.TaerraApi#navigation()}.
 *
 * <p>A player has one target at a time, kept across restarts until it is cleared or replaced, by
 * your plugin or by the player picking another one. The map and the mod show the change at once.
 * All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface Navigation {

  /**
   * Guides a player to a place, replacing their current target. The player sees it only while they
   * may see that world on the map.
   *
   * @param player the player's UUID
   * @param label the target's name, up to 32 letters, digits, spaces and {@code _ . , : ' & ( ) # !
   *     ? + -}, with at least one letter or digit
   * @param target the place to guide them to
   * @param color the color the target is drawn in
   * @throws IllegalArgumentException if the label is not a valid name or the location has no world
   */
  void set(
      @NotNull UUID player, @NotNull String label, @NotNull Location target, @NotNull Color color);

  /**
   * Clears a player's target, whoever set it.
   *
   * @param player the player's UUID
   */
  void clear(@NotNull UUID player);
}

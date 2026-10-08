package net.aerulion.taerra.api.marker;

import java.util.UUID;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A layer whose markers each player sees only for themselves, like their homes. Get one from {@link
 * MarkerRegistry#playerLayer(Plugin, String, String, boolean, Function)}.
 *
 * <p>TAERRA holds none of its markers: it asks the layer's function for a player's markers whenever
 * that player, logged in, opens a world's map, so players who are not looking cost nothing. The web
 * map and the mod draw these markers apart from everyone's. All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface PlayerMarkerLayer {

  /**
   * Returns the plugin that owns this layer.
   *
   * @return the owning plugin
   */
  @NotNull
  Plugin owner();

  /**
   * Returns this layer's id, unique across all plugins and shared with {@link MarkerLayer} ids.
   *
   * @return the layer's id
   */
  @NotNull
  String id();

  /**
   * Returns the name shown in the layer list.
   *
   * @return the layer's label
   */
  @NotNull
  String label();

  /**
   * Returns whether this layer is on for viewers who have not toggled it.
   *
   * @return {@code true} if the layer is shown by default
   */
  boolean shownByDefault();

  /**
   * Tells the player's open maps that their markers changed, so they ask for them again within
   * about a second. Call it after a change, like a home set or deleted.
   *
   * @param player the player whose markers changed
   */
  void refresh(@NotNull UUID player);
}

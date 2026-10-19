package net.aerulion.taerra.api.marker;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The map's marker layers.
 *
 * <p>A layer belongs to the plugin that created it and is removed with all its markers when that
 * plugin disables. All methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface MarkerRegistry {

  /**
   * Returns the layer with the given id, creating it if there is none.
   *
   * <p>Ids are unique across all plugins, so prefix them with your plugin's name, like {@code
   * myplugin.claims}. TAERRA's own layers are {@code worldguard}, {@code worldborder}, {@code
   * chunks}, {@code waypoints} and {@code waypoints.*}. Server owners can make a layer private with
   * {@code /taerra config layer <id> private}; only logged-in viewers with {@code
   * taerra.layer.<id>} see it then, any colon in the id replaced by a dot.
   *
   * @param owner the plugin that owns the layer
   * @param id the layer's id
   * @param label the name shown in the layer list; ignored if the layer exists
   * @param shownByDefault whether the layer is on for viewers who have not toggled it; ignored if
   *     the layer exists
   * @return the layer
   * @throws IllegalArgumentException if another plugin owns a layer with this id
   */
  @NotNull
  MarkerLayer layer(
      @NotNull Plugin owner, @NotNull String id, @NotNull String label, boolean shownByDefault);

  /**
   * Returns the layer with the given id, creating it shown by default if there is none.
   *
   * @param owner the plugin that owns the layer
   * @param id the layer's id
   * @param label the name shown in the layer list; ignored if the layer exists
   * @return the layer
   * @throws IllegalArgumentException if another plugin owns a layer with this id
   * @see #layer(Plugin, String, String, boolean)
   */
  default @NotNull MarkerLayer layer(
      final @NotNull Plugin owner, final @NotNull String id, final @NotNull String label) {
    return layer(owner, id, label, true);
  }

  /**
   * Returns the player layer with the given id, creating it if there is none.
   *
   * <p>Each logged-in viewer sees only the markers {@code markers} gives for their own UUID, drawn
   * apart from everyone's markers. The function runs off the main thread whenever that viewer opens
   * a world's map, for players who are offline too, so it must be thread safe and quick: read what
   * you already have in memory or a small file, never wait on the main thread. Return markers of
   * any world; TAERRA shows those of the world being viewed. Call {@link
   * PlayerMarkerLayer#refresh(UUID)} after a player's markers change.
   *
   * <p>Ids share one namespace with {@link #layer(Plugin, String, String, boolean)}. TAERRA's own
   * player layer is {@code personal}, the waypoints players place for themselves. Private layers
   * work as there.
   *
   * @param owner the plugin that owns the layer
   * @param id the layer's id
   * @param label the name shown in the layer list; ignored if the layer exists
   * @param shownByDefault whether the layer is on for viewers who have not toggled it; ignored if
   *     the layer exists
   * @param markers the markers a player sees, by their UUID; ignored if the layer exists
   * @return the layer
   * @throws IllegalArgumentException if another plugin owns a layer with this id, or a {@link
   *     MarkerLayer} has it
   */
  @NotNull
  PlayerMarkerLayer playerLayer(
      @NotNull Plugin owner,
      @NotNull String id,
      @NotNull String label,
      boolean shownByDefault,
      @NotNull Function<UUID, ? extends Collection<? extends Marker>> markers);

  /**
   * Returns the layer with the given id without creating it.
   *
   * @param id the layer's id
   * @return the layer, or {@code null} if there is none
   */
  @Nullable
  MarkerLayer existingLayer(@NotNull String id);

  /**
   * Returns a snapshot of all layers, in the order they were created, without player layers.
   *
   * @return the layers
   */
  @NotNull
  Collection<MarkerLayer> layers();

  /**
   * Removes the layer or player layer with the given id and all its markers, whichever plugin owns
   * it. Does nothing if there is none.
   *
   * @param id the layer's id
   */
  void removeLayer(@NotNull String id);
}

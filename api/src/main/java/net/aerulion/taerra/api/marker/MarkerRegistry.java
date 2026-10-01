package net.aerulion.taerra.api.marker;

import java.util.Collection;
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
   * waypoints} and {@code waypoints.*}. Server owners can make a layer private with {@code /taerra
   * config layer <id> private}; only logged-in viewers with {@code taerra.web.layer.<id>} see it
   * then, any colon in the id replaced by a dot.
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
   * Returns the layer with the given id without creating it.
   *
   * @param id the layer's id
   * @return the layer, or {@code null} if there is none
   */
  @Nullable
  MarkerLayer existingLayer(@NotNull String id);

  /**
   * Returns a snapshot of all layers, in the order they were created.
   *
   * @return the layers
   */
  @NotNull
  Collection<MarkerLayer> layers();

  /**
   * Removes the layer with the given id and all its markers, whichever plugin owns it. Does nothing
   * if there is none.
   *
   * @param id the layer's id
   */
  void removeLayer(@NotNull String id);
}

package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Markers viewers switch on and off together, keyed by {@link Marker#id()}. Get one from {@link
 * MarkerRegistry#layer(Plugin, String, String, boolean)}.
 *
 * <p>One layer holds markers of any number of worlds; each world's map shows those on it. All
 * methods are thread safe.
 */
@ApiStatus.NonExtendable
public interface MarkerLayer {

  /**
   * Returns the plugin that owns this layer.
   *
   * @return the owning plugin
   */
  @NotNull
  Plugin owner();

  /**
   * Returns this layer's id, unique across all plugins.
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
   * Adds a marker, replacing the one with the same id.
   *
   * @param marker the marker
   */
  void set(@NotNull Marker marker);

  /**
   * Removes the marker with the given id.
   *
   * @param id the marker's id
   * @return the removed marker, or {@code null} if there was none
   */
  @Nullable
  Marker remove(@NotNull String id);

  /**
   * Returns the marker with the given id.
   *
   * @param id the marker's id
   * @return the marker, or {@code null} if there is none
   */
  @Nullable
  Marker get(@NotNull String id);

  /**
   * Returns a snapshot of this layer's markers, in no particular order.
   *
   * @return the markers
   */
  @NotNull
  Collection<Marker> markers();

  /**
   * Replaces this layer's markers. Markers equal to the ones they replace cause no update, so
   * syncing the full set on every change is cheap. Of markers sharing an id, the last one wins.
   *
   * @param markers the new markers
   */
  void setAll(@NotNull Collection<? extends Marker> markers);

  /** Removes all markers. The layer stays registered. */
  void clear();
}

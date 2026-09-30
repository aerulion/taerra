package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** The map's marker layers, from {@code TaerraApi.get().markers()}. Safe to use from any thread. */
@ApiStatus.NonExtendable
public interface MarkerRegistry {

  /**
   * The layer with this id, created for {@code owner} if it does not exist yet. A layer is removed
   * with its markers when its owner is disabled.
   *
   * @param id also names the permission {@code taerra.web.layer.<id>} when the layer is made
   *     private, so keep it short and stable, like {@code "myplugin.shops"}
   * @param label the name viewers see in the layer list
   * @param shownByDefault whether the layer is on for viewers who have not toggled it
   * @throws IllegalArgumentException if another plugin owns a layer with this id
   */
  @NotNull
  MarkerLayer layer(
      @NotNull Plugin owner, @NotNull String id, @NotNull String label, boolean shownByDefault);

  /** {@link #layer(Plugin, String, String, boolean)}, shown by default. */
  default @NotNull MarkerLayer layer(
      final @NotNull Plugin owner, final @NotNull String id, final @NotNull String label) {
    return layer(owner, id, label, true);
  }

  @Nullable
  MarkerLayer existingLayer(@NotNull String id);

  @NotNull
  Collection<MarkerLayer> layers();

  /** Removes the layer and takes its markers off the map. */
  void removeLayer(@NotNull String id);
}

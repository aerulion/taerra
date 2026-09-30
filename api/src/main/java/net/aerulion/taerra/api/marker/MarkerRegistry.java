package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface MarkerRegistry {

  @NotNull
  MarkerLayer layer(
      @NotNull Plugin owner, @NotNull String id, @NotNull String label, boolean shownByDefault);

  default @NotNull MarkerLayer layer(
      final @NotNull Plugin owner, final @NotNull String id, final @NotNull String label) {
    return layer(owner, id, label, true);
  }

  @Nullable
  MarkerLayer existingLayer(@NotNull String id);

  @NotNull
  Collection<MarkerLayer> layers();

  void removeLayer(@NotNull String id);
}

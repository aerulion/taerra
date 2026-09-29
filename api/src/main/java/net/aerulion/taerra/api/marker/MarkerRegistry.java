package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface MarkerRegistry {

  @NotNull
  MarkerLayer layer(@NotNull String id, @NotNull String label, boolean shownByDefault);

  default @NotNull MarkerLayer layer(final @NotNull String id, final @NotNull String label) {
    return layer(id, label, true);
  }

  @Nullable
  MarkerLayer existingLayer(@NotNull String id);

  @NotNull
  Collection<MarkerLayer> layers();

  void removeLayer(@NotNull String id);
}

package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A group of markers viewers switch on and off together. */
@ApiStatus.NonExtendable
public interface MarkerLayer {

  /** The plugin that created the layer; it goes when that plugin is disabled. */
  @NotNull
  Plugin owner();

  @NotNull
  String id();

  @NotNull
  String label();

  boolean shownByDefault();

  void set(@NotNull Marker marker);

  @Nullable
  Marker remove(@NotNull String id);

  @Nullable
  Marker get(@NotNull String id);

  @NotNull
  Collection<Marker> markers();

  void setAll(@NotNull Collection<? extends Marker> markers);

  void clear();
}

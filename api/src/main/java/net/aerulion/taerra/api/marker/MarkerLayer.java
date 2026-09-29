package net.aerulion.taerra.api.marker;

import java.util.Collection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.NonExtendable
public interface MarkerLayer {

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

package net.aerulion.taerra.mod.pick;

import java.util.List;
import java.util.OptionalInt;
import net.aerulion.taerra.mod.tile.TileKey;
import net.aerulion.taerra.mod.web.model.Marker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface PickCache permits PickCacheImpl {

  static @NotNull String groundKey(final String world, final @NotNull Marker marker) {
    return world + " " + marker.id() + " " + marker.points();
  }

  @Nullable
  OptionalInt surface(String world, int x, int z);

  int @Nullable [] isometric(String world, int rotation, int screenX, int screenY);

  @Nullable
  List<List<double[]>> ground(String key, String world, Marker marker);

  void refresh(TileKey tile);

  void refreshAll();

  void refreshWorld(String world);
}

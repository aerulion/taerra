package net.aerulion.taerra.rendering.queue;

import java.util.Collection;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public sealed interface ChangeBuffer permits ChangeBufferImpl {

  void add(@NotNull Collection<TileCoordinate> tiles);

  int discard();

  void start();

  void stop();
}

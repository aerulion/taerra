package net.aerulion.taerra.rendering.queue;

import java.util.Map;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public sealed interface RenderQueueStore permits RenderQueueStoreImpl {

  @NotNull
  Map<TileCoordinate, RenderPriority> load();

  void save(@NotNull Map<TileCoordinate, RenderPriority> tasks);
}

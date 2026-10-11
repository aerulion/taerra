package net.aerulion.taerra.rendering.queue;

import java.util.Collection;
import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface RenderQueue permits RenderQueueImpl {

  boolean enqueue(@NotNull TileCoordinate coord, @NotNull RenderPriority priority);

  void enqueueAll(
      @NotNull Collection<TileCoordinate> coordinates, @NotNull RenderPriority priority);

  void enqueueAllAhead(@NotNull List<TileCoordinate> coordinates, @NotNull RenderPriority priority);

  @NotNull
  List<RenderQueueTask> pollNextBaseBatch(int maxBatchSize);

  @Nullable
  RenderQueueTask pollNextZoomTask();

  void markClean(@NotNull TileCoordinate coord);

  void markFailed(@NotNull TileCoordinate coord);

  @Nullable
  RenderPriority nextBasePriority();

  int discardQueued(@NotNull RenderPriority priority);

  @NotNull
  RenderQueueSnapshot snapshot();

  int size();

  void save();
}

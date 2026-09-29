package net.aerulion.taerra.rendering.queue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderQueueStoreImpl implements RenderQueueStore {

  private final @NotNull Path file;
  private final @NotNull Logger logger;
  private final @NotNull ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public RenderQueueStoreImpl(final @NotNull Path file, final @NotNull Logger logger) {
    super();
    this.file = file;
    this.logger = logger;
  }

  private static @Nullable RenderPriority priority(final @NotNull StoredRenderTask task) {
    if (task.priority() == null) {
      return task.zoom() == 0
          ? RenderPriority.FULL_RENDER_ZOOM_0
          : RenderPriority.FULL_RENDER_ZOOM_PARENT;
    }
    try {
      return RenderPriority.valueOf(task.priority());
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  @Override
  public @NotNull Map<TileCoordinate, RenderPriority> load() {
    if (!Files.exists(this.file)) {
      return Map.of();
    }

    try {
      final @NotNull List<StoredRenderTask> stored =
          this.mapper.readValue(
              this.file.toFile(),
              this.mapper
                  .getTypeFactory()
                  .constructCollectionType(List.class, StoredRenderTask.class));
      final @NotNull Map<TileCoordinate, RenderPriority> tasks = new HashMap<>(stored.size());
      int skipped = 0;
      for (final @NotNull StoredRenderTask task : stored) {
        final @Nullable RenderPriority priority = priority(task);
        if (task.world() == null
            || priority == null
            || priority.isZoom0() != (task.zoom() == 0)
            || task.zoom() < 0
            || task.zoom() > TileGeometry.MAX_ZOOM) {
          skipped++;
          continue;
        }
        tasks.put(
            new TileCoordinate(
                task.world(),
                MapView.fromId(task.view(), MapView.TOP_DOWN),
                MapRotation.fromId(task.rotation(), MapRotation.R0),
                task.zoom(),
                task.x(),
                task.z()),
            priority);
      }
      if (!tasks.isEmpty()) {
        this.logger.info("Resuming " + tasks.size() + " queued tiles from the last run");
      }
      if (skipped > 0) {
        this.logger.warning("Skipped " + skipped + " unreadable entries in the render queue");
      }
      return tasks;
    } catch (final IOException | RuntimeException e) {
      this.logger.warning("Failed to load the render queue: " + e.getMessage());
      return Map.of();
    }
  }

  @Override
  public synchronized void save(final @NotNull Map<TileCoordinate, RenderPriority> tasks) {
    try {
      Files.createDirectories(this.file.getParent());

      final @NotNull List<StoredRenderTask> stored = new ArrayList<>(tasks.size());
      for (final Map.@NotNull Entry<TileCoordinate, RenderPriority> entry : tasks.entrySet()) {
        final @NotNull TileCoordinate coord = entry.getKey();
        stored.add(
            new StoredRenderTask(
                coord.world(),
                coord.view().id(),
                coord.rotation().id(),
                coord.zoom(),
                coord.x(),
                coord.z(),
                entry.getValue().name()));
      }

      final @NotNull Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
      this.mapper.writeValue(temp.toFile(), stored);
      Files.move(
          temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (final IOException e) {
      this.logger.warning("Failed to save the render queue: " + e.getMessage());
    }
  }
}

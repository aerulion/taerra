package net.aerulion.taerra.rendering.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderQueueStoreImplTest {

  private static final @NotNull Logger LOGGER = Logger.getLogger("RenderQueueStoreImplTest");

  @TempDir private Path temp;

  @Test
  @DisplayName("queued tasks survive a restart with their view, rotation and priority")
  void roundTrips() {
    final @NotNull Path file = this.temp.resolve("cache/dirty-tiles.json");
    final @NotNull Map<TileCoordinate, RenderPriority> tasks =
        Map.of(
            TileCoordinate.topDown("minecraft:overworld", 0, -3, 7),
            RenderPriority.LIVE_ZOOM_0,
            TileCoordinate.isometric("minecraft:overworld", MapRotation.R270, 2, 5, -1),
            RenderPriority.FULL_RENDER_ZOOM_PARENT);

    new RenderQueueStoreImpl(file, LOGGER).save(tasks);

    assertEquals(tasks, new RenderQueueStoreImpl(file, LOGGER).load());
  }

  @Test
  @DisplayName(
      "a file written before views and priorities existed still loads as a top-down full render")
  void loadsOlderFiles() throws IOException {
    final @NotNull Path file = this.temp.resolve("dirty-tiles.json");
    Files.writeString(
        file,
        "[{\"world\":\"world\",\"zoom\":0,\"x\":1,\"z\":2},{\"world\":\"world\",\"zoom\":3,\"x\":0,\"z\":0}]");

    assertEquals(
        Map.of(
            TileCoordinate.topDown("world", 0, 1, 2),
            RenderPriority.FULL_RENDER_ZOOM_0,
            TileCoordinate.topDown("world", 3, 0, 0),
            RenderPriority.FULL_RENDER_ZOOM_PARENT),
        new RenderQueueStoreImpl(file, LOGGER).load());
  }

  @Test
  @DisplayName("a damaged entry is skipped without losing the rest of the queue")
  void skipsDamagedEntries() throws IOException {
    final @NotNull Path file = this.temp.resolve("dirty-tiles.json");
    Files.writeString(
        file,
        "[{\"world\":\"world\",\"zoom\":0,\"x\":1,\"z\":2},"
            + "{\"world\":\"world\",\"zoom\":0,\"x\":0,\"z\":0,\"priority\":\"NO_SUCH\"},"
            + "{\"world\":\"world\",\"zoom\":2,\"x\":0,\"z\":0,\"priority\":\"LIVE_ZOOM_0\"},"
            + "{\"zoom\":0,\"x\":5,\"z\":5}]");

    assertEquals(
        Map.of(TileCoordinate.topDown("world", 0, 1, 2), RenderPriority.FULL_RENDER_ZOOM_0),
        new RenderQueueStoreImpl(file, LOGGER).load());
  }
}

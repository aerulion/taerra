package net.aerulion.taerra.web.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import net.aerulion.taerra.rendering.pipeline.RenderStatusSnapshot;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.web.socket.SocketMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderStatsPayloadSerializer {

  private RenderStatsPayloadSerializer() {
    super();
  }

  public static @NotNull Map<String, Object> progress(
      final @NotNull RenderStatusSnapshot snapshot, final TileStorage.@Nullable Move move) {
    final @NotNull Map<String, Object> payload = SocketMessage.PROGRESS.payload();
    payload.put("queued", snapshot.totalQueuedTiles());
    payload.put("inFlight", snapshot.totalInFlightTiles());
    final @NotNull ManualRenderSessionSnapshot session = snapshot.manualRenderSession();
    if (session.active()) {
      final @NotNull Map<String, Object> render = new LinkedHashMap<>();
      render.put("world", session.worldName());
      render.put(
          "kind",
          session.mode().equals(TileGeneratorService.FULL_RENDER)
                  || session.mode().equals(TileGeneratorService.LIMIT_RENDER)
              ? "full"
              : "area");
      render.put("phase", session.phase());
      render.put("scannedRegions", session.scannedRegions());
      render.put("totalRegions", session.totalRegions());
      render.put("completedTiles", session.completedTiles());
      render.put("totalTiles", session.totalTiles());
      render.put(
          "percent",
          "scanning".equals(session.phase()) ? session.regionPercent() : session.percent());
      render.put("remainingMillis", session.hasEstimate() ? session.remainingMillis() : null);
      payload.put("render", render);
    }
    if (move != null) {
      payload.put(
          "move",
          Map.of(
              "to",
              move.to(),
              "done",
              move.done(),
              "total",
              move.total(),
              "percent",
              move.percent()));
    }
    return payload;
  }
}

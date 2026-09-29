package net.aerulion.taerra.rendering.pipeline;

import net.aerulion.taerra.rendering.queue.RenderQueueSnapshot;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.rendering.session.ManualRenderTimingSnapshot;

public record RenderStatusSnapshot(
    boolean running,
    int baseThreads,
    int zoomThreads,
    int busyBaseWorkers,
    int busyZoomWorkers,
    RenderQueueSnapshot queueSnapshot,
    long completedZoom0Tiles,
    long completedZoomTiles,
    double averageZoom0Millis,
    double averageZoomMillis,
    double totalTilesPerSecond,
    ManualRenderSessionSnapshot manualRenderSession,
    ManualRenderTimingSnapshot manualTiming) {

  public int totalQueuedTiles() {
    return this.queueSnapshot.totalQueued();
  }

  public int totalInFlightTiles() {
    return this.queueSnapshot.baseInFlight() + this.queueSnapshot.zoomInFlight();
  }
}

package net.aerulion.taerra.rendering.queue;

public record RenderQueueSnapshot(
    int queuedLiveZoom0,
    int queuedFullZoom0,
    int queuedLiveZoom,
    int queuedFullZoom,
    int baseInFlight,
    int zoomInFlight,
    int requeueCount) {

  public int totalQueued() {
    return this.queuedLiveZoom0 + this.queuedFullZoom0 + this.queuedLiveZoom + this.queuedFullZoom;
  }
}

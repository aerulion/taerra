package net.aerulion.taerra.rendering.session;

public record CompletedRender(
    String mode,
    String worldName,
    String outcome,
    int tiles,
    int regions,
    int chunks,
    long durationMillis,
    long finishedAt) {

  public double tilesPerSecond() {
    if (this.durationMillis <= 0L) {
      return 0.0;
    }
    return this.tiles * 1000.0 / this.durationMillis;
  }
}

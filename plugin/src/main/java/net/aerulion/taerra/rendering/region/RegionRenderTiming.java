package net.aerulion.taerra.rendering.region;

public record RegionRenderTiming(
    long chunkReadNanos,
    long sampleNanos,
    long renderTilesNanos,
    long totalNanos,
    int chunksRead,
    int tilesRendered) {

  public double chunkReadMillis() {
    return this.chunkReadNanos / 1_000_000.0;
  }

  public double sampleMillis() {
    return this.sampleNanos / 1_000_000.0;
  }

  public double renderTilesMillis() {
    return this.renderTilesNanos / 1_000_000.0;
  }

  public double totalMillis() {
    return this.totalNanos / 1_000_000.0;
  }
}

package net.aerulion.taerra.rendering.iso;

public record IsoRenderStats(
    long renderedBaseTiles,
    long renderNanos,
    long sampleNanos,
    long sampledChunks,
    long reusedChunks,
    long invalidatedTiles,
    long cachedChunks) {

  public double averageRenderMillis() {
    if (this.renderedBaseTiles <= 0L) {
      return 0.0;
    }
    return this.renderNanos / 1_000_000.0 / this.renderedBaseTiles;
  }

  public double sampleShare() {
    if (this.renderNanos <= 0L) {
      return 0.0;
    }
    return (double) this.sampleNanos / this.renderNanos;
  }

  public double chunkReuseRate() {
    final long total = this.sampledChunks + this.reusedChunks;
    if (total <= 0L) {
      return 0.0;
    }
    return (double) this.reusedChunks / total;
  }
}

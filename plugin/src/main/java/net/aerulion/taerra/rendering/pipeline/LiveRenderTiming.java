package net.aerulion.taerra.rendering.pipeline;

public record LiveRenderTiming(
    long batches,
    long tiles,
    long chunkLoadNanos,
    long snapshotNanos,
    long sampleNanos,
    long drawNanos,
    long persistNanos,
    long chunksRequested) {

  private static double averageMillis(final long nanos, final long count) {
    return count <= 0L ? 0.0 : nanos / 1_000_000.0 / count;
  }

  public boolean isEmpty() {
    return this.batches <= 0L;
  }

  public double averageChunkLoadMillis() {
    return averageMillis(this.chunkLoadNanos, this.batches);
  }

  public double averageSnapshotMillis() {
    return averageMillis(this.snapshotNanos, this.batches);
  }

  public double averageSampleMillis() {
    return averageMillis(this.sampleNanos, this.batches);
  }

  public double averageDrawMillis() {
    return averageMillis(this.drawNanos, this.batches);
  }

  public double averagePersistMillis() {
    return averageMillis(this.persistNanos, this.batches);
  }

  public double averageBatchMillis() {
    return averageMillis(
        this.chunkLoadNanos
            + this.snapshotNanos
            + this.sampleNanos
            + this.drawNanos
            + this.persistNanos,
        this.batches);
  }

  public double averageChunksPerBatch() {
    return this.batches <= 0L ? 0.0 : (double) this.chunksRequested / this.batches;
  }
}

package net.aerulion.taerra.rendering.pipeline;

import java.util.concurrent.atomic.LongAdder;
import org.jetbrains.annotations.NotNull;

final class LiveRenderRecorder {

  private final @NotNull LongAdder batches = new LongAdder();
  private final @NotNull LongAdder tiles = new LongAdder();
  private final @NotNull LongAdder chunkLoadNanos = new LongAdder();
  private final @NotNull LongAdder snapshotNanos = new LongAdder();
  private final @NotNull LongAdder sampleNanos = new LongAdder();
  private final @NotNull LongAdder drawNanos = new LongAdder();
  private final @NotNull LongAdder persistNanos = new LongAdder();
  private final @NotNull LongAdder chunksRequested = new LongAdder();

  LiveRenderRecorder() {
    super();
  }

  void recordBatch(
      final long chunkLoadNanos,
      final long snapshotNanos,
      final long sampleNanos,
      final long drawNanos,
      final int chunksRequested) {
    this.batches.increment();
    this.chunkLoadNanos.add(chunkLoadNanos);
    this.snapshotNanos.add(snapshotNanos);
    this.sampleNanos.add(sampleNanos);
    this.drawNanos.add(drawNanos);
    this.chunksRequested.add(chunksRequested);
  }

  void recordPersist(final long persistNanos, final int tiles) {
    this.persistNanos.add(persistNanos);
    this.tiles.add(tiles);
  }

  @NotNull
  LiveRenderTiming snapshot() {
    return new LiveRenderTiming(
        this.batches.sum(),
        this.tiles.sum(),
        this.chunkLoadNanos.sum(),
        this.snapshotNanos.sum(),
        this.sampleNanos.sum(),
        this.drawNanos.sum(),
        this.persistNanos.sum(),
        this.chunksRequested.sum());
  }
}

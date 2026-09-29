package net.aerulion.taerra.rendering.pipeline;

final class DispatchThrottle {

  private final int maxTilesPerSecond;
  private double permits;
  private long lastRefillNanos;

  DispatchThrottle(final int maxTilesPerSecond) {
    super();
    this.maxTilesPerSecond = maxTilesPerSecond;
  }

  void reset() {
    this.permits = this.maxTilesPerSecond > 0 ? this.maxTilesPerSecond : Double.POSITIVE_INFINITY;
    this.lastRefillNanos = System.nanoTime();
  }

  void refill() {
    if (this.maxTilesPerSecond <= 0) {
      this.permits = Double.POSITIVE_INFINITY;
      return;
    }

    final long now = System.nanoTime();
    final double seconds = (now - this.lastRefillNanos) / 1_000_000_000.0;
    this.lastRefillNanos = now;
    this.permits =
        Math.min(this.maxTilesPerSecond * 2.0, this.permits + seconds * this.maxTilesPerSecond);
  }

  boolean hasPermit() {
    return this.maxTilesPerSecond <= 0 || this.permits >= 1.0;
  }

  void consume() {
    if (this.maxTilesPerSecond > 0) {
      this.permits -= 1.0;
    }
  }
}

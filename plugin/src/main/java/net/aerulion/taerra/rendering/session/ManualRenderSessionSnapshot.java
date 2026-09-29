package net.aerulion.taerra.rendering.session;

public record ManualRenderSessionSnapshot(
    String mode,
    boolean active,
    String worldName,
    String phase,
    int totalRegions,
    int scannedRegions,
    int discoveredChunks,
    int totalTiles,
    boolean totalEstimated,
    int queuedTiles,
    int inFlightTiles,
    int completedTiles,
    String lastMessage,
    ManualRenderTimingSnapshot timing,
    long elapsedMillis,
    long remainingMillis) {

  public boolean hasEstimate() {
    return this.remainingMillis >= 0L;
  }

  public double percent() {
    if (this.totalTiles <= 0) {
      return 0.0;
    }
    return (this.completedTiles * 100.0) / this.totalTiles;
  }

  public int startedTiles() {
    return this.completedTiles + this.inFlightTiles;
  }

  public double startedPercent() {
    if (this.totalTiles <= 0) {
      return 0.0;
    }
    return (startedTiles() * 100.0) / this.totalTiles;
  }

  public double regionPercent() {
    if (this.totalRegions <= 0) {
      return 0.0;
    }
    return (this.scannedRegions * 100.0) / this.totalRegions;
  }
}

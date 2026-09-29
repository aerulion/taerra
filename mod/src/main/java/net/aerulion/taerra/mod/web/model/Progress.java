package net.aerulion.taerra.mod.web.model;

import org.jetbrains.annotations.Nullable;

public record Progress(@Nullable Render render, int queued, int inFlight) {

  public record Render(
      String world,
      String kind,
      String phase,
      long scannedRegions,
      long totalRegions,
      long completedTiles,
      long totalTiles,
      double percent,
      @Nullable Long remainingMillis) {}
}

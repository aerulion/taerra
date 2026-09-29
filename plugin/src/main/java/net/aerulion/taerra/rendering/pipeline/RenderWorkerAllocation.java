package net.aerulion.taerra.rendering.pipeline;

import org.jetbrains.annotations.NotNull;

public record RenderWorkerAllocation(int baseThreads, int zoomThreads) {

  public static @NotNull RenderWorkerAllocation fromConfiguredThreads(final int configuredThreads) {
    final int effectiveThreads = Math.max(2, configuredThreads);
    final int zoomThreads = effectiveThreads >= 6 ? 2 : 1;
    final int baseThreads = Math.max(1, effectiveThreads - zoomThreads);
    return new RenderWorkerAllocation(baseThreads, zoomThreads);
  }
}

package net.aerulion.taerra.rendering.session;

public record ManualRenderTimingSnapshot(
    int renderedRegions,
    double averageRegionMillis,
    double averageChunkReadMillis,
    double averageSampleMillis,
    double averageRenderTilesMillis,
    double averagePersistMillis,
    double lastRegionMillis,
    double lastChunkReadMillis,
    double lastSampleMillis,
    double lastRenderTilesMillis,
    double lastPersistMillis,
    int lastChunksRead,
    int lastTilesRendered) {}

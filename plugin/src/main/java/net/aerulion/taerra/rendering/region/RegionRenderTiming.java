package net.aerulion.taerra.rendering.region;

public record RegionRenderTiming(
    long chunkReadNanos,
    long sampleNanos,
    long renderTilesNanos,
    long totalNanos,
    int chunksRead,
    int tilesRendered) {}

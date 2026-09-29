package net.aerulion.taerra.rendering.region;

public record RegionScanProgress(
    int totalRegions, int scannedRegions, int discoveredChunks, int discoveredTiles) {}

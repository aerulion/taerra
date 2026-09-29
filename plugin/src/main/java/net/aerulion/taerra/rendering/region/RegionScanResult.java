package net.aerulion.taerra.rendering.region;

import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;

public record RegionScanResult(
    List<TileCoordinate> zoom0Tiles,
    int discoveredChunks,
    int totalRegions,
    int unreadableRegions) {}

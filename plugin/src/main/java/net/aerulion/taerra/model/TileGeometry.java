package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;

public final class TileGeometry {

  public static final int TILE_SIZE = MapView.TOP_DOWN.tilePixels();

  public static final int MAX_ZOOM = 12;

  public static final int LEAFLET_GROUND_ZOOM = 4;

  private TileGeometry() {
    super();
  }

  public static int blocksPerTile(final int zoom) {
    return TILE_SIZE << zoom;
  }

  public static int toLeafletZoom(final int zoom) {
    return LEAFLET_GROUND_ZOOM - zoom;
  }

  public static int fromLeafletZoom(final int leafletZoom) {
    return LEAFLET_GROUND_ZOOM - leafletZoom;
  }

  public static boolean isValidLeafletZoom(final int leafletZoom) {
    return leafletZoom <= LEAFLET_GROUND_ZOOM && leafletZoom >= LEAFLET_GROUND_ZOOM - MAX_ZOOM;
  }

  public static @NotNull TileCoordinate blockToTile(
      final int blockX, final int blockZ, final int zoom, final String worldName) {
    final int blocksPerTile = blocksPerTile(zoom);
    return TileCoordinate.topDown(
        worldName,
        zoom,
        Math.floorDiv(blockX, blocksPerTile),
        Math.floorDiv(blockZ, blocksPerTile));
  }

  public static @NotNull TileCoordinate chunkToTile(
      final int chunkX, final int chunkZ, final int zoom, final String worldName) {
    return blockToTile(chunkX * 16, chunkZ * 16, zoom, worldName);
  }
}

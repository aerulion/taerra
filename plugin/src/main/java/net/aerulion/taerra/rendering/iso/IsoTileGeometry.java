package net.aerulion.taerra.rendering.iso;

import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.RegionCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import org.jetbrains.annotations.NotNull;

public final class IsoTileGeometry {

  public static final int TILE_SIZE = MapView.ISOMETRIC.tilePixels();

  public static final int MAX_ZOOM = TileGeometry.MAX_ZOOM;

  public static final int LEAFLET_GROUND_ZOOM = TileGeometry.LEAFLET_GROUND_ZOOM;

  public static final int BASE_BLOCK_WIDTH = 8;

  public static final int CHUNK_SIZE = 16;

  private static final int LOCALITY_SEA_LEVEL = 64;

  private static final @NotNull IsoProjection BASE_PROJECTION =
      IsoProjection.ofBlockWidth(BASE_BLOCK_WIDTH);

  private IsoTileGeometry() {
    super();
  }

  public static @NotNull IsoProjection projection() {
    return BASE_PROJECTION;
  }

  public static long screenPixelsPerTile(final int zoom) {
    return (long) TILE_SIZE << zoom;
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

  public static @NotNull ScreenBounds tileBounds(final int tileX, final int tileY, final int zoom) {
    final long pixelsPerTile = screenPixelsPerTile(zoom);
    final long originX = tileX * pixelsPerTile;
    final long originY = tileY * pixelsPerTile;
    return new ScreenBounds(
        originX, originY, originX + pixelsPerTile - 1, originY + pixelsPerTile - 1);
  }

  public static int screenToTileX(final long screenX, final int zoom) {
    return Math.toIntExact(Math.floorDiv(screenX, screenPixelsPerTile(zoom)));
  }

  public static int screenToTileY(final long screenY, final int zoom) {
    return Math.toIntExact(Math.floorDiv(screenY, screenPixelsPerTile(zoom)));
  }

  public static @NotNull ScreenBounds chunkScreenBounds(
      final int chunkX,
      final int chunkZ,
      final int lowestY,
      final int maxTopY,
      final @NotNull MapRotation rotation) {
    return blockAreaScreenBounds(
        (long) chunkX * CHUNK_SIZE,
        (long) chunkZ * CHUNK_SIZE,
        CHUNK_SIZE,
        lowestY,
        maxTopY,
        rotation);
  }

  public static @NotNull ScreenBounds blockAreaScreenBounds(
      final long firstBlockX,
      final long firstBlockZ,
      final int size,
      final int lowestY,
      final int maxTopY,
      final @NotNull MapRotation rotation) {
    final int halfWidth = BASE_PROJECTION.halfWidth();
    final int quarterWidth = BASE_PROJECTION.quarterWidth();

    final long cornerX = rotation.rotatedX(firstBlockX, firstBlockZ);
    final long cornerZ = rotation.rotatedZ(firstBlockX, firstBlockZ);
    final long oppositeX = rotation.rotatedX(firstBlockX + size - 1, firstBlockZ + size - 1);
    final long oppositeZ = rotation.rotatedZ(firstBlockX + size - 1, firstBlockZ + size - 1);
    final long firstRotatedX = Math.min(cornerX, oppositeX);
    final long firstRotatedZ = Math.min(cornerZ, oppositeZ);
    final long lastRotatedX = firstRotatedX + size - 1;
    final long lastRotatedZ = firstRotatedZ + size - 1;

    final long minX = (firstRotatedX - lastRotatedZ) * halfWidth - halfWidth;
    final long maxX = (lastRotatedX - firstRotatedZ) * halfWidth + halfWidth - 1;
    final long minY =
        (firstRotatedX + firstRotatedZ) * quarterWidth - (long) (maxTopY + 1) * halfWidth;
    final long maxY =
        (lastRotatedX + lastRotatedZ) * quarterWidth
            - ((long) lowestY + 1) * halfWidth
            + BASE_PROJECTION.blockScreenHeight()
            - 1;
    return new ScreenBounds(minX, minY, maxX, maxY);
  }

  public static long localityKey(
      final @NotNull MapRotation rotation, final int tileX, final int tileY) {
    final long centerX = (long) tileX * TILE_SIZE + TILE_SIZE / 2;
    final long centerY = (long) tileY * TILE_SIZE + TILE_SIZE / 2;

    final long difference = Math.floorDiv(centerX, BASE_PROJECTION.halfWidth());
    final long sum =
        Math.floorDiv(
            centerY + (long) LOCALITY_SEA_LEVEL * BASE_PROJECTION.halfWidth(),
            BASE_PROJECTION.quarterWidth());
    final int rotatedX = Math.toIntExact(Math.floorDiv(sum + difference, 2));
    final int rotatedZ = Math.toIntExact(Math.floorDiv(sum - difference, 2));
    return RegionCoordinate.locality(
        rotation.worldX(rotatedX, rotatedZ), rotation.worldZ(rotatedX, rotatedZ));
  }

  public static void forEachTileCovering(
      final long firstBlockX,
      final long firstBlockZ,
      final int size,
      final int minWorldY,
      final int maxWorldY,
      final @NotNull MapRotation rotation,
      final @NotNull TileVisitor visitor) {
    final @NotNull ScreenBounds bounds =
        blockAreaScreenBounds(firstBlockX, firstBlockZ, size, minWorldY, maxWorldY - 1, rotation);
    final int firstTileX = screenToTileX(bounds.minX(), 0);
    final int lastTileX = screenToTileX(bounds.maxX(), 0);
    final int firstTileY = screenToTileY(bounds.minY(), 0);
    final int lastTileY = screenToTileY(bounds.maxY(), 0);
    for (int tileX = firstTileX; tileX <= lastTileX; tileX++) {
      for (int tileY = firstTileY; tileY <= lastTileY; tileY++) {
        visitor.visit(tileX, tileY);
      }
    }
  }

  public static void forEachCandidateChunk(
      final @NotNull ScreenBounds bounds,
      final int minWorldY,
      final int maxWorldY,
      final @NotNull MapRotation rotation,
      final @NotNull ChunkVisitor visitor) {
    final int halfWidth = BASE_PROJECTION.halfWidth();
    final int quarterWidth = BASE_PROJECTION.quarterWidth();
    final long horizontalStep = (long) CHUNK_SIZE * halfWidth;
    final long verticalStep = (long) CHUNK_SIZE * quarterWidth;

    final long minDifference = Math.ceilDiv(bounds.minX() - horizontalStep + 1, horizontalStep);
    final long maxDifference = Math.floorDiv(bounds.maxX() + horizontalStep, horizontalStep);

    final long minSumNumerator =
        bounds.minY()
            + ((long) minWorldY + 1) * halfWidth
            - BASE_PROJECTION.blockScreenHeight()
            + 1
            - (long) (2 * CHUNK_SIZE - 2) * quarterWidth;
    final long minSum = Math.ceilDiv(minSumNumerator, verticalStep);
    final long maxSum = Math.floorDiv(bounds.maxY() + (long) maxWorldY * halfWidth, verticalStep);

    for (long difference = minDifference; difference <= maxDifference; difference++) {

      long sum = minSum;
      if (((sum + difference) & 1L) != 0L) {
        sum++;
      }
      for (; sum <= maxSum; sum += 2) {

        final int rotatedChunkX = Math.toIntExact((sum + difference) / 2);
        final int rotatedChunkZ = Math.toIntExact((sum - difference) / 2);
        visitor.visit(
            rotation.worldX(rotatedChunkX, rotatedChunkZ),
            rotation.worldZ(rotatedChunkX, rotatedChunkZ));
      }
    }
  }
}

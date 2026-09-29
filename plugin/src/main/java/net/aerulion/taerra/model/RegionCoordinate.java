package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;

public record RegionCoordinate(int x, int z) {

  private static final int ZOOM0_TILES_PER_REGION = 8;
  private static final int LOCALITY_CELL_BLOCKS = 64;
  private static final int LOCALITY_BITS = 21;

  public static @NotNull RegionCoordinate fromChunk(final int chunkX, final int chunkZ) {
    return new RegionCoordinate(Math.floorDiv(chunkX, 32), Math.floorDiv(chunkZ, 32));
  }

  public static @NotNull RegionCoordinate fromZoom0Tile(
      final @NotNull TileCoordinate tileCoordinate) {
    return new RegionCoordinate(
        Math.floorDiv(tileCoordinate.x(), ZOOM0_TILES_PER_REGION),
        Math.floorDiv(tileCoordinate.z(), ZOOM0_TILES_PER_REGION));
  }

  public static long locality(final long blockX, final long blockZ) {
    final long cellX = Math.floorDiv(blockX, LOCALITY_CELL_BLOCKS) + (1L << (LOCALITY_BITS - 1));
    final long cellZ = Math.floorDiv(blockZ, LOCALITY_CELL_BLOCKS) + (1L << (LOCALITY_BITS - 1));
    long key = 0L;
    for (int bit = 0; bit < LOCALITY_BITS; bit++) {
      key |= ((cellX >> bit) & 1L) << (2 * bit) | ((cellZ >> bit) & 1L) << (2 * bit + 1);
    }
    return key;
  }

  public long locality() {
    return locality((long) this.x * 512, (long) this.z * 512);
  }
}

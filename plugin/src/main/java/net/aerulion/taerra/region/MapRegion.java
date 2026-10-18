package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;

public record MapRegion(
    int id,
    @NotNull String world,
    @NotNull String name,
    @NotNull RegionFog fog,
    boolean discoverable,
    @NotNull LongSet chunks,
    int minChunkX,
    int minChunkZ,
    int maxChunkX,
    int maxChunkZ) {

  public static @NotNull MapRegion of(
      final int id,
      final @NotNull String world,
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull LongSet chunks) {
    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (final long chunk : chunks) {
      minX = Math.min(minX, ChunkCoordinate.keyX(chunk));
      minZ = Math.min(minZ, ChunkCoordinate.keyZ(chunk));
      maxX = Math.max(maxX, ChunkCoordinate.keyX(chunk));
      maxZ = Math.max(maxZ, ChunkCoordinate.keyZ(chunk));
    }
    return new MapRegion(
        id, world, name, fog, discoverable, LongSets.unmodifiable(chunks), minX, minZ, maxX, maxZ);
  }

  public boolean contains(final int chunkX, final int chunkZ) {
    return this.chunks.contains(ChunkCoordinate.key(chunkX, chunkZ));
  }

  public boolean reaches(
      final int minChunkX,
      final int minChunkZ,
      final int maxChunkX,
      final int maxChunkZ,
      final int reach) {
    return this.minChunkX - reach <= maxChunkX
        && this.maxChunkX + reach >= minChunkX
        && this.minChunkZ - reach <= maxChunkZ
        && this.maxChunkZ + reach >= minChunkZ;
  }

  public @NotNull MapRegion with(
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull LongSet chunks) {
    return of(this.id, this.world, name, fog, discoverable, chunks);
  }
}

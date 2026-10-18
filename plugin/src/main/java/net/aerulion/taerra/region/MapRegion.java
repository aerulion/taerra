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
    @NotNull LongSet chunks) {

  public static @NotNull MapRegion of(
      final int id,
      final @NotNull String world,
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull LongSet chunks) {
    return new MapRegion(id, world, name, fog, discoverable, LongSets.unmodifiable(chunks));
  }

  public boolean contains(final int chunkX, final int chunkZ) {
    return this.chunks.contains(ChunkCoordinate.key(chunkX, chunkZ));
  }

  public @NotNull MapRegion with(
      final @NotNull String name,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull LongSet chunks) {
    return of(this.id, this.world, name, fog, discoverable, chunks);
  }
}

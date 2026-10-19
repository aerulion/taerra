package net.aerulion.taerra.region;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.UUID;
import net.aerulion.taerra.model.ChunkCoordinate;
import org.jetbrains.annotations.NotNull;

public record MapRegion(
    @NotNull UUID id,
    @NotNull String world,
    @NotNull String name,
    int color,
    @NotNull RegionFog fog,
    boolean discoverable,
    long reset,
    @NotNull LongSet chunks) {

  public static final int @NotNull [] PALETTE = {
    0x3AB3DA, 0xB02E26, 0x80C71F, 0xF9801D, 0x8932B8, 0xFED83D, 0x169C9C, 0xC74EBD, 0x3C44AA,
    0x5E7C16, 0xF38BAA, 0x835432
  };

  public static @NotNull MapRegion of(
      final @NotNull UUID id,
      final @NotNull String world,
      final @NotNull String name,
      final int color,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final long reset,
      final @NotNull LongSet chunks) {
    return new MapRegion(
        id, world, name, color & 0xFFFFFF, fog, discoverable, reset, LongSets.unmodifiable(chunks));
  }

  public boolean contains(final int chunkX, final int chunkZ) {
    return this.chunks.contains(ChunkCoordinate.key(chunkX, chunkZ));
  }

  public @NotNull MapRegion with(
      final @NotNull String name,
      final int color,
      final @NotNull RegionFog fog,
      final boolean discoverable,
      final @NotNull LongSet chunks) {
    return of(this.id, this.world, name, color, fog, discoverable, this.reset, chunks);
  }

  public @NotNull MapRegion withReset(final long reset) {
    return new MapRegion(
        this.id,
        this.world,
        this.name,
        this.color,
        this.fog,
        this.discoverable,
        reset,
        this.chunks);
  }
}

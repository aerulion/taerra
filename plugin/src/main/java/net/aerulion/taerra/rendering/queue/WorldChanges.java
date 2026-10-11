package net.aerulion.taerra.rendering.queue;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.style.MapStyle;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

public final class WorldChanges {

  private static final int HALO = 1;
  private static final int SHADOW_DISTANCE = MapStyle.TOP_DOWN_SHADOW_DISTANCE;

  private final @NotNull ChangeBuffer buffer;
  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull TaerraConfig config;
  private final @NotNull IsoTileService isoTiles;
  private final @NotNull Predicate<? super World> pregenerating;

  public WorldChanges(
      final @NotNull ChangeBuffer buffer,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull TaerraConfig config,
      final @NotNull IsoTileService isoTiles,
      final @NotNull Predicate<? super World> pregenerating) {
    super();
    this.buffer = buffer;
    this.columns = columns;
    this.config = config;
    this.isoTiles = isoTiles;
    this.pregenerating = pregenerating;
  }

  private static boolean isVisibleFromAbove(
      final @NotNull World world, final int blockX, final int blockY, final int blockZ) {
    return world.isChunkLoaded(blockX >> 4, blockZ >> 4)
        && blockY >= world.getHighestBlockYAt(blockX, blockZ, HeightMap.OCEAN_FLOOR);
  }

  private void collectColumn(
      final @NotNull Collection<? super TileCoordinate> tiles,
      final @NotNull String worldName,
      final int blockX,
      final int blockZ) {
    final int tileSize = TileGeometry.TILE_SIZE;

    final int minTileX = Math.floorDiv(blockX - HALO - SHADOW_DISTANCE, tileSize);
    final int maxTileX = Math.floorDiv(blockX + HALO, tileSize);
    final int minTileZ = Math.floorDiv(blockZ - HALO, tileSize);
    final int maxTileZ = Math.floorDiv(blockZ + HALO + SHADOW_DISTANCE, tileSize);

    for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
      for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
        tiles.add(TileCoordinate.topDown(worldName, 0, tileX, tileZ));
      }
    }
  }

  public void blocksChanged(final @NotNull List<? extends Block> blocks) {
    if (blocks.isEmpty()) {
      return;
    }
    final @NotNull World world = blocks.getFirst().getWorld();
    final @NotNull String worldName = WorldKeys.id(world);
    if (!this.config.worldEnabled(worldName)) {
      return;
    }

    final @NotNull Set<TileCoordinate> tiles = new HashSet<>();
    final @NotNull Long2LongOpenHashMap spans = new Long2LongOpenHashMap();
    for (final @NotNull Block block : blocks) {
      final long span = (long) block.getY() << 32 | block.getY() & 0xFFFFFFFFL;
      spans.mergeLong(
          ChunkCoordinate.key(block.getX() >> 4, block.getZ() >> 4),
          span,
          (kept, added) ->
              (long) Math.min((int) (kept >> 32), (int) (added >> 32)) << 32
                  | Math.max((int) kept, (int) added) & 0xFFFFFFFFL);
      if (isVisibleFromAbove(world, block.getX(), block.getY(), block.getZ())) {
        collectColumn(tiles, worldName, block.getX(), block.getZ());
      }
    }
    for (final Long2LongMap.@NotNull Entry entry : spans.long2LongEntrySet()) {
      final int chunkX = ChunkCoordinate.keyX(entry.getLongKey());
      final int chunkZ = ChunkCoordinate.keyZ(entry.getLongKey());
      final int lowest = (int) (entry.getLongValue() >> 32);
      final int highest = (int) entry.getLongValue();
      tiles.addAll(this.isoTiles.invalidateChunk(world, chunkX, chunkZ, lowest));
      if (highest != lowest) {
        tiles.addAll(this.isoTiles.invalidateChunk(world, chunkX, chunkZ, highest));
      }
      this.columns.markChanged(worldName, chunkX, chunkZ);
    }
    this.buffer.add(tiles);
  }

  public void chunkGenerated(final @NotNull World world, final int chunkX, final int chunkZ) {
    if (!this.config.worldEnabled(WorldKeys.id(world)) || this.pregenerating.test(world)) {
      return;
    }

    final @NotNull Set<TileCoordinate> tiles = new HashSet<>();
    tiles.addAll(this.isoTiles.invalidateChunk(world, chunkX, chunkZ, null));
    this.columns.markChanged(WorldKeys.id(world), chunkX, chunkZ);

    final int minBlockX = chunkX * 16;
    final int minBlockZ = chunkZ * 16;
    collectColumn(tiles, WorldKeys.id(world), minBlockX, minBlockZ);
    collectColumn(tiles, WorldKeys.id(world), minBlockX + 15, minBlockZ + 15);
    this.buffer.add(tiles);
  }
}

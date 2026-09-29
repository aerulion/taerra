package net.aerulion.taerra.listener.world;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
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
import net.aerulion.taerra.rendering.queue.ChangeBuffer;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.style.MapStyle;
import org.bukkit.HeightMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.jetbrains.annotations.NotNull;

public final class WorldChangeListener implements Listener {

  private static final int HALO = 1;
  private static final int SHADOW_DISTANCE = MapStyle.SHADOW_DISTANCE;

  private final @NotNull ChangeBuffer changes;
  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull TaerraConfig config;
  private final @NotNull IsoTileService isoTiles;
  private final @NotNull Predicate<? super World> pregenerating;

  public WorldChangeListener(
      final @NotNull ChangeBuffer changes,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull TaerraConfig config,
      final @NotNull IsoTileService isoTiles,
      final @NotNull Predicate<? super World> pregenerating) {
    super();
    this.changes = changes;
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

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockPlace(final @NotNull BlockPlaceEvent event) {
    markBlock(event.getBlockPlaced());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockMultiPlace(final @NotNull BlockMultiPlaceEvent event) {
    markBlockStates(event.getReplacedBlockStates());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBreak(final @NotNull BlockBreakEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockBurn(final @NotNull BlockBurnEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockIgnite(final @NotNull BlockIgniteEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockForm(final @NotNull BlockFormEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockSpread(final @NotNull BlockSpreadEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFade(final @NotNull BlockFadeEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockGrow(final @NotNull BlockGrowEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onLeavesDecay(final @NotNull LeavesDecayEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFromTo(final @NotNull BlockFromToEvent event) {
    markBlock(event.getToBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityChangeBlock(final @NotNull EntityChangeBlockEvent event) {
    markBlock(event.getBlock());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerBucketEmpty(final @NotNull PlayerBucketEmptyEvent event) {
    markBucketBlocks(event);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerBucketFill(final @NotNull PlayerBucketFillEvent event) {
    markBucketBlocks(event);
  }

  private void markBucketBlocks(final @NotNull PlayerBucketEvent event) {
    markBlock(event.getBlockClicked());
    markBlock(event.getBlockClicked().getRelative(event.getBlockFace()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onEntityExplode(final @NotNull EntityExplodeEvent event) {
    markBlocks(event.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockExplode(final @NotNull BlockExplodeEvent event) {
    markBlocks(event.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonExtend(final @NotNull BlockPistonExtendEvent event) {
    markMovedBlocks(event.getBlocks(), event.getBlock().getWorld(), event.getDirection());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPistonRetract(final @NotNull BlockPistonRetractEvent event) {
    markMovedBlocks(event.getBlocks(), event.getBlock().getWorld(), event.getDirection());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onStructureGrow(final @NotNull StructureGrowEvent event) {
    markBlockStates(event.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockFertilize(final @NotNull BlockFertilizeEvent event) {
    markBlockStates(event.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSpongeAbsorb(final @NotNull SpongeAbsorbEvent event) {
    markBlockStates(event.getBlocks());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(final @NotNull ChunkLoadEvent event) {
    if (!event.isNewChunk()) {
      return;
    }
    markChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkPopulate(final @NotNull ChunkPopulateEvent event) {
    markChunk(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
  }

  private void markBlock(final @NotNull Block block) {
    markBlocks(List.of(block));
  }

  private void markBlockStates(final @NotNull List<? extends BlockState> states) {
    markBlocks(states.stream().map(BlockState::getBlock).toList());
  }

  private void markMovedBlocks(
      final @NotNull List<? extends Block> blocks,
      final @NotNull World world,
      final @NotNull BlockFace direction) {
    final @NotNull List<Block> touched = new ArrayList<>(blocks.size() * 2);
    for (final @NotNull Block block : blocks) {
      touched.add(block);
      touched.add(block.getRelative(direction));
    }
    markBlocks(touched);
  }

  private void markBlocks(final @NotNull List<? extends Block> blocks) {
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
    this.changes.add(tiles);
  }

  private void markChunk(final @NotNull World world, final int chunkX, final int chunkZ) {
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
    this.changes.add(tiles);
  }
}

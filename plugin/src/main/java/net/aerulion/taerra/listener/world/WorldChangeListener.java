package net.aerulion.taerra.listener.world;

import java.util.ArrayList;
import java.util.List;
import net.aerulion.taerra.rendering.queue.WorldChanges;
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
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.jetbrains.annotations.NotNull;

public final class WorldChangeListener implements Listener {

  private final @NotNull WorldChanges changes;

  public WorldChangeListener(final @NotNull WorldChanges changes) {
    super();
    this.changes = changes;
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
    this.changes.blocksChanged(event.blockList());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onBlockExplode(final @NotNull BlockExplodeEvent event) {
    this.changes.blocksChanged(event.blockList());
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
  public void onChunkPopulate(final @NotNull ChunkPopulateEvent event) {
    this.changes.chunkGenerated(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
  }

  private void markBlock(final @NotNull Block block) {
    this.changes.blocksChanged(List.of(block));
  }

  private void markBlockStates(final @NotNull List<? extends BlockState> states) {
    this.changes.blocksChanged(states.stream().map(BlockState::getBlock).toList());
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
    this.changes.blocksChanged(touched);
  }
}

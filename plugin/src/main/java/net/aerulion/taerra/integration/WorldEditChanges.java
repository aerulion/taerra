package net.aerulion.taerra.integration;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.rendering.queue.WorldChanges;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class WorldEditChanges {

  private final @NotNull Plugin plugin;
  private final @NotNull WorldChanges changes;
  private final @NotNull Map<World, Long2LongOpenHashMap> columns = new HashMap<>();

  private WorldEditChanges(final @NotNull Plugin plugin, final @NotNull WorldChanges changes) {
    super();
    this.plugin = plugin;
    this.changes = changes;
  }

  public static void start(final @NotNull Plugin plugin, final @NotNull WorldChanges changes) {
    WorldEdit.getInstance().getEventBus().register(new WorldEditChanges(plugin, changes));
  }

  private static long span(final int lowest, final int highest) {
    return (long) lowest << 32 | highest & 0xFFFFFFFFL;
  }

  private static long widen(final long kept, final long added) {
    return span(
        Math.min((int) (kept >> 32), (int) (added >> 32)), Math.max((int) kept, (int) added));
  }

  @Subscribe
  public void onEditSession(final @NotNull EditSessionEvent event) {
    if (event.getStage() != EditSession.Stage.BEFORE_CHANGE || event.getWorld() == null) {
      return;
    }
    final @NotNull World world = BukkitAdapter.adapt(event.getWorld());
    event.setExtent(
        new AbstractDelegateExtent(event.getExtent()) {
          @Override
          public <T extends BlockStateHolder<T>> boolean setBlock(
              final @NotNull BlockVector3 position, final @NotNull T block)
              throws WorldEditException {
            final boolean changed = super.setBlock(position, block);
            if (changed) {
              mark(world, position);
            }
            return changed;
          }

          @Override
          public boolean setBiome(
              final @NotNull BlockVector3 position, final @NotNull BiomeType biome) {
            final boolean changed = super.setBiome(position, biome);
            if (changed) {
              mark(world, position);
            }
            return changed;
          }
        });
  }

  private synchronized void mark(final @NotNull World world, final @NotNull BlockVector3 position) {
    if (this.columns.isEmpty()) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, this::flush);
    }
    this.columns
        .computeIfAbsent(world, _ -> new Long2LongOpenHashMap())
        .mergeLong(
            ChunkCoordinate.key(position.x(), position.z()),
            span(position.y(), position.y()),
            WorldEditChanges::widen);
  }

  private void flush() {
    final @NotNull Map<World, Long2LongOpenHashMap> taken;
    synchronized (this) {
      taken = new HashMap<>(this.columns);
      this.columns.clear();
    }
    taken.forEach(
        (world, spans) -> {
          final @NotNull List<Block> blocks = new ArrayList<>(spans.size() * 2);
          for (final Long2LongMap.@NotNull Entry entry : spans.long2LongEntrySet()) {
            final int x = ChunkCoordinate.keyX(entry.getLongKey());
            final int z = ChunkCoordinate.keyZ(entry.getLongKey());
            final int lowest = (int) (entry.getLongValue() >> 32);
            final int highest = (int) entry.getLongValue();
            blocks.add(world.getBlockAt(x, highest, z));
            if (lowest != highest) {
              blocks.add(world.getBlockAt(x, lowest, z));
            }
          }
          this.changes.blocksChanged(blocks);
        });
  }
}

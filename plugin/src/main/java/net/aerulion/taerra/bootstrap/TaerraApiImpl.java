package net.aerulion.taerra.bootstrap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.aerulion.taerra.api.TaerraApi;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.region.Regions;
import net.aerulion.taerra.rendering.queue.WorldChanges;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

record TaerraApiImpl(
    @NotNull Plugin plugin,
    @NotNull MarkerRegistry markers,
    @NotNull MarkerIcons icons,
    @NotNull Regions regions,
    @NotNull WorldChanges changes)
    implements TaerraApi {

  @Override
  public void blocksChanged(final @NotNull Collection<? extends Block> blocks) {
    final @NotNull List<Block> copy = List.copyOf(blocks);
    onMainThread(
        () ->
            copy.stream()
                .collect(Collectors.groupingBy(Block::getWorld))
                .values()
                .forEach(this.changes::blocksChanged));
  }

  @Override
  public void areaChanged(
      final @NotNull World world,
      final int x1,
      final int y1,
      final int z1,
      final int x2,
      final int y2,
      final int z2) {
    final int lowest = Math.max(Math.min(y1, y2), world.getMinHeight());
    final int highest = Math.min(Math.max(y1, y2), world.getMaxHeight() - 1);
    if (lowest > highest) {
      return;
    }
    onMainThread(
        () -> {
          final @NotNull List<Block> blocks = new ArrayList<>();
          for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
              blocks.add(world.getBlockAt(x, highest, z));
              if (lowest != highest) {
                blocks.add(world.getBlockAt(x, lowest, z));
              }
            }
          }
          this.changes.blocksChanged(blocks);
        });
  }

  private void onMainThread(final @NotNull Runnable task) {
    if (this.plugin.getServer().isPrimaryThread()) {
      task.run();
    } else {
      this.plugin.getServer().getScheduler().runTask(this.plugin, task);
    }
  }
}

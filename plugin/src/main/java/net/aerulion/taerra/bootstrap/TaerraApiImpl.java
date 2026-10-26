package net.aerulion.taerra.bootstrap;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import net.aerulion.taerra.api.TaerraApi;
import net.aerulion.taerra.api.marker.MarkerIcons;
import net.aerulion.taerra.api.marker.MarkerRegistry;
import net.aerulion.taerra.api.navigation.Navigation;
import net.aerulion.taerra.api.player.MapPlayers;
import net.aerulion.taerra.api.region.Regions;
import net.aerulion.taerra.api.waypoint.Waypoints;
import net.aerulion.taerra.config.WebSettings;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.queue.WorldChanges;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record TaerraApiImpl(
    @NotNull Plugin plugin,
    @NotNull MarkerRegistry markers,
    @NotNull MarkerIcons icons,
    @NotNull Regions regions,
    @NotNull Waypoints waypoints,
    @NotNull Navigation navigation,
    @NotNull MapPlayers players,
    @NotNull WebSettings web,
    @NotNull WorldChanges changes)
    implements TaerraApi {

  private static @NotNull String encoded(final @NotNull String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @Override
  public @NotNull String mapUrl() {
    return this.web.baseUrl();
  }

  @Override
  public @NotNull String mapUrl(final @NotNull World world) {
    return mapUrl() + "/#world=" + encoded(WorldKeys.id(world));
  }

  @Override
  public @NotNull String mapUrl(final @NotNull Location location) {
    final @Nullable World world = location.getWorld();
    if (world == null) {
      throw new IllegalArgumentException("The location has no world");
    }
    return mapUrl(world)
        + "&x="
        + location.getBlockX()
        + "&z="
        + location.getBlockZ()
        + "&y="
        + location.getBlockY();
  }

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

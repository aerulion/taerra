package net.aerulion.taerra.world;

import java.util.concurrent.CompletableFuture;
import net.aerulion.taerra.rendering.sample.ColumnRunSampler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WorldPositions {

  private WorldPositions() {
    super();
  }

  public static boolean insideBorder(final @NotNull World world, final int x, final int z) {
    return world.getWorldBorder().isInside(new Location(world, x + .5, 0, z + .5));
  }

  public static boolean nearBorder(final @NotNull World world, final double x, final double z) {
    final @NotNull WorldBorder border = world.getWorldBorder();
    final double reach = border.getSize() / 2 + 1;
    return Math.abs(x - border.getCenter().getX()) <= reach
        && Math.abs(z - border.getCenter().getZ()) <= reach;
  }

  public static @NotNull CompletableFuture<Integer> standingY(
      final @NotNull World world, final int x, final @Nullable Integer y, final int z) {
    return y != null
        ? CompletableFuture.completedFuture(
            Math.clamp(y, world.getMinHeight(), world.getMaxHeight()))
        : world.getChunkAtAsync(x >> 4, z >> 4).thenApply(_ -> surfaceY(world, x, z));
  }

  public static @NotNull CompletableFuture<Integer> clearY(
      final @NotNull World world, final int x, final @Nullable Integer y, final int z) {
    return world
        .getChunkAtAsync(x >> 4, z >> 4)
        .thenApply(
            _ -> {
              if (y != null) {
                final int feet = Math.clamp(y, world.getMinHeight(), world.getMaxHeight());
                if (!world.getBlockAt(x, feet, z).isSolid()
                    && !world.getBlockAt(x, feet + 1, z).isSolid()) {
                  return feet;
                }
              }
              return surfaceY(world, x, z);
            });
  }

  private static int surfaceY(final @NotNull World world, final int x, final int z) {
    if (world.hasCeiling()) {
      for (int y = ColumnRunSampler.ceilingY(world); y > world.getMinHeight(); y--) {
        if (world.getBlockAt(x, y - 1, z).isSolid()
            && !world.getBlockAt(x, y, z).isSolid()
            && !world.getBlockAt(x, y + 1, z).isSolid()) {
          return y;
        }
      }
    }
    return world.getHighestBlockYAt(x, z) + 1;
  }
}

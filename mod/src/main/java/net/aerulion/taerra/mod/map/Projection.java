package net.aerulion.taerra.mod.map;

import java.util.List;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.World;
import org.jetbrains.annotations.NotNull;

public record Projection(boolean isometric, int rotation, double unitsPerBlock, int blockWidth) {

  public static final int TILE_PIXELS = 256;

  public static double tileUnits(final int zoom) {
    return Math.scalb((double) TILE_PIXELS, -zoom);
  }

  public static double closestZoom(
      final @NotNull MapConfig config, final boolean isometric, final int guiScale) {
    if (isometric) {
      return Math.log(config.isometric().tileSize() / tileUnits(config.isometric().maxZoom()))
          / Math.log(2);
    }
    return Math.log(config.tileSize() / tileUnits(config.maxZoom()) * guiScale) / Math.log(2);
  }

  public static int viewOffset(final @NotNull MapConfig config) {
    final double isoBlock = isometric(config, 0).unitsPerBlock() * config.isometric().blockWidth();
    return (int) Math.round(Math.log(isoBlock / topDown(config).unitsPerBlock()) / Math.log(2));
  }

  public static double scale(
      final @NotNull MapConfig config, final boolean isometric, final double zoom) {
    return zoom
        + Math.log(topDown(config).unitsPerBlock()) / Math.log(2)
        + (isometric ? viewOffset(config) : 0);
  }

  public static double zoomFor(
      final @NotNull MapConfig config, final boolean isometric, final int scale) {
    return scale - scale(config, isometric, 0);
  }

  public static int @NotNull [] scaleRange(
      final @NotNull MapConfig config, final @NotNull List<World> worlds) {
    int far = Integer.MAX_VALUE;
    int close = Integer.MAX_VALUE;
    for (final @NotNull World world : worlds) {
      if (world.shows(false)) {
        far = Math.min(far, (int) scale(config, false, world.minZoom(config, false, 0)));
        close = Math.min(close, (int) scale(config, false, config.maxZoom()));
      }
      if (world.shows(true)) {
        for (int rotation = 0; rotation < world.depth().isometric().length; rotation++) {
          far = Math.min(far, (int) scale(config, true, world.minZoom(config, true, rotation)));
        }
        close = Math.min(close, (int) scale(config, true, config.isometric().maxZoom()));
      }
    }
    return close == Integer.MAX_VALUE ? new int[] {-4, 2} : new int[] {far, close};
  }

  public static @NotNull Projection topDown(final @NotNull MapConfig config) {
    return new Projection(
        false, 0, tileUnits(config.maxZoom()) / config.tileSize(), config.isometric().blockWidth());
  }

  public static @NotNull Projection isometric(final @NotNull MapConfig config, final int rotation) {
    return new Projection(
        true,
        rotation,
        1 / Math.scalb(1.0, config.isometric().maxZoom()),
        config.isometric().blockWidth());
  }

  public static @NotNull Projection of(
      final @NotNull MapConfig config, final boolean isometric, final int rotation) {
    return isometric ? isometric(config, rotation) : topDown(config);
  }

  public double[] toMap(final double x, final double y, final double z) {
    if (!this.isometric) {
      return new double[] {x * this.unitsPerBlock, z * this.unitsPerBlock};
    }
    final double rotatedX;
    final double rotatedZ;
    switch (this.rotation) {
      case 1 -> {
        rotatedX = z;
        rotatedZ = -x;
      }
      case 2 -> {
        rotatedX = -x;
        rotatedZ = -z;
      }
      case 3 -> {
        rotatedX = -z;
        rotatedZ = x;
      }
      default -> {
        rotatedX = x;
        rotatedZ = z;
      }
    }
    final double half = this.blockWidth / 2.0;
    return new double[] {
      (rotatedX - rotatedZ) * half * this.unitsPerBlock,
      ((rotatedX + rotatedZ) * half / 2 - (y - 1) * half) * this.unitsPerBlock
    };
  }

  public double[] toBlock(final double mapX, final double mapY, final double y) {
    if (!this.isometric) {
      return new double[] {mapX / this.unitsPerBlock, mapY / this.unitsPerBlock};
    }
    final double half = this.blockWidth / 2.0;
    final double difference = mapX / this.unitsPerBlock / half;
    final double sum = (mapY / this.unitsPerBlock + (y - 1) * half) / (half / 2);
    final double rotatedX = (sum + difference) / 2;
    final double rotatedZ = (sum - difference) / 2;
    return switch (this.rotation) {
      case 1 -> new double[] {-rotatedZ, rotatedX};
      case 2 -> new double[] {-rotatedX, -rotatedZ};
      case 3 -> new double[] {rotatedZ, -rotatedX};
      default -> new double[] {rotatedX, rotatedZ};
    };
  }

  public double bearing(final float yaw) {
    final double radians = Math.toRadians(yaw);
    final double @NotNull [] origin = toMap(0, 0, 0);
    final double @NotNull [] ahead = toMap(-Math.sin(radians), 0, Math.cos(radians));
    return Math.atan2(ahead[0] - origin[0], origin[1] - ahead[1]);
  }
}

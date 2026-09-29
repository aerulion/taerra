package net.aerulion.taerra.mod.map;

import net.aerulion.taerra.mod.web.model.MapConfig;
import org.jetbrains.annotations.NotNull;

public record Projection(boolean isometric, int rotation, double unitsPerBlock, int blockWidth) {

  public static final int TILE_PIXELS = 256;

  public static double tileUnits(final int zoom) {
    return Math.scalb((double) TILE_PIXELS, -zoom);
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

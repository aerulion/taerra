package net.aerulion.taerra.rendering.style;

import org.jetbrains.annotations.NotNull;

public final class SunShadow {

  public static final int NO_COLUMN = Integer.MIN_VALUE;

  private static final int FIXED_POINT_BITS = 10;

  private SunShadow() {
    super();
  }

  public static boolean isShadowed(
      final @NotNull HeightField field,
      final int blockX,
      final int blockZ,
      final int topY,
      final int maxDistance) {
    return isShadowed(
        field, blockX, blockZ, topY, maxDistance, MapStyle.SUN_STEP_X, MapStyle.SUN_STEP_Z);
  }

  public static boolean isShadowed(
      final @NotNull HeightField field,
      final int blockX,
      final int blockZ,
      final int topY,
      final int maxDistance,
      final int sunStepX,
      final int sunStepZ) {
    for (int step = 1; step <= maxDistance; step++) {
      final int sampleX = advance(blockX, step, sunStepX);
      final int sampleZ = advance(blockZ, step, sunStepZ);
      if (topY + rise(step) > field.ceilingY()) {
        return false;
      }
      final int occluderTop = field.topYAt(sampleX, sampleZ);
      if (occluderTop == NO_COLUMN) {
        continue;
      }

      if (occluderTop >= topY + rise(step)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isShadowedThrough(
      final @NotNull SolidField field,
      final int originX,
      final int openY,
      final int originZ,
      final int maxDistance,
      final int sunStepX,
      final int sunStepZ) {
    for (int step = 1; step <= maxDistance; step++) {
      final int sampleY = openY - 1 + rise(step);
      if (sampleY > field.ceilingY()) {
        return false;
      }
      if (field.isSolidAt(
          advance(originX, step, sunStepX), sampleY, advance(originZ, step, sunStepZ))) {
        return true;
      }
    }
    return false;
  }

  private static int rise(final int step) {
    return ((step << FIXED_POINT_BITS) + MapStyle.SHADOW_LENGTH_PER_BLOCK - 1)
        / MapStyle.SHADOW_LENGTH_PER_BLOCK;
  }

  private static int advance(final int origin, final int step, final int perBlock) {
    return perBlock >= 0
        ? origin + ((step * perBlock) >> FIXED_POINT_BITS)
        : origin - ((step * -perBlock) >> FIXED_POINT_BITS);
  }
}

package net.aerulion.taerra.rendering.style;

import org.jetbrains.annotations.NotNull;

public final class SunShadow {

  public static final int NO_COLUMN = Integer.MIN_VALUE;

  private static final int FIXED_POINT_BITS = 10;

  private SunShadow() {
    super();
  }

  public static int reach(final int maxDistance) {
    return Math.max(
        1,
        (maxDistance * Math.max(Math.abs(MapStyle.SUN_STEP_X), Math.abs(MapStyle.SUN_STEP_Z))
            >> FIXED_POINT_BITS));
  }

  public static int isoDrop(final int maxDistance) {
    return rise(maxDistance, MapStyle.ISO_SHADOW_LENGTH_PER_BLOCK);
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
    final int ceilingY = field.ceilingYNear(blockX, blockZ);
    for (int step = 1; step <= maxDistance; step++) {
      final int sampleX = advance(blockX, step, sunStepX);
      final int sampleZ = advance(blockZ, step, sunStepZ);
      final int rise = rise(step, MapStyle.TOP_DOWN_SHADOW_LENGTH_PER_BLOCK);
      if (topY + rise > ceilingY) {
        return false;
      }
      final int occluderTop = field.topYAt(sampleX, sampleZ);
      if (occluderTop == NO_COLUMN) {
        continue;
      }

      if (occluderTop >= topY + rise) {
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
    if (maxDistance <= 0
        || openY - 1 + rise(1, MapStyle.ISO_SHADOW_LENGTH_PER_BLOCK) > field.ceilingY()) {
      return false;
    }
    final int topY =
        field.topYBetween(
            advance(originX, 1, sunStepX),
            advance(originZ, 1, sunStepZ),
            advance(originX, maxDistance, sunStepX),
            advance(originZ, maxDistance, sunStepZ));
    for (int step = 1; step <= maxDistance; step++) {
      final int sampleY = openY - 1 + rise(step, MapStyle.ISO_SHADOW_LENGTH_PER_BLOCK);
      if (sampleY > topY) {
        return false;
      }
      if (field.isSolidAt(
          advance(originX, step, sunStepX), sampleY, advance(originZ, step, sunStepZ))) {
        return true;
      }
    }
    return false;
  }

  private static int rise(final int step, final int lengthPerBlock) {
    return ((step << FIXED_POINT_BITS) + lengthPerBlock - 1) / lengthPerBlock;
  }

  private static int advance(final int origin, final int step, final int perBlock) {
    return perBlock >= 0
        ? origin + ((step * perBlock) >> FIXED_POINT_BITS)
        : origin - ((step * -perBlock) >> FIXED_POINT_BITS);
  }
}

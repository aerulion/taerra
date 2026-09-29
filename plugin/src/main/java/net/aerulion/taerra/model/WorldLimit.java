package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;

public record WorldLimit(@NotNull Shape shape, int minX, int minZ, int maxX, int maxZ) {

  public static @NotNull WorldLimit circle(final int centerX, final int centerZ, final int radius) {
    return new WorldLimit(
        Shape.CIRCLE, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
  }

  public static @NotNull WorldLimit square(final int centerX, final int centerZ, final int radius) {
    return new WorldLimit(
        Shape.SQUARE, centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
  }

  public static @NotNull WorldLimit rectangle(
      final int fromX, final int fromZ, final int toX, final int toZ) {
    return new WorldLimit(
        Shape.RECTANGLE,
        Math.min(fromX, toX),
        Math.min(fromZ, toZ),
        Math.max(fromX, toX),
        Math.max(fromZ, toZ));
  }

  public int centerX() {
    return (this.minX + this.maxX) >> 1;
  }

  public int centerZ() {
    return (this.minZ + this.maxZ) >> 1;
  }

  public int radius() {
    return (this.maxX - this.minX) >> 1;
  }

  public boolean contains(final int blockX, final int blockZ) {
    return intersects(blockX, blockZ, blockX, blockZ);
  }

  public boolean intersects(final int minX, final int minZ, final int maxX, final int maxZ) {
    if (maxX < this.minX || minX > this.maxX || maxZ < this.minZ || minZ > this.maxZ) {
      return false;
    }
    if (this.shape != Shape.CIRCLE) {
      return true;
    }
    final long deltaX = (long) centerX() - Math.clamp(centerX(), minX, maxX);
    final long deltaZ = (long) centerZ() - Math.clamp(centerZ(), minZ, maxZ);
    return deltaX * deltaX + deltaZ * deltaZ <= (long) radius() * radius();
  }

  public boolean covers(final int minX, final int minZ, final int maxX, final int maxZ) {
    return contains(minX, minZ)
        && contains(minX, maxZ)
        && contains(maxX, minZ)
        && contains(maxX, maxZ);
  }

  public enum Shape {
    CIRCLE,
    SQUARE,
    RECTANGLE
  }
}

package net.aerulion.taerra.rendering.iso;

import org.jetbrains.annotations.NotNull;

public record ScreenBounds(long minX, long minY, long maxX, long maxY) {

  public boolean intersects(final @NotNull ScreenBounds other) {
    return this.minX <= other.maxX
        && other.minX <= this.maxX
        && this.minY <= other.maxY
        && other.minY <= this.maxY;
  }
}

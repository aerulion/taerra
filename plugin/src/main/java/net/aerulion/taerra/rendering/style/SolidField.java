package net.aerulion.taerra.rendering.style;

@FunctionalInterface
public interface SolidField {

  boolean isSolidAt(int blockX, int blockY, int blockZ);

  default int ceilingY() {
    return Integer.MAX_VALUE;
  }
}

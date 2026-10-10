package net.aerulion.taerra.rendering.style;

@FunctionalInterface
public interface HeightField {

  int topYAt(int blockX, int blockZ);

  default int ceilingYNear(int blockX, int blockZ) {
    return Integer.MAX_VALUE;
  }
}

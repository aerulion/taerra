package net.aerulion.taerra.rendering.style;

@FunctionalInterface
public interface HeightField {

  int topYAt(int blockX, int blockZ);

  default int ceilingY() {
    return Integer.MAX_VALUE;
  }
}

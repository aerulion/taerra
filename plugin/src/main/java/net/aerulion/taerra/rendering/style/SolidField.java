package net.aerulion.taerra.rendering.style;

public interface SolidField {

  boolean isSolidAt(int blockX, int blockY, int blockZ);

  int ceilingY();

  int topYBetween(int fromX, int fromZ, int toX, int toZ);
}

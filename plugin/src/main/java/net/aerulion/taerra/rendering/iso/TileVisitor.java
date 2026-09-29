package net.aerulion.taerra.rendering.iso;

@FunctionalInterface
public interface TileVisitor {

  void visit(int tileX, int tileY);
}

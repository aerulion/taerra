package net.aerulion.taerra.rendering.iso;

@FunctionalInterface
public interface ChunkVisitor {

  void visit(int chunkX, int chunkZ);
}

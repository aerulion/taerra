package net.aerulion.taerra.rendering.block;

import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface BlockShapeResolver {

  @NotNull
  BlockShape shapeOf(@NotNull BlockType blockType);

  default @NotNull BlockShape shapeOf(final int blockId) {
    return shapeOf(BlockTypes.byId(blockId));
  }
}

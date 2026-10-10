package net.aerulion.taerra.rendering.color;

import net.aerulion.taerra.rendering.block.BlockTypes;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface MapColorResolver {

  @NotNull
  BlockColors colorsOf(@NotNull BlockType blockType);

  default @NotNull BlockColors colorsOf(final int blockId) {
    return colorsOf(BlockTypes.byId(blockId));
  }
}

package net.aerulion.taerra.rendering.color;

import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface MapColorResolver {

  @NotNull
  BlockColors colorsOf(@NotNull BlockType blockType);
}

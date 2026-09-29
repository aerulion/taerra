package net.aerulion.taerra.rendering.block;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface BlockShapeResolver {

  @NotNull
  BlockShape shapeOf(@NotNull Material material);
}

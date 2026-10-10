package net.aerulion.taerra.rendering.color;

import net.aerulion.taerra.rendering.block.BlockTypes;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface MapColorResolver {

  @NotNull
  BlockColors colorsOf(@NotNull BlockType blockType);

  default @NotNull BlockColors colorsOf(final int blockId) {
    return colorsOf(BlockTypes.byId(blockId));
  }

  default boolean tintVaries(final @NotNull Biome biome) {
    return false;
  }

  default @Nullable TintColors tintAt(
      final @Nullable Biome biome, final int blockX, final int blockZ) {
    return null;
  }
}

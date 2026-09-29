package net.aerulion.taerra.rendering.color;

import java.util.Set;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface BiomeTintMapper permits BiomeTintMapperImpl {

  int applyBiomeTint(
      int baseRgb, @Nullable BlockType blockType, @Nullable Biome biome, int x, int z);

  @NotNull
  Set<BlockType> tintedBlocks();
}

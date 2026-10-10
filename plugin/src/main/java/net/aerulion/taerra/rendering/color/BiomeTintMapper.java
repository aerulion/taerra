package net.aerulion.taerra.rendering.color;

import java.util.Set;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface BiomeTintMapper permits BiomeTintMapperImpl {

  @Nullable
  BiomeTint tintOf(@NotNull BlockType blockType);

  boolean tintVaries(@NotNull Biome biome);

  @NotNull
  TintColors tintAt(@NotNull Biome biome, int x, int z);

  int applyBiomeTint(int baseRgb, @Nullable BiomeTint tint, @Nullable TintColors colors);

  @NotNull
  Set<BlockType> tintedBlocks();
}

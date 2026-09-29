package net.aerulion.taerra.rendering.color;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MapColorResolver {

  int topColor(@NotNull Material material, @Nullable Biome biome, int blockX, int blockZ);

  int sideColor(@NotNull Material material, @Nullable Biome biome, int blockX, int blockZ);
}

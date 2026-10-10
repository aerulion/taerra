package net.aerulion.taerra.rendering.color;

import org.bukkit.block.Biome;
import org.jetbrains.annotations.Nullable;

public interface BlockColors {

  int topColor(@Nullable Biome biome, int blockX, int blockZ);

  int sideColor(@Nullable Biome biome, int blockX, int blockZ);

  default boolean emitsLight() {
    return false;
  }
}

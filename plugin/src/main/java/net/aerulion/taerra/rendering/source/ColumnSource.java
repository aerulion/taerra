package net.aerulion.taerra.rendering.source;

import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.jetbrains.annotations.NotNull;

public interface ColumnSource {

  int EMPTY_COLUMN = Integer.MIN_VALUE;

  int minHeight();

  int maxHeight();

  int highestNonAirY(int localX, int localZ);

  boolean isSectionAllAir(int y);

  default boolean isSectionAllFull(final int y, final @NotNull BlockShapeResolver shapes) {
    return false;
  }

  @NotNull
  Material materialAt(int localX, int y, int localZ);

  @NotNull
  Biome biomeAt(int localX, int y, int localZ);
}

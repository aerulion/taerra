package net.aerulion.taerra.rendering.color;

import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.jetbrains.annotations.NotNull;

record BiomeColors(int water, int grass, boolean swampGrass, int foliage, int dryFoliage) {

  static @NotNull BiomeColors of(final @NotNull Biome biome) {
    final net.minecraft.world.level.biome.@NotNull Biome handle = ((CraftBiome) biome).getHandle();
    return new BiomeColors(
        handle.getWaterColor(),
        handle.getGrassColor(0, 0),
        handle.getSpecialEffects().grassColorModifier()
            == BiomeSpecialEffects.GrassColorModifier.SWAMP,
        handle.getFoliageColor(),
        handle.getDryFoliageColor());
  }
}

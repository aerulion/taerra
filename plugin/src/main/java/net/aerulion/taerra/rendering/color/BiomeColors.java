package net.aerulion.taerra.rendering.color;

import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.bukkit.block.Biome;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.jetbrains.annotations.NotNull;

record BiomeColors(@NotNull TintColors tint, boolean swampGrass) {

  static @NotNull BiomeColors of(final @NotNull Biome biome) {
    final net.minecraft.world.level.biome.@NotNull Biome handle = ((CraftBiome) biome).getHandle();
    return new BiomeColors(
        new TintColors(
            handle.getWaterColor(),
            handle.getGrassColor(0, 0),
            handle.getFoliageColor(),
            handle.getDryFoliageColor()),
        handle.getSpecialEffects().grassColorModifier()
            == BiomeSpecialEffects.GrassColorModifier.SWAMP);
  }
}

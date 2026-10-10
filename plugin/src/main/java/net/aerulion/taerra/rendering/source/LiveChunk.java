package net.aerulion.taerra.rendering.source;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;

public record LiveChunk(
    @NotNull List<PalettedContainer<BlockState>> states,
    @NotNull List<PalettedContainerRO<Holder<Biome>>> biomes,
    @NotNull PalettedContainerFactory factory) {

  public static @NotNull LiveChunk capture(
      final @NotNull World world, final int chunkX, final int chunkZ) {
    final @NotNull ServerLevel level = ((CraftWorld) world).getHandle();
    final @NotNull List<PalettedContainer<BlockState>> states = new ArrayList<>();
    final @NotNull List<PalettedContainerRO<Holder<Biome>>> biomes = new ArrayList<>();
    for (final @NotNull LevelChunkSection section : level.getChunk(chunkX, chunkZ).getSections()) {
      states.add(section.getStates().copy());
      biomes.add(section.getBiomes().copy());
    }
    return new LiveChunk(states, biomes, level.palettedContainerFactory());
  }
}

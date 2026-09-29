package net.aerulion.taerra.rendering.color;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.FoliageColor;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.block.CraftBiome;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BiomeTintMapperImpl implements BiomeTintMapper {

  private static final int OPAQUE = 0xFF000000;
  private static final int RGB_MASK = 0x00FFFFFF;

  private static final int LILY_PAD_TINT = 0x208030;

  private final @NotNull Map<BlockType, BiomeTint> tints = buildTints();

  private final @NotNull Map<Biome, BiomeColors> biomeColors = new ConcurrentHashMap<>();

  public BiomeTintMapperImpl() {
    super();
  }

  private static @NotNull Map<BlockType, BiomeTint> buildTints() {
    final @NotNull Map<BlockType, BiomeTint> tints = new Reference2ObjectOpenHashMap<>();
    for (final @NotNull BlockType blockType :
        List.of(BlockType.WATER, BlockType.BUBBLE_COLUMN, BlockType.WATER_CAULDRON)) {
      tints.put(blockType, BiomeTint.WATER);
    }
    for (final @NotNull BlockType blockType :
        List.of(
            BlockType.GRASS_BLOCK,
            BlockType.SHORT_GRASS,
            BlockType.TALL_GRASS,
            BlockType.FERN,
            BlockType.LARGE_FERN,
            BlockType.BUSH,
            BlockType.SUGAR_CANE)) {
      tints.put(blockType, BiomeTint.GRASS);
    }
    for (final @NotNull BlockType blockType :
        List.of(
            BlockType.OAK_LEAVES,
            BlockType.JUNGLE_LEAVES,
            BlockType.ACACIA_LEAVES,
            BlockType.DARK_OAK_LEAVES,
            BlockType.MANGROVE_LEAVES,
            BlockType.VINE)) {
      tints.put(blockType, BiomeTint.FOLIAGE);
    }
    tints.put(BlockType.LEAF_LITTER, BiomeTint.DRY_FOLIAGE);
    tints.put(BlockType.BIRCH_LEAVES, BiomeTint.BIRCH_FOLIAGE);
    tints.put(BlockType.SPRUCE_LEAVES, BiomeTint.EVERGREEN_FOLIAGE);
    tints.put(BlockType.LILY_PAD, BiomeTint.LILY_PAD);
    return tints;
  }

  private static int tintBaseColor(final int baseRgb, final int tintRgb) {
    final int red = (((baseRgb >> 16) & 0xFF) * ((tintRgb >> 16) & 0xFF)) / 255;
    final int green = (((baseRgb >> 8) & 0xFF) * ((tintRgb >> 8) & 0xFF)) / 255;
    final int blue = ((baseRgb & 0xFF) * (tintRgb & 0xFF)) / 255;
    return OPAQUE | (red << 16) | (green << 8) | blue;
  }

  private int applyBiomeColor(
      final int baseRgb,
      final @NotNull Biome biome,
      final @NotNull BiomeTint tint,
      final int x,
      final int z) {
    final @NotNull BiomeColors colors = this.biomeColors.computeIfAbsent(biome, BiomeColors::of);
    return switch (tint) {
      case WATER -> OPAQUE | (colors.water() & RGB_MASK);
      case GRASS ->
          tintBaseColor(
              baseRgb,
              colors.swampGrass()
                  ? ((CraftBiome) biome).getHandle().getGrassColor(x, z)
                  : colors.grass());
      case FOLIAGE -> tintBaseColor(baseRgb, colors.foliage());
      case DRY_FOLIAGE -> tintBaseColor(baseRgb, colors.dryFoliage());
      case BIRCH_FOLIAGE -> tintBaseColor(baseRgb, FoliageColor.FOLIAGE_BIRCH);
      case EVERGREEN_FOLIAGE -> tintBaseColor(baseRgb, FoliageColor.FOLIAGE_EVERGREEN);
      case LILY_PAD -> tintBaseColor(baseRgb, LILY_PAD_TINT);
    };
  }

  @Override
  public int applyBiomeTint(
      final int baseRgb,
      final @Nullable BlockType blockType,
      final @Nullable Biome biome,
      final int x,
      final int z) {
    if (blockType == null || biome == null) {
      return baseRgb;
    }
    final @Nullable BiomeTint tint = this.tints.get(blockType);
    return tint == null ? baseRgb : applyBiomeColor(baseRgb, biome, tint, x, z);
  }

  @Override
  public @NotNull Set<BlockType> tintedBlocks() {
    return Set.copyOf(this.tints.keySet());
  }
}

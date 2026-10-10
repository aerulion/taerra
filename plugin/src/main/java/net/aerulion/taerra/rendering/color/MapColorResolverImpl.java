package net.aerulion.taerra.rendering.color;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import net.aerulion.taerra.rendering.block.BlockTypes;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapColorResolverImpl implements MapColorResolver {

  private final @NotNull BlockDataStore blockData;
  private final @NotNull BiomeTintMapper biomeTints;

  private static final int MIN_GLOWING_LIGHT = 3;

  private final BlockColors @NotNull [] colors = new BlockColors[BlockTypes.count()];

  public MapColorResolverImpl(
      final @NotNull Logger logger,
      final @NotNull BlockDataStore blockData,
      final @NotNull BiomeTintMapper biomeTints) {
    super();
    this.blockData = blockData;
    this.biomeTints = biomeTints;
    for (int id = 0; id < this.colors.length; id++) {
      final @NotNull BlockType blockType = BlockTypes.byId(id);
      final @NotNull BlockRenderData data = blockData.dataFor(blockType);
      final @Nullable BiomeTint tint = biomeTints.tintOf(blockType);
      this.colors[id] =
          new ResolvedColors(
              biomeTints,
              data.topRgb(),
              data.sideRgb(),
              tint,
              data.tintSide() ? tint : null,
              BlockTypes.handle(blockType).defaultBlockState().getLightEmission()
                  >= MIN_GLOWING_LIGHT);
    }
    reconcileTints(logger);
  }

  private void reconcileTints(final @NotNull Logger logger) {
    final @NotNull Set<BlockType> tinted = this.biomeTints.tintedBlocks();
    final @NotNull List<String> tintedWithoutRule = new ArrayList<>();
    final @NotNull List<String> ruleWithoutTint = new ArrayList<>();

    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @NotNull BlockRenderData data = this.blockData.dataFor(blockType);
      final boolean hasRule = tinted.contains(blockType);
      if (data.tintTop() && !hasRule) {
        tintedWithoutRule.add(blockType.key().asString());
      } else if (hasRule && !data.tintTop()) {
        ruleWithoutTint.add(blockType.key().asString());
      }
    }

    if (!ruleWithoutTint.isEmpty()) {
      ruleWithoutTint.sort(Comparator.naturalOrder());
      logger.fine(
          "Biome tint set for blocks the models do not tint: "
              + String.join(", ", ruleWithoutTint));
    }

    if (!tintedWithoutRule.isEmpty()) {
      tintedWithoutRule.sort(Comparator.naturalOrder());
      logger.fine(
          "Blocks tinted in the models but not in game, rendered untinted: "
              + String.join(", ", tintedWithoutRule));
    }
  }

  @Override
  public @NotNull BlockColors colorsOf(final @NotNull BlockType blockType) {
    return this.colors[BlockTypes.id(blockType)];
  }

  @Override
  public @NotNull BlockColors colorsOf(final int blockId) {
    return this.colors[blockId];
  }

  @Override
  public boolean tintVaries(final @NotNull Biome biome) {
    return this.biomeTints.tintVaries(biome);
  }

  @Override
  public @Nullable TintColors tintAt(
      final @Nullable Biome biome, final int blockX, final int blockZ) {
    return biome == null ? null : this.biomeTints.tintAt(biome, blockX, blockZ);
  }

  private record ResolvedColors(
      @NotNull BiomeTintMapper tints,
      int topRgb,
      int sideRgb,
      @Nullable BiomeTint topTint,
      @Nullable BiomeTint sideTint,
      boolean emitsLight)
      implements BlockColors {

    @Override
    public int topColor(final @Nullable TintColors tint) {
      return this.tints.applyBiomeTint(this.topRgb, this.topTint, tint);
    }

    @Override
    public int sideColor(final @Nullable TintColors tint) {
      return this.tints.applyBiomeTint(this.sideRgb, this.sideTint, tint);
    }
  }
}

package net.aerulion.taerra.rendering.color;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MapColorResolverImpl implements MapColorResolver {

  private final @NotNull BlockDataStore blockData;
  private final @NotNull BiomeTintMapper biomeTints;

  private final BlockType @NotNull [] blockTypesByMaterial;

  public MapColorResolverImpl(
      final @NotNull Logger logger,
      final @NotNull BlockDataStore blockData,
      final @NotNull BiomeTintMapper biomeTints) {
    super();
    this.blockData = blockData;
    this.biomeTints = biomeTints;
    this.blockTypesByMaterial = new BlockType[Material.values().length];
    RegistryAccess.registryAccess()
        .getRegistry(RegistryKey.BLOCK)
        .forEach(
            blockType -> this.blockTypesByMaterial[blockType.asMaterial().ordinal()] = blockType);
    reconcileTints(logger);
  }

  private void reconcileTints(final @NotNull Logger logger) {
    final @NotNull Set<BlockType> tinted = this.biomeTints.tintedBlocks();
    final @NotNull List<String> tintedWithoutRule = new ArrayList<>();
    final @NotNull List<String> ruleWithoutTint = new ArrayList<>();

    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @NotNull BlockRenderData data = this.blockData.dataFor(blockType.asMaterial());
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

  private @Nullable BlockType blockType(final @NotNull Material material) {
    return this.blockTypesByMaterial[material.ordinal()];
  }

  @Override
  public int topColor(
      final @NotNull Material material,
      final @Nullable Biome biome,
      final int blockX,
      final int blockZ) {
    return this.biomeTints.applyBiomeTint(
        this.blockData.dataFor(material).topRgb(), blockType(material), biome, blockX, blockZ);
  }

  @Override
  public int sideColor(
      final @NotNull Material material,
      final @Nullable Biome biome,
      final int blockX,
      final int blockZ) {
    final @NotNull BlockRenderData data = this.blockData.dataFor(material);
    if (!data.tintSide()) {
      return data.sideRgb();
    }
    return this.biomeTints.applyBiomeTint(
        data.sideRgb(), blockType(material), biome, blockX, blockZ);
  }
}

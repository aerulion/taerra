package net.aerulion.taerra.rendering.block;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.craftbukkit.block.CraftBlockType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class Fluids {

  private static final @NotNull Set<Material> SUBMERGED_PLANTS =
      EnumSet.of(Material.KELP, Material.KELP_PLANT, Material.SEAGRASS, Material.TALL_SEAGRASS);

  private static final boolean @NotNull [] SUBMERGED_BY_ORDINAL =
      new boolean[Material.values().length];

  static {
    for (final @NotNull Material material : SUBMERGED_PLANTS) {
      SUBMERGED_BY_ORDINAL[material.ordinal()] = true;
    }
  }

  private Fluids() {
    super();
  }

  public static @NotNull Material substituteSubmerged(final @NotNull Material material) {
    return SUBMERGED_BY_ORDINAL[material.ordinal()] ? Material.WATER : material;
  }

  public static void reportUnhandled(final @NotNull Plugin plugin) {
    final @NotNull List<String> candidates = new ArrayList<>();
    for (final @NotNull BlockType blockType :
        RegistryAccess.registryAccess().getRegistry(RegistryKey.BLOCK)) {
      final @NotNull Material material = blockType.asMaterial();
      if (isFluid(material) || SUBMERGED_PLANTS.contains(material)) {
        continue;
      }
      if (!CraftBlockType.bukkitToMinecraft(material)
          .defaultBlockState()
          .getFluidState()
          .isEmpty()) {
        candidates.add(blockType.key().asString());
      }
    }
    if (candidates.isEmpty()) {
      return;
    }
    candidates.sort(Comparator.naturalOrder());
    plugin
        .getLogger()
        .fine(
            "Waterlogged blocks not rendered as water: "
                + String.join(", ", candidates)
                + " (add any that can reach the surface to Fluids)");
  }

  public static boolean isClearWater(final @NotNull Material material) {
    return material == Material.WATER || material == Material.BUBBLE_COLUMN;
  }

  public static boolean isFluid(final @NotNull Material material) {
    return material == Material.WATER
        || material == Material.LAVA
        || material == Material.BUBBLE_COLUMN;
  }
}

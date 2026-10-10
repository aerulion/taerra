package net.aerulion.taerra.rendering.block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.block.BlockType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class Fluids {

  private static final int WATER = BlockTypes.id(BlockType.WATER);
  private static final boolean @NotNull [] SUBMERGED = new boolean[BlockTypes.count()];
  private static final boolean @NotNull [] FLUID = new boolean[BlockTypes.count()];
  private static final boolean @NotNull [] CLEAR_WATER = new boolean[BlockTypes.count()];

  static {
    for (final @NotNull BlockType blockType :
        List.of(
            BlockType.KELP, BlockType.KELP_PLANT, BlockType.SEAGRASS, BlockType.TALL_SEAGRASS)) {
      SUBMERGED[BlockTypes.id(blockType)] = true;
    }
    for (final @NotNull BlockType blockType :
        List.of(BlockType.WATER, BlockType.LAVA, BlockType.BUBBLE_COLUMN)) {
      FLUID[BlockTypes.id(blockType)] = true;
    }
    CLEAR_WATER[WATER] = true;
    CLEAR_WATER[BlockTypes.id(BlockType.BUBBLE_COLUMN)] = true;
  }

  private Fluids() {
    super();
  }

  public static int substituteSubmerged(final int blockId) {
    return SUBMERGED[blockId] ? WATER : blockId;
  }

  public static void reportUnhandled(final @NotNull Plugin plugin) {
    final @NotNull List<String> candidates = new ArrayList<>();
    for (int id = 0; id < BlockTypes.count(); id++) {
      final @NotNull BlockType blockType = BlockTypes.byId(id);
      if (FLUID[id] || SUBMERGED[id]) {
        continue;
      }
      if (!BlockTypes.handle(blockType).defaultBlockState().getFluidState().isEmpty()) {
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

  public static boolean isClearWater(final int blockId) {
    return CLEAR_WATER[blockId];
  }

  public static boolean isFluid(final int blockId) {
    return FLUID[blockId];
  }
}

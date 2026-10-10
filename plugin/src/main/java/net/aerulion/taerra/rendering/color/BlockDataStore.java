package net.aerulion.taerra.rendering.color;

import net.aerulion.taerra.rendering.block.BlockShapeResolver;
import org.bukkit.block.BlockType;
import org.jetbrains.annotations.NotNull;

public sealed interface BlockDataStore extends BlockShapeResolver permits BlockDataStoreImpl {

  @NotNull
  BlockRenderData dataFor(@NotNull BlockType blockType);
}

package net.aerulion.taerra.rendering.iso;

import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import org.jetbrains.annotations.NotNull;

public sealed interface IsoTileRenderer permits IsoTileRendererImpl {

  @NotNull
  RenderedImage renderTile(
      @NotNull MapRotation rotation,
      int tileX,
      int tileY,
      int minWorldY,
      int maxWorldY,
      @NotNull ChunkColumnsSource source,
      boolean withPick);
}

package net.aerulion.taerra.rendering.topdown;

import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import org.jetbrains.annotations.NotNull;

public sealed interface TopDownTileRenderer permits TopDownTileRendererImpl {

  @NotNull
  RenderedImage render(
      @NotNull ChunkColumnsSource source, int startBlockX, int startBlockZ, int tileSize);

  @NotNull
  RenderedImage render(
      @NotNull ChunkColumnsSource source,
      int startBlockX,
      int startBlockZ,
      int tileSize,
      boolean overlay);

  @NotNull
  List<RenderedTile> renderBatch(
      @NotNull ChunkColumnsSource source, @NotNull List<TileCoordinate> tileCoordinates);
}

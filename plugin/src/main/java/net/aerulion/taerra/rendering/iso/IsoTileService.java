package net.aerulion.taerra.rendering.iso;

import java.util.List;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface IsoTileService permits IsoTileServiceImpl {

  @NotNull
  List<TileCoordinate> invalidateChunk(
      @NotNull World world, int chunkX, int chunkZ, @Nullable Integer blockY);

  @NotNull
  List<TileCoordinate> tilesCovering(
      @NotNull World world,
      @NotNull List<TileCoordinate> topDownTiles,
      @NotNull ChunkColumnsSource source);

  @NotNull
  List<RenderedTile> renderBatch(
      @NotNull World world, @NotNull List<TileCoordinate> tileCoordinates) throws Exception;

  @NotNull
  IsoRenderStats stats();
}

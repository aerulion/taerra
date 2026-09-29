package net.aerulion.taerra.rendering.iso;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.rendering.sample.CachedChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.source.RegionFileChunkSourceProvider;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IsoTileServiceImpl implements IsoTileService {

  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull IsoTileRenderer renderer;
  private final int shadowDistance = MapStyle.SHADOW_DISTANCE;

  private final @NotNull LongAdder renderedBaseTiles = new LongAdder();
  private final @NotNull LongAdder renderNanos = new LongAdder();
  private final @NotNull LongAdder sampleNanos = new LongAdder();
  private final @NotNull LongAdder sampledChunks = new LongAdder();
  private final @NotNull LongAdder reusedChunks = new LongAdder();
  private final @NotNull LongAdder invalidatedTiles = new LongAdder();

  public IsoTileServiceImpl(
      final @NotNull ChunkColumnsCache columns, final @NotNull IsoTileRenderer renderer) {
    super();
    this.columns = columns;
    this.renderer = renderer;
  }

  @Override
  public @NotNull List<TileCoordinate> invalidateChunk(
      final @NotNull World world,
      final int chunkX,
      final int chunkZ,
      final @Nullable Integer blockY) {
    int lowestY = world.getMinHeight();
    int maxTopY = world.getMaxHeight() - 1;
    final long known = this.columns.knownHeights(WorldKeys.id(world), chunkX, chunkZ);
    if (blockY != null
        && known != ChunkColumns.UNKNOWN_HEIGHTS
        && known != ChunkColumns.MISSING_HEIGHTS
        && ChunkColumns.minTopOf(known) != ChunkColumns.EMPTY_COLUMN) {
      lowestY =
          Math.max(
              world.getMinHeight(),
              Math.min(lowestTopAround(world, chunkX, chunkZ, ChunkColumns.minTopOf(known)), blockY)
                  - this.shadowDistance
                  - 1);
      maxTopY = Math.max(ChunkColumns.maxTopOf(known), blockY);
    }

    final @NotNull List<TileCoordinate> affected = new ArrayList<>();

    for (final @NotNull MapRotation rotation : MapRotation.values()) {

      final int shadowX = rotation.worldDeltaX(-MapStyle.SUN_STEP_X, -MapStyle.SUN_STEP_Z);
      final int shadowZ = rotation.worldDeltaZ(-MapStyle.SUN_STEP_X, -MapStyle.SUN_STEP_Z);
      final @NotNull ScreenBounds bounds =
          IsoTileGeometry.blockAreaScreenBounds(
              (long) chunkX * IsoTileGeometry.CHUNK_SIZE - (shadowX < 0 ? this.shadowDistance : 0),
              (long) chunkZ * IsoTileGeometry.CHUNK_SIZE - (shadowZ < 0 ? this.shadowDistance : 0),
              IsoTileGeometry.CHUNK_SIZE + this.shadowDistance,
              lowestY,
              maxTopY,
              rotation);
      final int lastTileX = IsoTileGeometry.screenToTileX(bounds.maxX(), 0);
      final int lastTileY = IsoTileGeometry.screenToTileY(bounds.maxY(), 0);
      for (int tileX = IsoTileGeometry.screenToTileX(bounds.minX(), 0);
          tileX <= lastTileX;
          tileX++) {
        for (int tileY = IsoTileGeometry.screenToTileY(bounds.minY(), 0);
            tileY <= lastTileY;
            tileY++) {
          affected.add(TileCoordinate.isometric(WorldKeys.id(world), rotation, 0, tileX, tileY));
        }
      }
    }
    this.invalidatedTiles.add(affected.size());
    return affected;
  }

  @Override
  public @NotNull List<TileCoordinate> tilesCovering(
      final @NotNull World world,
      final @NotNull List<TileCoordinate> topDownTiles,
      final @NotNull ChunkColumnsSource source) {
    final int chunksPerTile = TileGeometry.TILE_SIZE / IsoTileGeometry.CHUNK_SIZE;
    final @NotNull Set<TileCoordinate> tiles = new LinkedHashSet<>();
    for (final @NotNull TileCoordinate tile : topDownTiles) {
      for (int offsetX = 0; offsetX < chunksPerTile; offsetX++) {
        for (int offsetZ = 0; offsetZ < chunksPerTile; offsetZ++) {
          final int chunkX = tile.x() * chunksPerTile + offsetX;
          final int chunkZ = tile.z() * chunksPerTile + offsetZ;
          final @Nullable ChunkColumns columns = source.columnsFor(chunkX, chunkZ);
          if (columns == null || columns.isEmpty()) {
            continue;
          }

          for (final @NotNull MapRotation rotation : MapRotation.values()) {
            IsoTileGeometry.forEachTileCovering(
                (long) chunkX * IsoTileGeometry.CHUNK_SIZE,
                (long) chunkZ * IsoTileGeometry.CHUNK_SIZE,
                IsoTileGeometry.CHUNK_SIZE,
                world.getMinHeight(),
                columns.maxTopY() + 1,
                rotation,
                (tileX, tileY) ->
                    tiles.add(
                        TileCoordinate.isometric(WorldKeys.id(world), rotation, 0, tileX, tileY)));
          }
        }
      }
    }
    return new ArrayList<>(tiles);
  }

  @Override
  public @NotNull List<RenderedTile> renderBatch(
      final @NotNull World world, final @NotNull List<TileCoordinate> tileCoordinates)
      throws Exception {
    final @NotNull List<RenderedTile> rendered = new ArrayList<>(tileCoordinates.size());
    try (final @NotNull RegionFileChunkSourceProvider provider =
            new RegionFileChunkSourceProvider(world)) {
      final @NotNull CachedChunkColumnsSource source = this.columns.sourceFor(world, provider);
      for (final @NotNull TileCoordinate coordinate : tileCoordinates) {
        final long startedAt = System.nanoTime();
        final @NotNull RenderedImage result =
            this.renderer.renderTile(
                coordinate.rotation(),
                coordinate.x(),
                coordinate.z(),
                world.getMinHeight(),
                world.getMaxHeight(),
                source,
                true);
        this.renderNanos.add(System.nanoTime() - startedAt);
        this.renderedBaseTiles.increment();

        if (ImageUtils.hasVisiblePixels(result.image())) {
          rendered.add(new RenderedTile(coordinate, result));
        }
      }
      this.sampleNanos.add(source.sampleNanos());
      this.sampledChunks.add(source.sampledChunks());
      this.reusedChunks.add(source.reusedChunks());
    }
    return rendered;
  }

  @Override
  public @NotNull IsoRenderStats stats() {
    return new IsoRenderStats(
        this.renderedBaseTiles.sum(),
        this.renderNanos.sum(),
        this.sampleNanos.sum(),
        this.sampledChunks.sum(),
        this.reusedChunks.sum(),
        this.invalidatedTiles.sum(),
        this.columns.size());
  }

  private int lowestTopAround(
      final @NotNull World world, final int chunkX, final int chunkZ, final int fallback) {
    int lowest = fallback;
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (offsetX == 0 && offsetZ == 0) {
          continue;
        }
        final long neighbor =
            this.columns.knownHeights(WorldKeys.id(world), chunkX + offsetX, chunkZ + offsetZ);
        if (neighbor == ChunkColumns.UNKNOWN_HEIGHTS) {
          return world.getMinHeight();
        }
        if (neighbor != ChunkColumns.MISSING_HEIGHTS
            && ChunkColumns.minTopOf(neighbor) != ChunkColumns.EMPTY_COLUMN) {
          lowest = Math.min(lowest, ChunkColumns.minTopOf(neighbor));
        }
      }
    }
    return lowest;
  }
}

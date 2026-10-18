package net.aerulion.taerra.rendering.iso;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.rendering.pipeline.FogLayers;
import net.aerulion.taerra.rendering.sample.CachedChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.ChunkColumns;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.source.RegionFileChunkSourceProvider;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.rendering.style.MapStyle;
import net.aerulion.taerra.rendering.style.SunShadow;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.RenderedImage;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IsoTileServiceImpl implements IsoTileService {

  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull IsoTileRenderer renderer;
  private final @NotNull MapRegions regions;
  private final int shadowDistance = MapStyle.ISO_SHADOW_DISTANCE;

  private final @NotNull LongAdder renderedBaseTiles = new LongAdder();
  private final @NotNull LongAdder renderNanos = new LongAdder();
  private final @NotNull LongAdder sampleNanos = new LongAdder();
  private final @NotNull LongAdder sampledChunks = new LongAdder();
  private final @NotNull LongAdder reusedChunks = new LongAdder();
  private final @NotNull LongAdder invalidatedTiles = new LongAdder();

  public IsoTileServiceImpl(
      final @NotNull ChunkColumnsCache columns,
      final @NotNull IsoTileRenderer renderer,
      final @NotNull MapRegions regions) {
    super();
    this.columns = columns;
    this.renderer = renderer;
    this.regions = regions;
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
                  - Math.max(SunShadow.isoDrop(this.shadowDistance), IsoTileRendererImpl.SKY_REACH)
                  - 1);
      maxTopY = Math.max(ChunkColumns.maxTopOf(known), blockY);
    }

    final @NotNull List<TileCoordinate> affected = new ArrayList<>();

    for (final @NotNull MapRotation rotation : MapRotation.values()) {

      final int shadowX = rotation.worldDeltaX(-MapStyle.SUN_STEP_X, -MapStyle.SUN_STEP_Z);
      final int shadowZ = rotation.worldDeltaZ(-MapStyle.SUN_STEP_X, -MapStyle.SUN_STEP_Z);
      final int backX = rotation.worldDeltaX(-1, -1);
      final int backZ = rotation.worldDeltaZ(-1, -1);
      final int padNegativeX = padding(backX, shadowX, -1);
      final int padNegativeZ = padding(backZ, shadowZ, -1);
      final int size =
          IsoTileGeometry.CHUNK_SIZE
              + Math.max(
                  padNegativeX + padding(backX, shadowX, 1),
                  padNegativeZ + padding(backZ, shadowZ, 1));
      final @NotNull ScreenBounds bounds =
          IsoTileGeometry.blockAreaScreenBounds(
              (long) chunkX * IsoTileGeometry.CHUNK_SIZE - padNegativeX,
              (long) chunkZ * IsoTileGeometry.CHUNK_SIZE - padNegativeZ,
              size,
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
          final long known = this.columns.knownHeights(WorldKeys.id(world), chunkX, chunkZ);
          final int maxTopY =
              known == ChunkColumns.UNKNOWN_HEIGHTS || known == ChunkColumns.MISSING_HEIGHTS
                  ? columns.maxTopY()
                  : Math.max(columns.maxTopY(), ChunkColumns.maxTopOf(known));

          for (final @NotNull MapRotation rotation : MapRotation.values()) {
            IsoTileGeometry.forEachTileCovering(
                (long) chunkX * IsoTileGeometry.CHUNK_SIZE,
                (long) chunkZ * IsoTileGeometry.CHUNK_SIZE,
                IsoTileGeometry.CHUNK_SIZE,
                world.getMinHeight(),
                maxTopY + 1,
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
      final @NotNull Map<FogView, CachedChunkColumnsSource> sources = new HashMap<>();
      final @NotNull CachedChunkColumnsSource source =
          sources.computeIfAbsent(
              FogView.BASE, view -> this.columns.sourceFor(world, provider, view));
      final boolean fogged = this.regions.anyFogged(WorldKeys.id(world));
      for (final @NotNull TileCoordinate coordinate : tileCoordinates) {
        final long startedAt = System.nanoTime();
        final @NotNull RenderedImage result = render(world, coordinate, source, false);
        this.renderNanos.add(System.nanoTime() - startedAt);
        this.renderedBaseTiles.increment();

        if (!ImageUtils.hasVisiblePixels(result.image())) {
          continue;
        }
        rendered.add(new RenderedTile(coordinate, result));
        if (!fogged) {
          continue;
        }
        final @NotNull Set<MapRegion> bodies = new LinkedHashSet<>();
        final @NotNull Set<MapRegion> spills = new LinkedHashSet<>();
        regionsIn(world, coordinate, bodies, spills);
        for (final @NotNull MapRegion region : bodies) {
          final @NotNull FogView body = FogView.body(region.id());
          final @NotNull RenderedImage layer =
              render(
                  world,
                  coordinate,
                  sources.computeIfAbsent(
                      body, view -> this.columns.sourceFor(world, provider, view)),
                  false);
          final @NotNull BufferedImage changed = FogLayers.changed(layer.image(), result.image());
          if (ImageUtils.hasVisiblePixels(changed)) {
            final @Nullable PickBuffer pick = layer.pick();
            rendered.add(
                new RenderedTile(
                    coordinate, body, changed, pick == null ? null : pick.masked(changed)));
          }
        }
        for (final @NotNull MapRegion region : spills) {
          final @NotNull FogView spill = FogView.spill(region.id());
          final @NotNull RenderedImage layer =
              render(
                  world,
                  coordinate,
                  sources.computeIfAbsent(
                      spill, view -> this.columns.sourceFor(world, provider, view)),
                  true);
          if (ImageUtils.hasVisiblePixels(layer.image())) {
            rendered.add(new RenderedTile(coordinate, spill, layer.image(), null));
          }
        }
      }
      for (final @NotNull CachedChunkColumnsSource used : sources.values()) {
        this.sampleNanos.add(used.sampleNanos());
        this.sampledChunks.add(used.sampledChunks());
        this.reusedChunks.add(used.reusedChunks());
      }
    }
    return rendered;
  }

  private @NotNull RenderedImage render(
      final @NotNull World world,
      final @NotNull TileCoordinate coordinate,
      final @NotNull ChunkColumnsSource source,
      final boolean overlay) {
    return this.renderer.renderTile(
        coordinate.rotation(),
        coordinate.x(),
        coordinate.z(),
        world.getMinHeight(),
        world.getMaxHeight(),
        source,
        true,
        overlay);
  }

  private void regionsIn(
      final @NotNull World world,
      final @NotNull TileCoordinate coordinate,
      final @NotNull Set<MapRegion> bodies,
      final @NotNull Set<MapRegion> spills) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull ScreenBounds tileBounds =
        IsoTileGeometry.tileBounds(coordinate.x(), coordinate.z(), 0);
    final @NotNull List<MapRegion> fogged =
        this.regions.in(worldName).stream().filter(region -> region.fog().fogged()).toList();
    IsoTileGeometry.forEachCandidateChunk(
        tileBounds,
        world.getMinHeight(),
        world.getMaxHeight(),
        coordinate.rotation(),
        (chunkX, chunkZ) -> {
          final long known = this.columns.knownHeights(worldName, chunkX, chunkZ);
          if (known == ChunkColumns.MISSING_HEIGHTS
              || (known != ChunkColumns.UNKNOWN_HEIGHTS
                  && ChunkColumns.maxTopOf(known) == ChunkColumns.EMPTY_COLUMN)) {
            return;
          }
          final int maxTopY =
              known == ChunkColumns.UNKNOWN_HEIGHTS
                  ? world.getMaxHeight() - 1
                  : ChunkColumns.maxTopOf(known);
          if (IsoTileGeometry.chunkScreenBounds(
                  chunkX, chunkZ, world.getMinHeight(), maxTopY, coordinate.rotation())
              .intersects(tileBounds)) {
            final @Nullable MapRegion owner = this.regions.foggedAt(worldName, chunkX, chunkZ);
            if (owner != null) {
              bodies.add(owner);
            }
          }
          if (IsoTileGeometry.chunkScreenBounds(
                  chunkX,
                  chunkZ,
                  world.getMinHeight(),
                  Math.max(maxTopY, Fog.SKY_Y + Fog.SKY_THICKNESS),
                  coordinate.rotation())
              .intersects(tileBounds)) {
            for (final @NotNull MapRegion region : fogged) {
              if (region.reaches(chunkX, chunkZ, chunkX, chunkZ, Fog.REACH_CHUNKS)) {
                spills.add(region);
              }
            }
          }
        });
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

  private int padding(final int back, final int shadow, final int side) {
    return Math.max(
        IsoTileRendererImpl.SKY_REACH,
        Math.max(
            back * side > 0 ? 2 * IsoTileRendererImpl.SKY_REACH + 1 : 0,
            shadow * side > 0 ? this.shadowDistance : 0));
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

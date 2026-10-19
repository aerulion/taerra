package net.aerulion.taerra.rendering.iso;

import it.unimi.dsi.fastutil.longs.LongCollection;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongPredicate;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegion;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.region.RegionFog;
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
      final @NotNull ChunkColumnsSource source,
      final @Nullable LongPredicate chunks) {
    final int chunksPerTile = TileGeometry.TILE_SIZE / IsoTileGeometry.CHUNK_SIZE;
    final @NotNull Set<TileCoordinate> tiles = new LinkedHashSet<>();
    for (final @NotNull TileCoordinate tile : topDownTiles) {
      for (int offsetX = 0; offsetX < chunksPerTile; offsetX++) {
        for (int offsetZ = 0; offsetZ < chunksPerTile; offsetZ++) {
          final int chunkX = tile.x() * chunksPerTile + offsetX;
          final int chunkZ = tile.z() * chunksPerTile + offsetZ;
          if (chunks != null && !chunks.test(ChunkCoordinate.key(chunkX, chunkZ))) {
            continue;
          }
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
                chunks == null ? world.getMinHeight() : lowestVisibleY(world, chunkX, chunkZ),
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
        final @NotNull Influence influence = fogged ? influence(world, coordinate) : Influence.NONE;
        final @NotNull List<FogView> layers = new ArrayList<>();
        for (final @NotNull UUID region : influence.spills()) {
          layers.add(FogView.spill(region));
        }
        if (influence.veiled() && influence.bodies().size() > 1) {
          for (final @NotNull UUID region : influence.bodies()) {
            layers.add(FogView.body(region));
          }
        }
        final @NotNull List<IsoTileRenderer.MistView> views = new ArrayList<>(layers.size());
        for (final @NotNull FogView layer : layers) {
          views.add(
              new IsoTileRenderer.MistView(
                  sources.computeIfAbsent(
                      layer, view -> this.columns.sourceFor(world, provider, view)),
                  layer.kind() == FogView.Kind.SPILL));
        }
        final long startedAt = System.nanoTime();
        final @NotNull RenderedImage result = render(world, coordinate, source, views);
        this.renderNanos.add(System.nanoTime() - startedAt);
        this.renderedBaseTiles.increment();

        if (!ImageUtils.hasVisiblePixels(result.image())) {
          continue;
        }
        rendered.add(new RenderedTile(coordinate, result));
        boolean bodied = false;
        for (int index = 0; index < layers.size(); index++) {
          final @NotNull FogView layer = layers.get(index);
          final @NotNull BufferedImage image = result.views().get(index);
          if (layer.kind() == FogView.Kind.SPILL) {
            if (ImageUtils.hasVisiblePixels(image)) {
              rendered.add(new RenderedTile(coordinate, layer, image, null));
            }
          } else {
            bodied |= addBody(rendered, coordinate, layer, image, result.pick(), result.image());
          }
        }
        if (influence.bodies().isEmpty()) {
          continue;
        }
        if (influence.veiled()) {
          if (influence.bodies().size() == 1 && result.unveiled() != null) {
            bodied |=
                addBody(
                    rendered,
                    coordinate,
                    FogView.body(influence.bodies().iterator().next()),
                    result.unveiled(),
                    result.pick(),
                    result.image());
          }
          if (bodied && result.unveiled() != null) {
            rendered.add(new RenderedTile(coordinate, FogView.CLEAR, result.unveiled(), null));
          }
        } else if (influence.alone()) {
          final @NotNull RenderedImage clear =
              render(world, coordinate, source(sources, world, provider, FogView.CLEAR), List.of());
          if (addBody(
              rendered,
              coordinate,
              FogView.body(influence.bodies().iterator().next()),
              clear.image(),
              clear.pick(),
              result.image())) {
            rendered.add(new RenderedTile(coordinate, FogView.CLEAR, clear.image(), null));
          }
        } else {
          for (final @NotNull UUID region : influence.bodies()) {
            final @NotNull FogView body = FogView.body(region);
            final @NotNull RenderedImage layer =
                render(world, coordinate, source(sources, world, provider, body), List.of());
            addBody(rendered, coordinate, body, layer.image(), layer.pick(), result.image());
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

  private @NotNull CachedChunkColumnsSource source(
      final @NotNull Map<FogView, CachedChunkColumnsSource> sources,
      final @NotNull World world,
      final @NotNull RegionFileChunkSourceProvider provider,
      final @NotNull FogView layer) {
    return sources.computeIfAbsent(layer, view -> this.columns.sourceFor(world, provider, view));
  }

  private static boolean addBody(
      final @NotNull List<RenderedTile> rendered,
      final @NotNull TileCoordinate coordinate,
      final @NotNull FogView body,
      final @NotNull BufferedImage image,
      final @Nullable PickBuffer pick,
      final @NotNull BufferedImage base) {
    final @NotNull BufferedImage changed = FogLayers.changed(image, base);
    if (!ImageUtils.hasVisiblePixels(changed)) {
      return false;
    }
    rendered.add(
        new RenderedTile(coordinate, body, changed, pick == null ? null : pick.masked(changed)));
    return true;
  }

  private @NotNull RenderedImage render(
      final @NotNull World world,
      final @NotNull TileCoordinate coordinate,
      final @NotNull ChunkColumnsSource source,
      final @NotNull List<IsoTileRenderer.MistView> views) {
    return this.renderer.renderTile(
        coordinate.rotation(),
        coordinate.x(),
        coordinate.z(),
        world.getMinHeight(),
        world.getMaxHeight(),
        source,
        true,
        views);
  }

  private @NotNull Influence influence(
      final @NotNull World world, final @NotNull TileCoordinate coordinate) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull ScreenBounds tileBounds =
        IsoTileGeometry.tileBounds(coordinate.x(), coordinate.z(), 0);
    final @NotNull Set<UUID> bodies = new LinkedHashSet<>();
    final @NotNull Set<UUID> spills = new LinkedHashSet<>();
    final @NotNull Set<UUID> close = new LinkedHashSet<>();
    IsoTileGeometry.forEachCandidateChunk(
        tileBounds,
        world.getMinHeight(),
        world.getMaxHeight(),
        coordinate.rotation(),
        (chunkX, chunkZ) -> {
          final @NotNull UUID @NotNull [] near = this.regions.foggedNear(worldName, chunkX, chunkZ);
          if (near.length == 0) {
            return;
          }
          final long known = this.columns.knownHeights(worldName, chunkX, chunkZ, false);
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
              bodies.add(owner.id());
            }
            close.addAll(List.of(this.regions.foggedClose(worldName, chunkX, chunkZ)));
          }
          if (IsoTileGeometry.chunkScreenBounds(
                  chunkX,
                  chunkZ,
                  world.getMinHeight(),
                  Math.max(maxTopY, Fog.SKY_Y + Fog.SKY_THICKNESS),
                  coordinate.rotation())
              .intersects(tileBounds)) {
            spills.addAll(List.of(near));
          }
        });
    boolean veiled = true;
    for (final @NotNull UUID region : close) {
      final @Nullable MapRegion found = this.regions.get(region);
      veiled &= found != null && found.fog() == RegionFog.VEIL;
    }
    return new Influence(bodies, spills, close.size() <= 1, veiled);
  }

  private record Influence(
      @NotNull Set<UUID> bodies, @NotNull Set<UUID> spills, boolean alone, boolean veiled) {

    private static final @NotNull Influence NONE = new Influence(Set.of(), Set.of(), true, true);
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

  @Override
  public @NotNull List<TileCoordinate> tilesShowing(
      final @NotNull World world, final @NotNull LongCollection chunks, final boolean fromBottom) {
    final @NotNull Set<TileCoordinate> tiles = new LinkedHashSet<>();
    for (final long chunk : chunks) {
      final int chunkX = ChunkCoordinate.keyX(chunk);
      final int chunkZ = ChunkCoordinate.keyZ(chunk);
      final long known = this.columns.knownHeights(WorldKeys.id(world), chunkX, chunkZ);
      if (known == ChunkColumns.MISSING_HEIGHTS) {
        continue;
      }
      for (final @NotNull MapRotation rotation : MapRotation.values()) {
        IsoTileGeometry.forEachTileCovering(
            (long) chunkX * IsoTileGeometry.CHUNK_SIZE,
            (long) chunkZ * IsoTileGeometry.CHUNK_SIZE,
            IsoTileGeometry.CHUNK_SIZE,
            fromBottom ? world.getMinHeight() : lowestVisibleY(world, chunkX, chunkZ),
            known == ChunkColumns.UNKNOWN_HEIGHTS
                ? world.getMaxHeight()
                : ChunkColumns.maxTopOf(known) + 1,
            rotation,
            (tileX, tileY) ->
                tiles.add(
                    TileCoordinate.isometric(WorldKeys.id(world), rotation, 0, tileX, tileY)));
      }
    }
    return new ArrayList<>(tiles);
  }

  private int lowestVisibleY(final @NotNull World world, final int chunkX, final int chunkZ) {
    final long known = this.columns.knownHeights(WorldKeys.id(world), chunkX, chunkZ);
    if (known == ChunkColumns.UNKNOWN_HEIGHTS
        || known == ChunkColumns.MISSING_HEIGHTS
        || ChunkColumns.minTopOf(known) == ChunkColumns.EMPTY_COLUMN) {
      return world.getMinHeight();
    }
    for (int offsetX = -1; offsetX <= 1; offsetX++) {
      for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
        if (this.columns.knownHeights(WorldKeys.id(world), chunkX + offsetX, chunkZ + offsetZ)
            == ChunkColumns.MISSING_HEIGHTS) {
          return world.getMinHeight();
        }
      }
    }
    return Math.max(
        world.getMinHeight(),
        lowestTopAround(world, chunkX, chunkZ, ChunkColumns.minTopOf(known))
            - Math.max(SunShadow.isoDrop(this.shadowDistance), IsoTileRendererImpl.SKY_REACH)
            - 1);
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

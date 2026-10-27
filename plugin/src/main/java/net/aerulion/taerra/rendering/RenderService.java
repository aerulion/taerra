package net.aerulion.taerra.rendering;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.LongPredicate;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.pipeline.TileGeneratorService;
import net.aerulion.taerra.rendering.queue.ChangeBuffer;
import net.aerulion.taerra.rendering.queue.RenderPriority;
import net.aerulion.taerra.rendering.queue.RenderQueue;
import net.aerulion.taerra.rendering.region.RegionFileScanner;
import net.aerulion.taerra.rendering.region.RegionScanListener;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.session.ManualRenderSession;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.rendering.style.Fog;
import net.aerulion.taerra.util.Throwables;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderService {

  static final int REGION_SIZE_BLOCKS = 512;

  private final @NotNull Plugin plugin;
  private final @NotNull TileGeneratorService generator;
  private final @NotNull RenderQueue renderQueue;
  private final @NotNull ChangeBuffer changes;
  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull IsoTileService isoTiles;
  private final @NotNull WorldLimits limits;
  private final @NotNull TaerraConfig config;

  public RenderService(
      final @NotNull Plugin plugin,
      final @NotNull TileGeneratorService generator,
      final @NotNull RenderQueue renderQueue,
      final @NotNull ChangeBuffer changes,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull IsoTileService isoTiles,
      final @NotNull WorldLimits limits,
      final @NotNull TaerraConfig config) {
    super();
    this.plugin = plugin;
    this.generator = generator;
    this.renderQueue = renderQueue;
    this.changes = changes;
    this.columns = columns;
    this.isoTiles = isoTiles;
    this.limits = limits;
    this.config = config;
  }

  private static boolean intersects(
      final @Nullable WorldLimit limit, final int x, final int z, final int size) {
    return limit == null
        || limit.intersects(x * size, z * size, x * size + size - 1, z * size + size - 1);
  }

  static @NotNull Set<Long> withFogReach(final @NotNull Set<Long> changed) {
    final @NotNull Set<Long> affected = new HashSet<>(changed);
    for (final long chunk : changed) {
      for (int offsetX = -Fog.REACH_CHUNKS; offsetX <= Fog.REACH_CHUNKS; offsetX++) {
        for (int offsetZ = -Fog.REACH_CHUNKS; offsetZ <= Fog.REACH_CHUNKS; offsetZ++) {
          affected.add(
              ChunkCoordinate.key(
                  ChunkCoordinate.keyX(chunk) + offsetX, ChunkCoordinate.keyZ(chunk) + offsetZ));
        }
      }
    }
    return affected;
  }

  static @NotNull Set<Long> containing(final @NotNull Set<Long> chunks, final int sizeBlocks) {
    final @NotNull Set<Long> cells = new HashSet<>();
    for (final long chunk : chunks) {
      cells.add(
          ChunkCoordinate.key(
              Math.floorDiv(ChunkCoordinate.keyX(chunk) << 4, sizeBlocks),
              Math.floorDiv(ChunkCoordinate.keyZ(chunk) << 4, sizeBlocks)));
    }
    return cells;
  }

  public @NotNull Start full(final @NotNull World world) {
    return start(TileGeneratorService.FULL_RENDER, world, listener -> scanFull(world, listener));
  }

  public @NotNull Start limit(final @NotNull World world, final @Nullable WorldLimit limit) {
    return start(
        TileGeneratorService.LIMIT_RENDER,
        world,
        () -> this.limits.set(WorldKeys.id(world), limit),
        listener -> scanFull(world, listener));
  }

  public @Nullable Start views(final @NotNull World world, final @NotNull MapViews views) {
    if (rendering() != null) {
      return new Refused(Refusal.BUSY);
    }
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull MapViews before = this.config.views(worldName);
    final @NotNull Runnable apply =
        () -> {
          this.config.setViews(this.plugin, worldName, views);
          for (final @NotNull MapView view : before.views()) {
            if (!views.shows(view)) {
              this.generator.dropView(worldName, view);
            }
          }
        };
    if (!this.config.worldEnabled(worldName) || before.views().containsAll(views.views())) {
      apply.run();
      return null;
    }
    return start(
        TileGeneratorService.FULL_RENDER, world, apply, listener -> scanFull(world, listener));
  }

  public @NotNull Start radius(
      final @NotNull World world, final int centerX, final int centerZ, final int radiusBlocks) {
    final int minRegionX = Math.floorDiv(centerX - radiusBlocks, REGION_SIZE_BLOCKS);
    final int maxRegionX = Math.floorDiv(centerX + radiusBlocks, REGION_SIZE_BLOCKS);
    final int minRegionZ = Math.floorDiv(centerZ - radiusBlocks, REGION_SIZE_BLOCKS);
    final int maxRegionZ = Math.floorDiv(centerZ + radiusBlocks, REGION_SIZE_BLOCKS);
    final @NotNull WorldLimit circle = WorldLimit.circle(centerX, centerZ, radiusBlocks);
    final @Nullable WorldLimit limit = this.limits.get(WorldKeys.id(world));
    return start(
        TileGeneratorService.RADIUS_RENDER,
        world,
        listener ->
            RegionFileScanner.scanWorld(
                world,
                region ->
                    region.x() >= minRegionX
                        && region.x() <= maxRegionX
                        && region.z() >= minRegionZ
                        && region.z() <= maxRegionZ
                        && intersects(limit, region.x(), region.z(), REGION_SIZE_BLOCKS),
                tile ->
                    intersects(circle, tile.x(), tile.z(), TileGeometry.TILE_SIZE)
                        && intersects(limit, tile.x(), tile.z(), TileGeometry.TILE_SIZE),
                listener));
  }

  public @NotNull Start area(final @NotNull World world, final @NotNull Set<Long> chunks) {
    return area(TileGeneratorService.AREA_RENDER, world, chunks, () -> {}, true);
  }

  public @NotNull Start regions(
      final @NotNull World world, final @NotNull Set<Long> changed, final @NotNull Runnable apply) {
    return area(TileGeneratorService.REGION_RENDER, world, withFogReach(changed), apply, false);
  }

  private @NotNull Start area(
      final @NotNull String mode,
      final @NotNull World world,
      final @NotNull Set<Long> changed,
      final @NotNull Runnable apply,
      final boolean terrain) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @NotNull Set<Long> affected = new HashSet<>(changed);
    if (terrain) {
      affected.addAll(this.columns.groundDependents(worldName, changed));
    }
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    final @NotNull Set<Long> regions = containing(affected, REGION_SIZE_BLOCKS);
    final @NotNull Set<Long> tiles = containing(affected, TileGeometry.TILE_SIZE);
    final @NotNull LongSet chunks = new LongOpenHashSet(affected);
    final @NotNull List<TileCoordinate> isometric =
        this.config.views(worldName).shows(MapView.ISOMETRIC)
            ? this.isoTiles.tilesShowing(world, chunks, terrain)
            : List.of();
    return start(
        mode,
        world,
        apply,
        terrain ? null : chunks::contains,
        listener -> {
          if (!terrain) {
            this.generator.dropUnusedLayers(worldName);
          }
          this.columns.forget(worldName, affected, terrain);
          this.renderQueue.enqueueAll(isometric, RenderPriority.FULL_RENDER_ZOOM_0);
          return RegionFileScanner.scanWorld(
              world,
              region -> regions.contains(ChunkCoordinate.key(region.x(), region.z())),
              tile ->
                  tiles.contains(ChunkCoordinate.key(tile.x(), tile.z()))
                      && intersects(limit, tile.x(), tile.z(), TileGeometry.TILE_SIZE),
              listener);
        });
  }

  public @Nullable String rendering() {
    final @NotNull ManualRenderSessionSnapshot session =
        this.generator.statusSnapshot().manualRenderSession();
    return session.active() ? session.worldName() : null;
  }

  public @NotNull Stop cancel(final @NotNull String reason) {
    final @NotNull ManualRenderSessionSnapshot session =
        this.generator.statusSnapshot().manualRenderSession();
    final int discarded = this.generator.cancelManualRender(reason);
    final @NotNull Stop stop = new Stop(session, discarded);
    if (!stop.idle()) {
      this.renderQueue.save();
    }
    return stop;
  }

  public @NotNull Stop flush(final @NotNull String reason) {
    final @NotNull ManualRenderSessionSnapshot session =
        this.generator.statusSnapshot().manualRenderSession();
    final int discarded =
        this.generator.cancelManualRender(reason)
            + this.renderQueue.discardQueued(RenderPriority.LIVE_ZOOM_0)
            + this.changes.discard();
    this.renderQueue.save();
    return new Stop(session, discarded);
  }

  private @NotNull RegionScanResult scanFull(
      final @NotNull World world, final @NotNull RegionScanListener listener) {
    final @NotNull String worldName = WorldKeys.id(world);
    this.columns.forgetWorld(worldName);
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    return RegionFileScanner.scanWorld(
        world,
        region -> intersects(limit, region.x(), region.z(), REGION_SIZE_BLOCKS),
        tile -> intersects(limit, tile.x(), tile.z(), TileGeometry.TILE_SIZE),
        listener);
  }

  private @NotNull Start start(
      final @NotNull String mode,
      final @NotNull World world,
      final @NotNull Function<RegionScanListener, RegionScanResult> scan) {
    return start(mode, world, () -> {}, scan);
  }

  private @NotNull Start start(
      final @NotNull String mode,
      final @NotNull World world,
      final @NotNull Runnable began,
      final @NotNull Function<RegionScanListener, RegionScanResult> scan) {
    return start(mode, world, began, null, scan);
  }

  private @NotNull Start start(
      final @NotNull String mode,
      final @NotNull World world,
      final @NotNull Runnable began,
      final @Nullable LongPredicate chunks,
      final @NotNull Function<RegionScanListener, RegionScanResult> scan) {
    final @NotNull String worldName = WorldKeys.id(world);
    if (!this.config.worldEnabled(worldName)) {
      return new Refused(Refusal.WORLD_DISABLED);
    }
    final @Nullable ManualRenderSession session =
        this.generator.tryBeginManualRender(mode, worldName);
    if (session == null) {
      return new Refused(Refusal.BUSY);
    }
    session.limitTo(chunks);
    began.run();

    final @NotNull CompletableFuture<Scan> scanned = new CompletableFuture<>();
    Thread.ofPlatform()
        .name("Taerra-Scan")
        .daemon()
        .start(
            () -> {
              try {
                scanned.complete(submit(mode, session, scan));
              } catch (final Throwable e) {
                final @NotNull String failure = Throwables.describe(e);
                this.generator.cancelManualRender(session, "Render failed: " + failure);
                this.plugin
                    .getLogger()
                    .log(Level.SEVERE, "Failed to render " + worldName + ": " + failure, e);
                scanned.completeExceptionally(e);
              }
            });
    return new Started(scanned);
  }

  private @NotNull Scan submit(
      final @NotNull String mode,
      final @NotNull ManualRenderSession session,
      final @NotNull Function<RegionScanListener, RegionScanResult> scan) {
    final @NotNull RegionScanResult result =
        scan.apply(progress -> this.generator.updateManualRenderScanProgress(session, progress));
    if (result.zoom0Tiles().isEmpty()) {
      this.generator.cancelManualRender(session, "Nothing to render");
      return new Scan(Outcome.NOTHING, 0, result);
    }
    final int queued = this.generator.submitManualRender(session, result, "Queued " + mode);
    if (queued == 0) {
      return new Scan(Outcome.CANCELED, 0, result);
    }
    this.renderQueue.save();
    return new Scan(Outcome.QUEUED, queued, result);
  }

  public enum Refusal {
    WORLD_DISABLED,
    BUSY
  }

  public enum Outcome {
    QUEUED,
    NOTHING,
    CANCELED
  }

  public sealed interface Start permits Refused, Started {}

  public record Refused(@NotNull Refusal reason) implements Start {}

  public record Started(@NotNull CompletableFuture<Scan> scan) implements Start {}

  public record Scan(@NotNull Outcome outcome, int queuedTiles, @NotNull RegionScanResult result) {}

  public record Stop(@NotNull ManualRenderSessionSnapshot session, int discarded) {

    public boolean idle() {
      return !this.session.active() && this.discarded == 0;
    }
  }
}

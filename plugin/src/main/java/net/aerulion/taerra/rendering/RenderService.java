package net.aerulion.taerra.rendering;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.limit.WorldLimits;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.model.WorldLimit;
import net.aerulion.taerra.rendering.iso.IsoTileGeometry;
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
import net.aerulion.taerra.util.Throwables;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderService {

  private static final int REGION_SIZE_BLOCKS = 512;

  private final @NotNull Plugin plugin;
  private final @NotNull TileGeneratorService generator;
  private final @NotNull RenderQueue renderQueue;
  private final @NotNull ChangeBuffer changes;
  private final @NotNull ChunkColumnsCache columns;
  private final @NotNull WorldLimits limits;
  private final @NotNull TaerraConfig config;

  public RenderService(
      final @NotNull Plugin plugin,
      final @NotNull TileGeneratorService generator,
      final @NotNull RenderQueue renderQueue,
      final @NotNull ChangeBuffer changes,
      final @NotNull ChunkColumnsCache columns,
      final @NotNull WorldLimits limits,
      final @NotNull TaerraConfig config) {
    super();
    this.plugin = plugin;
    this.generator = generator;
    this.renderQueue = renderQueue;
    this.changes = changes;
    this.columns = columns;
    this.limits = limits;
    this.config = config;
  }

  private static boolean intersects(
      final @Nullable WorldLimit limit, final int x, final int z, final int size) {
    return limit == null
        || limit.intersects(x * size, z * size, x * size + size - 1, z * size + size - 1);
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

  public @NotNull Start area(
      final @NotNull World world, final @NotNull Set<Long> chunks, final @Nullable Boolean hide) {
    final @NotNull String worldName = WorldKeys.id(world);
    final @Nullable WorldLimit limit = this.limits.get(worldName);
    final @NotNull Set<Long> regions = new HashSet<>();
    final @NotNull Set<Long> tiles = new HashSet<>();
    final @NotNull Set<TileCoordinate> isometric = new LinkedHashSet<>();
    for (final long chunk : chunks) {
      final int blockX = ChunkCoordinate.keyX(chunk) << 4;
      final int blockZ = ChunkCoordinate.keyZ(chunk) << 4;
      regions.add(
          ChunkCoordinate.key(
              Math.floorDiv(blockX, REGION_SIZE_BLOCKS),
              Math.floorDiv(blockZ, REGION_SIZE_BLOCKS)));
      tiles.add(
          ChunkCoordinate.key(
              Math.floorDiv(blockX, TileGeometry.TILE_SIZE),
              Math.floorDiv(blockZ, TileGeometry.TILE_SIZE)));
      for (final @NotNull MapRotation rotation : MapRotation.values()) {
        IsoTileGeometry.forEachTileCovering(
            blockX,
            blockZ,
            16,
            world.getMinHeight(),
            world.getMaxHeight(),
            rotation,
            (tileX, tileY) ->
                isometric.add(TileCoordinate.isometric(worldName, rotation, 0, tileX, tileY)));
      }
    }
    return start(
        hide == null ? TileGeneratorService.AREA_RENDER : TileGeneratorService.HIDE_RENDER,
        world,
        listener -> {
          if (hide != null) {
            this.limits.setHidden(worldName, chunks, hide);
          }
          this.columns.forget(worldName, chunks);
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
    final @NotNull String worldName = WorldKeys.id(world);
    if (!this.config.worldEnabled(worldName)) {
      return new Refused(Refusal.WORLD_DISABLED);
    }
    final @Nullable ManualRenderSession session =
        this.generator.tryBeginManualRender(mode, worldName);
    if (session == null) {
      return new Refused(Refusal.BUSY);
    }
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

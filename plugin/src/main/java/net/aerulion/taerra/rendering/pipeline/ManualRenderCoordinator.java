package net.aerulion.taerra.rendering.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.queue.RenderPriority;
import net.aerulion.taerra.rendering.queue.RenderQueue;
import net.aerulion.taerra.rendering.region.RegionRenderTiming;
import net.aerulion.taerra.rendering.region.RegionScanProgress;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.sample.ChunkColumnsSource;
import net.aerulion.taerra.rendering.session.CompletedRender;
import net.aerulion.taerra.rendering.session.ManualRenderSession;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.rendering.session.RenderHistory;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ManualRenderCoordinator {

  private final @NotNull RenderQueue renderQueue;
  private final @NotNull IsoTileService isoTileService;
  private final @NotNull ToIntFunction<TileCoordinate> depth;
  private final @NotNull RenderHistory history = new RenderHistory();
  private final @NotNull AtomicInteger cancels = new AtomicInteger();
  private volatile @NotNull ManualRenderSession session = ManualRenderSession.idle();
  private volatile boolean scannedEveryRegion;

  ManualRenderCoordinator(
      final @NotNull RenderQueue renderQueue,
      final @NotNull IsoTileService isoTileService,
      final @NotNull ToIntFunction<TileCoordinate> depth) {
    super();
    this.renderQueue = renderQueue;
    this.isoTileService = isoTileService;
    this.depth = depth;
  }

  boolean rendering(final @NotNull String worldName) {
    final @NotNull ManualRenderSession session = this.session;
    return session.isActive() && session.matches(worldName);
  }

  @NotNull
  RenderHistory history() {
    return this.history;
  }

  int cancels() {
    return this.cancels.get();
  }

  boolean scannedEveryRegion() {
    return this.scannedEveryRegion;
  }

  @NotNull
  ManualRenderSessionSnapshot snapshot() {
    return this.session.snapshot();
  }

  synchronized @Nullable ManualRenderSession tryBegin(
      final @NotNull String mode, final @NotNull String worldName) {
    if (this.session.isActive()) {
      return null;
    }
    this.session = ManualRenderSession.scanning(mode, worldName, this.depth);
    return this.session;
  }

  void updateScanProgress(
      final @NotNull ManualRenderSession session, final @NotNull RegionScanProgress progress) {
    if (session == this.session && session.isActive()) {
      session.updateScan(
          progress.totalRegions(),
          progress.scannedRegions(),
          progress.discoveredChunks(),
          progress.discoveredTiles());
    }
  }

  synchronized int submit(
      final @NotNull ManualRenderSession session,
      final @NotNull RegionScanResult result,
      final @NotNull String plannedMessage) {
    final @NotNull List<TileCoordinate> tiles = new ArrayList<>(result.zoom0Tiles());
    if (session != this.session
        || !session.activate(
            result.totalRegions(), result.discoveredChunks(), tiles, plannedMessage)) {
      return 0;
    }
    this.scannedEveryRegion = result.unreadableRegions() == 0;
    this.renderQueue.enqueueAll(tiles, RenderPriority.FULL_RENDER_ZOOM_0);
    session.setLastMessage(
        plannedMessage
            + " | queued="
            + tiles.size()
            + " top-down"
            + ", isometric tiles follow their ground as it is measured");
    return tiles.size();
  }

  synchronized int cancel(
      final @NotNull ManualRenderSession session, final @NotNull String reason) {
    return session == this.session ? cancel(reason) : 0;
  }

  synchronized int cancel(final @NotNull String reason) {
    this.cancels.incrementAndGet();
    final @NotNull ManualRenderSession session = this.session;
    final int discarded = this.renderQueue.discardQueued(RenderPriority.FULL_RENDER_ZOOM_0);
    if (session.isActive()) {
      session.cancel(reason);
      this.history.record(session.toCompletedRender());
    }
    return discarded;
  }

  void queueIsometricCoverage(
      final @NotNull World world,
      final @NotNull List<TileCoordinate> batch,
      final @NotNull List<RenderedTile> renderedTiles,
      final @NotNull ChunkColumnsSource source,
      final @NotNull RenderPriority priority,
      final int cancelsAtStart) {
    final @NotNull ManualRenderSession session = this.session;
    final int measured =
        session.isActive() ? (int) batch.stream().filter(session::owns).count() : 0;
    if (measured > 0) {
      final @NotNull List<TileCoordinate> covered =
          renderedTiles.stream().map(RenderedTile::coordinate).filter(session::owns).toList();
      final @NotNull List<TileCoordinate> fresh =
          session.addIsometricCoverage(
              measured, this.isoTileService.tilesCovering(world, covered, source));
      if (!fresh.isEmpty()) {
        this.renderQueue.enqueueAllAhead(fresh, RenderPriority.FULL_RENDER_ZOOM_0);
      }
    }
    if (priority.isLive() || cancelsAtStart != this.cancels.get()) {
      return;
    }
    final @NotNull List<TileCoordinate> orphans =
        renderedTiles.stream()
            .map(RenderedTile::coordinate)
            .filter(tile -> !session.isActive() || !session.owns(tile))
            .toList();
    if (!orphans.isEmpty()) {
      this.renderQueue.enqueueAll(
          this.isoTileService.tilesCovering(world, orphans, source),
          RenderPriority.FULL_RENDER_ZOOM_0);
    }
  }

  @Nullable
  CompletedRender recordTileCompletion(final @NotNull TileCoordinate coord) {
    final @NotNull ManualRenderSession session = this.session;
    if (session.isActive() && session.matches(coord.world())) {
      session.completeTile(coord);
      if (session.finishIfDrained()) {
        final @NotNull CompletedRender completed = session.toCompletedRender();
        this.history.record(completed);
        return completed;
      }
    }
    return null;
  }

  void recordRegionTiming(final @NotNull RegionRenderTiming timing, final long persistNanos) {
    this.session.recordRegionTiming(timing, persistNanos);
  }

  void markDispatched(final @NotNull List<TileCoordinate> tiles) {
    final @NotNull ManualRenderSession session = this.session;
    if (!session.isActive()) {
      return;
    }
    for (final @NotNull TileCoordinate coordinate : tiles) {
      if (session.matches(coordinate.world())) {
        session.markDispatched(coordinate);
      }
    }
  }

  void markReturned(final @NotNull List<TileCoordinate> tiles) {
    final @NotNull ManualRenderSession session = this.session;
    if (!session.isActive()) {
      return;
    }
    for (final @NotNull TileCoordinate coordinate : tiles) {
      if (session.matches(coordinate.world())) {
        session.markReturned(coordinate);
      }
    }
  }
}

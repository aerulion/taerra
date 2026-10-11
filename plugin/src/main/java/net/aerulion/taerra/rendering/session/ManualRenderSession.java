package net.aerulion.taerra.rendering.session;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ToIntFunction;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.region.RegionRenderTiming;
import net.aerulion.taerra.rendering.tile.TileHierarchy;
import org.jetbrains.annotations.NotNull;

public final class ManualRenderSession {

  private final String mode;
  private final String worldName;
  private final @NotNull ToIntFunction<TileCoordinate> depth;
  private final @NotNull Set<TileCoordinate> queuedTiles;
  private final @NotNull Set<TileCoordinate> inFlightTiles;
  private final @NotNull Set<TileCoordinate> knownTiles = new HashSet<>();
  private final @NotNull AtomicInteger completedTiles;
  private final @NotNull AtomicInteger renderedRegions;
  private final @NotNull LongAdder totalRegionNanos;
  private final @NotNull LongAdder totalChunkReadNanos;
  private final @NotNull LongAdder totalSampleNanos;
  private final @NotNull LongAdder totalRenderTilesNanos;
  private final @NotNull LongAdder totalPersistNanos;
  private final long startedAtNanos;
  private volatile boolean active;
  private volatile String phase;
  private volatile int totalRegions;
  private volatile int scannedRegions;
  private volatile int discoveredChunks;
  private volatile int totalTiles;
  private volatile int topDownTiles;
  private volatile int topDownZoom0Tiles;
  private volatile int measuredTopDownTiles;
  private volatile String lastMessage;
  private volatile long lastRegionNanos;
  private volatile long lastChunkReadNanos;
  private volatile long lastSampleNanos;
  private volatile long lastRenderTilesNanos;
  private volatile long lastPersistNanos;
  private volatile int lastChunksRead;
  private volatile int lastTilesRendered;
  private volatile long finishedAtNanos;

  public ManualRenderSession(
      final String mode,
      final String worldName,
      final @NotNull ToIntFunction<TileCoordinate> depth,
      final boolean active,
      final String phase) {
    super();
    this.mode = mode;
    this.worldName = worldName;
    this.depth = depth;
    this.active = active;
    this.phase = phase;
    this.queuedTiles = ConcurrentHashMap.newKeySet();
    this.inFlightTiles = ConcurrentHashMap.newKeySet();
    this.completedTiles = new AtomicInteger();
    this.renderedRegions = new AtomicInteger();
    this.totalRegionNanos = new LongAdder();
    this.totalChunkReadNanos = new LongAdder();
    this.totalSampleNanos = new LongAdder();
    this.totalRenderTilesNanos = new LongAdder();
    this.totalPersistNanos = new LongAdder();
    this.lastMessage = "";
    this.startedAtNanos = System.nanoTime();
    this.finishedAtNanos = active ? 0L : this.startedAtNanos;
  }

  public static @NotNull ManualRenderSession idle() {
    return new ManualRenderSession("none", "-", tile -> 0, false, "idle");
  }

  public static @NotNull ManualRenderSession scanning(
      final String mode,
      final String worldName,
      final @NotNull ToIntFunction<TileCoordinate> depth) {
    return new ManualRenderSession(mode, worldName, depth, true, "scanning");
  }

  private static double averageMillis(final long nanos, final int count) {
    if (count <= 0) {
      return 0.0;
    }
    return nanosToMillis(nanos) / count;
  }

  private static double nanosToMillis(final long nanos) {
    return nanos / 1_000_000.0;
  }

  private static void resetAdder(final @NotNull LongAdder adder) {
    adder.reset();
  }

  public long elapsedMillis() {
    final long until = this.finishedAtNanos == 0L ? System.nanoTime() : this.finishedAtNanos;
    return Math.max(0L, (until - this.startedAtNanos) / 1_000_000L);
  }

  public long estimatedRemainingMillis() {
    final int completed = this.completedTiles.get();
    final int total = estimatedTotalTiles();
    if (!this.active || completed <= 0 || total <= completed) {
      return -1L;
    }
    return elapsedMillis() * (total - completed) / completed;
  }

  private int estimatedTotalTiles() {
    final int total = this.totalTiles;
    final int topDown = this.topDownTiles;
    final int measured = this.measuredTopDownTiles;
    if (!totalIsEstimated()) {
      return total;
    }
    return Math.max(
        total, topDown + (int) ((long) (total - topDown) * this.topDownZoom0Tiles / measured));
  }

  private boolean totalIsEstimated() {
    return this.measuredTopDownTiles > 0 && this.measuredTopDownTiles < this.topDownZoom0Tiles;
  }

  public long finishedAtEpochMillis() {
    if (this.finishedAtNanos == 0L) {
      return 0L;
    }
    return System.currentTimeMillis() - (System.nanoTime() - this.finishedAtNanos) / 1_000_000L;
  }

  public @NotNull CompletedRender toCompletedRender() {
    return new CompletedRender(
        this.mode,
        this.worldName,
        this.phase,
        this.completedTiles.get(),
        this.scannedRegions,
        this.discoveredChunks,
        elapsedMillis(),
        finishedAtEpochMillis());
  }

  public boolean isActive() {
    return this.active;
  }

  public boolean matches(final String worldName) {
    return this.worldName.equals(worldName);
  }

  public void updateScan(
      final int totalRegions,
      final int scannedRegions,
      final int discoveredChunks,
      final int discoveredTiles) {
    this.phase = "scanning";
    this.totalRegions = totalRegions;
    this.scannedRegions = scannedRegions;
    this.discoveredChunks = discoveredChunks;
    this.totalTiles = discoveredTiles;
    this.lastMessage =
        "Discovered " + discoveredTiles + " zoom-0 tiles from " + discoveredChunks + " chunks";
  }

  public synchronized boolean activate(
      final int totalRegions,
      final int discoveredChunks,
      final @NotNull List<TileCoordinate> tiles,
      final String plannedMessage) {
    if (!this.active) {
      return false;
    }
    this.phase = "rendering";
    this.totalRegions = totalRegions;
    this.scannedRegions = totalRegions;
    this.discoveredChunks = discoveredChunks;
    this.completedTiles.set(0);
    this.renderedRegions.set(0);
    this.lastRegionNanos = 0L;
    this.lastChunkReadNanos = 0L;
    this.lastSampleNanos = 0L;
    this.lastRenderTilesNanos = 0L;
    this.lastPersistNanos = 0L;
    this.lastChunksRead = 0;
    this.lastTilesRendered = 0;
    resetAdder(this.totalRegionNanos);
    resetAdder(this.totalChunkReadNanos);
    resetAdder(this.totalSampleNanos);
    resetAdder(this.totalRenderTilesNanos);
    resetAdder(this.totalPersistNanos);
    this.queuedTiles.clear();
    this.inFlightTiles.clear();
    this.knownTiles.clear();
    this.totalTiles = 0;
    tiles.forEach(this::addWithAncestors);
    this.topDownTiles = this.totalTiles;
    this.topDownZoom0Tiles = tiles.size();
    this.lastMessage = plannedMessage;
    return true;
  }

  public void recordRegionTiming(
      final @NotNull RegionRenderTiming timing, final long persistNanos) {
    this.renderedRegions.incrementAndGet();
    this.totalRegionNanos.add(timing.totalNanos() + persistNanos);
    this.totalChunkReadNanos.add(timing.chunkReadNanos());
    this.totalSampleNanos.add(timing.sampleNanos());
    this.totalRenderTilesNanos.add(timing.renderTilesNanos());
    this.totalPersistNanos.add(persistNanos);
    this.lastRegionNanos = timing.totalNanos() + persistNanos;
    this.lastChunkReadNanos = timing.chunkReadNanos();
    this.lastSampleNanos = timing.sampleNanos();
    this.lastRenderTilesNanos = timing.renderTilesNanos();
    this.lastPersistNanos = persistNanos;
    this.lastChunksRead = timing.chunksRead();
    this.lastTilesRendered = timing.tilesRendered();
  }

  public void markDispatched(final TileCoordinate coord) {
    if (this.queuedTiles.remove(coord)) {
      this.inFlightTiles.add(coord);
    }
  }

  public void markReturned(final TileCoordinate coord) {
    if (this.inFlightTiles.remove(coord)) {
      this.queuedTiles.add(coord);
    }
  }

  public void completeTile(final @NotNull TileCoordinate coord) {
    if (coord.zoom() > 0 && Arrays.stream(TileHierarchy.childrenOf(coord)).anyMatch(this::owns)) {
      return;
    }
    if (this.inFlightTiles.remove(coord) || this.queuedTiles.remove(coord)) {
      this.completedTiles.incrementAndGet();
    }
  }

  public boolean isDrained() {
    return this.completedTiles.get() >= this.totalTiles
        && this.queuedTiles.isEmpty()
        && this.inFlightTiles.isEmpty();
  }

  public synchronized boolean finishIfDrained() {
    if (!this.active || !isDrained()) {
      return false;
    }
    this.phase = "completed";
    this.finishedAtNanos = System.nanoTime();
    this.active = false;
    this.lastMessage = this.mode + " completed";
    this.knownTiles.clear();
    return true;
  }

  public boolean owns(final TileCoordinate coord) {
    return this.inFlightTiles.contains(coord) || this.queuedTiles.contains(coord);
  }

  public synchronized @NotNull List<TileCoordinate> addIsometricCoverage(
      final int measuredTopDown, final @NotNull Collection<TileCoordinate> isometric) {
    if (!this.active) {
      return List.of();
    }
    final @NotNull List<TileCoordinate> fresh =
        isometric.stream().filter(this::addWithAncestors).toList();
    this.measuredTopDownTiles += measuredTopDown;
    return fresh;
  }

  private boolean addWithAncestors(final @NotNull TileCoordinate tile) {
    final boolean fresh = !this.knownTiles.contains(tile);
    for (@NotNull TileCoordinate current = tile;
        current.zoom() <= this.depth.applyAsInt(current) && this.knownTiles.add(current);
        current = TileHierarchy.parentOf(current)) {
      this.queuedTiles.add(current);
      this.totalTiles++;
    }
    return fresh;
  }

  public synchronized void cancel(final String reason) {
    this.active = false;
    this.phase = "canceled";
    this.finishedAtNanos = System.nanoTime();
    this.lastMessage = reason;
    this.queuedTiles.clear();
    this.inFlightTiles.clear();
    this.knownTiles.clear();
  }

  public void setLastMessage(final String lastMessage) {
    this.lastMessage = lastMessage;
  }

  public @NotNull ManualRenderSessionSnapshot snapshot() {
    return new ManualRenderSessionSnapshot(
        this.mode,
        this.active,
        this.worldName,
        this.phase,
        this.totalRegions,
        this.scannedRegions,
        this.discoveredChunks,
        estimatedTotalTiles(),
        totalIsEstimated(),
        this.queuedTiles.size(),
        this.inFlightTiles.size(),
        this.completedTiles.get(),
        this.lastMessage,
        timingSnapshot(),
        elapsedMillis(),
        estimatedRemainingMillis());
  }

  private @NotNull ManualRenderTimingSnapshot timingSnapshot() {
    final int regionCount = this.renderedRegions.get();
    return new ManualRenderTimingSnapshot(
        regionCount,
        averageMillis(this.totalRegionNanos.sum(), regionCount),
        averageMillis(this.totalChunkReadNanos.sum(), regionCount),
        averageMillis(this.totalSampleNanos.sum(), regionCount),
        averageMillis(this.totalRenderTilesNanos.sum(), regionCount),
        averageMillis(this.totalPersistNanos.sum(), regionCount),
        nanosToMillis(this.lastRegionNanos),
        nanosToMillis(this.lastChunkReadNanos),
        nanosToMillis(this.lastSampleNanos),
        nanosToMillis(this.lastRenderTilesNanos),
        nanosToMillis(this.lastPersistNanos),
        this.lastChunksRead,
        this.lastTilesRendered);
  }
}

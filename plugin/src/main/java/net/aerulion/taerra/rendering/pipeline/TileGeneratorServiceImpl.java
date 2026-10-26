package net.aerulion.taerra.rendering.pipeline;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.model.ChunkCoordinate;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.MapViews;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import net.aerulion.taerra.model.WorldKeys;
import net.aerulion.taerra.region.MapRegions;
import net.aerulion.taerra.rendering.iso.IsoTileService;
import net.aerulion.taerra.rendering.queue.RenderPriority;
import net.aerulion.taerra.rendering.queue.RenderQueue;
import net.aerulion.taerra.rendering.queue.RenderQueueTask;
import net.aerulion.taerra.rendering.region.RegionRenderTiming;
import net.aerulion.taerra.rendering.region.RegionScanProgress;
import net.aerulion.taerra.rendering.region.RegionScanResult;
import net.aerulion.taerra.rendering.sample.CachedChunkColumnsSource;
import net.aerulion.taerra.rendering.sample.ChunkColumnsCache;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.session.CompletedRender;
import net.aerulion.taerra.rendering.session.ManualRenderSession;
import net.aerulion.taerra.rendering.session.ManualRenderSessionSnapshot;
import net.aerulion.taerra.rendering.session.RenderHistory;
import net.aerulion.taerra.rendering.source.RegionFileChunkSourceProvider;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.rendering.tile.PyramidDepths;
import net.aerulion.taerra.rendering.tile.RenderedTile;
import net.aerulion.taerra.rendering.tile.TileHierarchy;
import net.aerulion.taerra.rendering.topdown.TopDownTileRenderer;
import net.aerulion.taerra.storage.TileCache;
import net.aerulion.taerra.storage.TileStorage;
import net.aerulion.taerra.util.ImageUtils;
import net.aerulion.taerra.util.Throwables;
import net.aerulion.taerra.web.socket.TileUpdateBroadcaster;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileGeneratorServiceImpl implements TileGeneratorService {

  private static final long DISPATCH_INTERVAL_MS = 20L;
  private static final int LIVE_BATCH_SIZE = 16;
  private static final int REGION_BATCH_SIZE = 64;
  private static final int MIN_BATCH_SIZE = 4;
  private static final long STALE_TILE_MARGIN_MILLIS = 2_000L;
  private static final int RENDER_ATTEMPTS = 3;

  private final @NotNull Plugin plugin;
  private final @NotNull TopDownTileRenderer topDownRenderer;
  private final @NotNull ChunkColumnsCache columnsCache;
  private final @NotNull IsoTileService isoTileService;
  private final @NotNull TileCache cache;
  private final @NotNull TileStorage storage;
  private final @NotNull RenderQueue renderQueue;
  private final @NotNull TileUpdateBroadcaster tileUpdateBroadcaster;
  private final @NotNull MapRegions regions;
  private final @NotNull TaerraConfig config;
  private final @NotNull ThreadPoolExecutor baseExecutorService;
  private final @NotNull ThreadPoolExecutor zoomExecutorService;
  private final @NotNull ScheduledExecutorService scheduler;
  private final @NotNull AtomicBoolean running = new AtomicBoolean(false);
  private final @NotNull AtomicBoolean stopping = new AtomicBoolean(false);
  private final @NotNull AtomicInteger baseInFlight = new AtomicInteger();
  private final @NotNull AtomicInteger zoomInFlight = new AtomicInteger();
  private final @NotNull LongAdder completedZoom0Tiles = new LongAdder();
  private final @NotNull LongAdder completedZoomTiles = new LongAdder();
  private final @NotNull LongAdder totalZoom0Nanos = new LongAdder();
  private final @NotNull LongAdder totalZoomNanos = new LongAdder();
  private final @NotNull LiveRenderRecorder liveRecorder = new LiveRenderRecorder();
  private final @NotNull Map<TileCoordinate, Integer> failures = new ConcurrentHashMap<>();
  private final @NotNull ManualRenderCoordinator manualRenders;
  private final @NotNull PyramidDepths depths;
  private volatile int baseThreads;
  private volatile int zoomThreads;

  private volatile long startTimeMillis;

  public TileGeneratorServiceImpl(
      final @NotNull Plugin plugin,
      final @NotNull TopDownTileRenderer topDownRenderer,
      final @NotNull ChunkColumnsCache columnsCache,
      final @NotNull IsoTileService isoTileService,
      final @NotNull TileCache cache,
      final @NotNull TileStorage storage,
      final @NotNull RenderQueue renderQueue,
      final @NotNull TileUpdateBroadcaster tileUpdateBroadcaster,
      final @NotNull MapRegions regions,
      final @NotNull TaerraConfig config,
      final int threadCount) {
    super();
    this.plugin = plugin;
    this.topDownRenderer = topDownRenderer;
    this.columnsCache = columnsCache;
    this.isoTileService = isoTileService;
    this.cache = cache;
    this.storage = storage;
    this.renderQueue = renderQueue;
    this.tileUpdateBroadcaster = tileUpdateBroadcaster;
    this.regions = regions;
    this.config = config;
    this.depths = new PyramidDepths(storage::tilesAt);
    this.manualRenders = new ManualRenderCoordinator(renderQueue, isoTileService, this::depth);

    final @NotNull RenderWorkerAllocation workerAllocation =
        RenderWorkerAllocation.fromConfiguredThreads(threadCount);
    this.baseThreads = workerAllocation.baseThreads();
    this.zoomThreads = workerAllocation.zoomThreads();

    this.baseExecutorService =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                this.baseThreads,
                Thread.ofPlatform().name("Taerra-Base-Renderer").daemon().factory());
    this.zoomExecutorService =
        (ThreadPoolExecutor)
            Executors.newFixedThreadPool(
                this.zoomThreads,
                Thread.ofPlatform().name("Taerra-Zoom-Renderer").daemon().factory());
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Taerra-Scheduler").daemon().factory());
  }

  private int batchSizeFor(final @NotNull RenderPriority priority) {
    if (priority.isLive()) {
      return LIVE_BATCH_SIZE;
    }
    final int threads = Math.max(1, this.baseThreads + this.zoomThreads);
    return Math.clamp(
        Math.ceilDiv(this.renderQueue.size(), threads), MIN_BATCH_SIZE, REGION_BATCH_SIZE);
  }

  private static @NotNull BufferedImage combine(
      final BufferedImage @NotNull [] children, final @NotNull MapView view) {
    return ImageUtils.combineTiles(
        children[0], children[1], children[2], children[3], view.zoomedTilePixels());
  }

  @Override
  public void start() {
    if (this.running.get()) {
      return;
    }

    this.running.set(true);
    this.stopping.set(false);
    this.startTimeMillis = System.currentTimeMillis();
    this.config
        .worldViews()
        .forEach(
            (world, views) -> {
              for (final @NotNull MapView view : MapView.values()) {
                if (!views.shows(view)) {
                  dropView(world, view);
                }
              }
            });

    this.scheduler.scheduleWithFixedDelay(
        this::dispatchSafely, 0L, DISPATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);

    this.plugin
        .getLogger()
        .info(
            "Rendering with "
                + this.baseThreads
                + " base and "
                + this.zoomThreads
                + " zoom workers");
  }

  @Override
  public void shutdown() {
    this.stopping.set(true);
    this.running.set(false);

    this.scheduler.shutdown();
    try {
      if (!this.scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
        this.scheduler.shutdownNow();
      }
    } catch (final InterruptedException e) {
      this.scheduler.shutdownNow();
    }

    this.baseExecutorService.shutdownNow();
    this.zoomExecutorService.shutdownNow();
    try {
      if (!this.baseExecutorService.awaitTermination(5, TimeUnit.SECONDS)
          || !this.zoomExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
        this.plugin.getLogger().warning("Render workers did not stop in time");
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public @Nullable ManualRenderSession tryBeginManualRender(
      final @NotNull String mode, final @NotNull String worldName) {
    return this.manualRenders.tryBegin(mode, worldName);
  }

  @Override
  public void updateManualRenderScanProgress(
      final @NotNull ManualRenderSession session, final @NotNull RegionScanProgress progress) {
    this.manualRenders.updateScanProgress(session, progress);
  }

  @Override
  public int submitManualRender(
      final @NotNull ManualRenderSession session,
      final @NotNull RegionScanResult result,
      final @NotNull String plannedMessage) {
    return this.manualRenders.submit(session, result, plannedMessage);
  }

  @Override
  public int cancelManualRender(
      final @NotNull ManualRenderSession session, final @NotNull String reason) {
    return this.manualRenders.cancel(session, reason);
  }

  @Override
  public int cancelManualRender(final @NotNull String reason) {
    return this.manualRenders.cancel(reason);
  }

  @Override
  public @NotNull LiveRenderTiming liveTiming() {
    return this.liveRecorder.snapshot();
  }

  @Override
  public @NotNull RenderHistory renderHistory() {
    return this.manualRenders.history();
  }

  @Override
  public @NotNull RenderStatusSnapshot statusSnapshot() {
    final long elapsedMillis = Math.max(1L, System.currentTimeMillis() - this.startTimeMillis);
    final long zoom0Tiles = this.completedZoom0Tiles.sum();
    final long zoomTiles = this.completedZoomTiles.sum();
    final double averageZoom0Millis =
        zoom0Tiles == 0L ? 0.0 : this.totalZoom0Nanos.sum() / 1_000_000.0 / zoom0Tiles;
    final double averageZoomMillis =
        zoomTiles == 0L ? 0.0 : this.totalZoomNanos.sum() / 1_000_000.0 / zoomTiles;

    final double totalTilesPerSecond = (zoom0Tiles + zoomTiles) * 1000.0 / elapsedMillis;
    final @NotNull ManualRenderSessionSnapshot sessionSnapshot = this.manualRenders.snapshot();
    return new RenderStatusSnapshot(
        this.running.get(),
        this.baseThreads,
        this.zoomThreads,
        this.baseInFlight.get(),
        this.zoomInFlight.get(),
        this.renderQueue.snapshot(),
        zoom0Tiles,
        zoomTiles,
        averageZoom0Millis,
        averageZoomMillis,
        totalTilesPerSecond,
        sessionSnapshot,
        sessionSnapshot.timing());
  }

  @Override
  public int pyramidDepth(final @NotNull TileCoordinate layer) {
    return depth(layer);
  }

  @Override
  public int @Nullable [] renderedArea(final @NotNull String worldName) {
    depth(TileCoordinate.topDown(worldName, 0, 0, 0));
    return this.depths.area(worldName);
  }

  private void dispatchSafely() {
    if (!this.running.get()) {
      return;
    }

    try {
      dispatchWork();
    } catch (final Throwable e) {
      this.plugin
          .getLogger()
          .log(Level.WARNING, "Failed to dispatch render work: " + Throwables.describe(e), e);
    }
  }

  private void dispatchSoon() {
    try {
      this.scheduler.execute(this::dispatchSafely);
    } catch (final RejectedExecutionException ignored) {
    }
  }

  private void dispatchWork() {
    while (this.running.get()
        && this.baseInFlight.get() < this.baseThreads
        && (dispatchBaseBatch(this.baseExecutorService, this.baseInFlight)
            || dispatchZoomTask(this.baseExecutorService, this.baseInFlight))) {}

    while (this.running.get()
        && this.zoomInFlight.get() < this.zoomThreads
        && (dispatchZoomTask(this.zoomExecutorService, this.zoomInFlight)
            || dispatchBaseBatch(this.zoomExecutorService, this.zoomInFlight))) {}
  }

  private boolean dispatchZoomTask(
      final @NotNull ExecutorService executor, final @NotNull AtomicInteger slotCounter) {
    final @Nullable RenderQueueTask task = this.renderQueue.pollNextZoomTask();
    if (task == null) {
      return false;
    }
    slotCounter.incrementAndGet();
    executor.execute(() -> processZoomTask(task, slotCounter));
    return true;
  }

  private boolean dispatchBaseBatch(
      final @NotNull ExecutorService executor, final @NotNull AtomicInteger slotCounter) {
    final @Nullable RenderPriority next = this.renderQueue.nextBasePriority();
    if (next == null) {
      return false;
    }

    final @NotNull List<RenderQueueTask> batch =
        this.renderQueue.pollNextBaseBatch(batchSizeFor(next));
    if (batch.isEmpty()) {
      return false;
    }

    slotCounter.incrementAndGet();
    executor.execute(() -> processBaseBatch(batch, slotCounter));
    return true;
  }

  private void processBaseBatch(
      final @NotNull List<RenderQueueTask> tasks, final @NotNull AtomicInteger slotCounter) {
    final long startedAt = System.nanoTime();
    final int cancels = this.manualRenders.cancels();
    final @NotNull RenderPriority priority = tasks.getFirst().priority();
    final @NotNull List<TileCoordinate> tileCoordinates =
        tasks.stream().map(RenderQueueTask::coordinate).toList();

    try {
      final @Nullable World world = WorldKeys.resolve(tasks.getFirst().coordinate().world());
      if (world == null) {
        this.plugin
            .getLogger()
            .warning("Skipping tiles of unloaded world " + tasks.getFirst().coordinate().world());
        this.manualRenders.markIncomplete(tasks.getFirst().coordinate().world());
        finishTiles(tileCoordinates, priority);
        return;
      }

      final @NotNull MapView view = tileCoordinates.getFirst().view();
      final @NotNull MapViews views = this.config.views(WorldKeys.id(world));
      if (!views.shows(view) && (view == MapView.ISOMETRIC || priority.isLive())) {
        finishTiles(tileCoordinates, priority);
        return;
      }

      this.manualRenders.markDispatched(tileCoordinates);

      final @NotNull List<RenderedTile> renderedTiles;
      final @NotNull List<RenderedTile> layers;
      long chunkReadNanos = 0L;
      long sampleNanos = 0L;
      long renderTilesNanos = 0L;
      int chunksRead = 0;

      final long refreshStartedAt = System.nanoTime();
      final @NotNull LongSet regrounded = new LongOpenHashSet();
      final int refreshedChunks = this.columnsCache.refreshChanged(world, regrounded);
      if (!regrounded.isEmpty()) {
        final @NotNull Set<TileCoordinate> fogged =
            views.shows(MapView.ISOMETRIC)
                ? new HashSet<>(this.isoTileService.tilesShowing(world, regrounded, false))
                : new HashSet<>();
        for (final long chunk : views.shows(MapView.TOP_DOWN) ? regrounded : LongSet.of()) {
          fogged.add(
              TileCoordinate.topDown(
                  WorldKeys.id(world),
                  0,
                  Math.floorDiv(ChunkCoordinate.keyX(chunk) << 4, TileGeometry.TILE_SIZE),
                  Math.floorDiv(ChunkCoordinate.keyZ(chunk) << 4, TileGeometry.TILE_SIZE)));
        }
        tileCoordinates.forEach(fogged::remove);
        this.renderQueue.enqueueAll(fogged, RenderPriority.LIVE_ZOOM_0);
      }
      final long refreshNanos = System.nanoTime() - refreshStartedAt;

      if (view == MapView.ISOMETRIC) {
        final long renderStartedAt = System.nanoTime();
        layers = this.isoTileService.renderBatch(world, tileCoordinates);
        renderedTiles = layers.stream().filter(tile -> tile.layer().base()).toList();
        renderTilesNanos = System.nanoTime() - renderStartedAt;
      } else {
        try (final @NotNull RegionFileChunkSourceProvider provider =
                new RegionFileChunkSourceProvider(world)) {

          final @NotNull CachedChunkColumnsSource source =
              this.columnsCache.sourceFor(world, provider, FogView.BASE);
          final long renderStartedAt = System.nanoTime();
          renderedTiles =
              views.shows(view)
                  ? this.topDownRenderer.renderBatch(source, tileCoordinates)
                  : List.of();
          layers = new ArrayList<>(renderedTiles);
          if (views.shows(view)) {
            layers.addAll(
                FogLayers.topDown(
                    world,
                    provider,
                    this.regions,
                    this.columnsCache,
                    this.topDownRenderer,
                    renderedTiles));
          }
          final long batchNanos = System.nanoTime() - renderStartedAt;
          sampleNanos = source.sampleNanos();
          renderTilesNanos = Math.max(0L, batchNanos - sampleNanos);
          chunkReadNanos = provider.chunkReadNanos();
          chunksRead = provider.chunksRead();

          if (views.shows(MapView.ISOMETRIC)) {
            this.manualRenders.queueIsometricCoverage(
                world,
                tileCoordinates,
                views.shows(view)
                    ? renderedTiles.stream().map(RenderedTile::coordinate).toList()
                    : tileCoordinates,
                source,
                priority,
                cancels);
          }
        }
        if (priority.isLive()) {
          this.liveRecorder.recordBatch(
              chunkReadNanos,
              refreshNanos,
              sampleNanos,
              renderTilesNanos,
              chunksRead + refreshedChunks);
        }
      }

      final long persistStartedAt = System.nanoTime();
      layers.addAll(FogLayers.clear(layers));
      for (final @NotNull RenderedTile renderedTile : layers) {
        persistTile(renderedTile.coordinate(), renderedTile.layer(), renderedTile.image());
        persistPick(renderedTile);
        if (renderedTile.layer().base()) {
          this.completedZoom0Tiles.increment();
        }
      }
      if (this.regions.anyFogged(WorldKeys.id(world))) {
        final @NotNull Map<TileCoordinate, Set<FogView>> kept = new HashMap<>();
        for (final @NotNull RenderedTile renderedTile : layers) {
          final @NotNull Set<FogView> tile =
              kept.computeIfAbsent(renderedTile.coordinate(), _ -> new HashSet<>());
          if (!renderedTile.layer().base()) {
            tile.add(renderedTile.layer());
          }
        }
        kept.forEach(this::keepOnly);
      }

      final @NotNull Set<TileCoordinate> produced =
          renderedTiles.stream().map(RenderedTile::coordinate).collect(Collectors.toSet());
      for (final @NotNull TileCoordinate coordinate : tileCoordinates) {
        if (views.shows(view) && !produced.contains(coordinate) && deleteStoredTile(coordinate)) {
          pyramidChanged(coordinate);
        }
      }

      if (priority.isLive() || view == MapView.ISOMETRIC || !views.shows(view)) {
        finishTiles(tileCoordinates, priority);
      } else {
        for (final @NotNull TileCoordinate coordinate : tileCoordinates) {
          this.renderQueue.markClean(coordinate);
          recordTileCompletion(coordinate);
        }
        for (final @NotNull TileCoordinate coordinate : tileCoordinates) {
          if (!produced.contains(coordinate)) {
            enqueueParentTile(coordinate, RenderPriority.FULL_RENDER_ZOOM_PARENT);
          }
        }
        for (final @NotNull TileCoordinate highestTile : persistZoomPyramid(renderedTiles)) {
          enqueueParentTile(highestTile, RenderPriority.FULL_RENDER_ZOOM_PARENT);
        }
      }
      final long persistNanos = System.nanoTime() - persistStartedAt;

      if (priority.isLive()) {
        this.liveRecorder.recordPersist(persistNanos, renderedTiles.size());
      } else {
        this.manualRenders.recordRegionTiming(
            new RegionRenderTiming(
                chunkReadNanos,
                sampleNanos,
                renderTilesNanos,
                System.nanoTime() - startedAt,
                chunksRead,
                renderedTiles.size()),
            persistNanos);
      }
      this.totalZoom0Nanos.add(System.nanoTime() - startedAt);
      if (!this.failures.isEmpty()) {
        tileCoordinates.forEach(this.failures::remove);
      }
    } catch (final Throwable e) {
      if (this.stopping.get()) {
        this.manualRenders.markReturned(tileCoordinates);
        tileCoordinates.forEach(this.renderQueue::markFailed);
        return;
      }
      final @NotNull List<TileCoordinate> retried = new ArrayList<>();
      final @NotNull List<TileCoordinate> failed = new ArrayList<>();
      for (final @NotNull TileCoordinate tile : tileCoordinates) {
        (this.failures.merge(tile, 1, Integer::sum) < RENDER_ATTEMPTS ? retried : failed).add(tile);
      }
      this.manualRenders.markReturned(retried);
      retried.forEach(this.renderQueue::markFailed);
      if (failed.isEmpty()) {
        return;
      }
      failed.forEach(this.failures::remove);
      this.plugin
          .getLogger()
          .log(
              Level.WARNING,
              "Failed to render tile batch starting at "
                  + failed.getFirst()
                  + " after "
                  + RENDER_ATTEMPTS
                  + " attempts: "
                  + Throwables.describe(e),
              e);
      this.manualRenders.markIncomplete(failed.getFirst().world());
      finishTiles(failed, priority);
    } finally {
      slotCounter.decrementAndGet();
      dispatchSoon();
    }
  }

  private @NotNull List<TileCoordinate> persistZoomPyramid(
      final @NotNull List<RenderedTile> zoom0Tiles) throws Exception {
    final long zoomStartedAt = System.nanoTime();
    @NotNull Map<TileCoordinate, BufferedImage> currentLevel = new HashMap<>();
    for (final @NotNull RenderedTile renderedTile : zoom0Tiles) {
      currentLevel.put(renderedTile.coordinate(), renderedTile.image());
    }

    @NotNull List<TileCoordinate> highestLevel = List.of();
    final int depth = zoom0Tiles.isEmpty() ? 0 : depth(zoom0Tiles.getFirst().coordinate());
    for (int targetZoom = 1;
        targetZoom < depth && !currentLevel.isEmpty() && !this.stopping.get();
        targetZoom++) {
      final @NotNull Map<TileCoordinate, BufferedImage[]> groupedChildren = new HashMap<>();
      for (final Map.@NotNull Entry<TileCoordinate, BufferedImage> entry :
          currentLevel.entrySet()) {
        final @NotNull TileCoordinate child = entry.getKey();
        final @NotNull TileCoordinate parent = TileHierarchy.parentOf(child);
        final BufferedImage @NotNull [] children =
            groupedChildren.computeIfAbsent(parent, key -> new BufferedImage[4]);
        children[TileHierarchy.childIndex(child)] = entry.getValue();
      }

      final @NotNull Map<TileCoordinate, BufferedImage> nextLevel =
          new HashMap<>(groupedChildren.size());
      final @NotNull List<TileCoordinate> nextLevelCoordinates =
          new ArrayList<>(groupedChildren.size());
      for (final Map.@NotNull Entry<TileCoordinate, BufferedImage[]> entry :
          groupedChildren.entrySet()) {
        final @NotNull TileCoordinate parent = entry.getKey();
        if (!this.renderQueue.claim(parent, RenderPriority.FULL_RENDER_ZOOM_PARENT)) {
          continue;
        }
        try {
          final BufferedImage @NotNull [] children = entry.getValue();
          final TileCoordinate @NotNull [] childCoordinates = TileHierarchy.childrenOf(parent);
          boolean anyPresent = false;
          for (int childIndex = 0; childIndex < children.length; childIndex++) {
            if (children[childIndex] == null) {
              children[childIndex] = this.cache.get(childCoordinates[childIndex]).orElse(null);
            }
            if (children[childIndex] != null) {
              anyPresent = true;
            }
          }
          if (!anyPresent) {
            continue;
          }

          final @NotNull BufferedImage image = combine(children, parent.view());
          persistTile(parent, image);
          persistLayers(parent, childCoordinates);
          recordTileCompletion(parent);
          nextLevel.put(parent, image);
          nextLevelCoordinates.add(parent);
          this.completedZoomTiles.increment();
        } finally {
          this.renderQueue.markClean(parent);
        }
      }

      highestLevel = nextLevelCoordinates;
      currentLevel = nextLevel;
    }

    if (!highestLevel.isEmpty()) {
      this.totalZoomNanos.add(System.nanoTime() - zoomStartedAt);
    }
    return highestLevel;
  }

  private void processZoomTask(
      final @NotNull RenderQueueTask task, final @NotNull AtomicInteger slotCounter) {
    final long startedAt = System.nanoTime();
    final @NotNull TileCoordinate coord = task.coordinate();
    final TileCoordinate @NotNull [] childCoordinates = TileHierarchy.childrenOf(coord);

    try {
      final BufferedImage @NotNull [] childTiles = new BufferedImage[4];
      boolean anyPresent = false;
      for (int i = 0; i < 4; i++) {
        childTiles[i] = this.cache.get(childCoordinates[i]).orElse(null);
        anyPresent |= childTiles[i] != null;
      }
      if (anyPresent && coord.zoom() <= depth(coord)) {
        persistTile(coord, combine(childTiles, coord.view()));
        persistLayers(coord, childCoordinates);
        this.completedZoomTiles.increment();
        this.totalZoomNanos.add(System.nanoTime() - startedAt);
      } else if (deleteStoredTile(coord)) {
        pyramidChanged(coord);
      }
    } catch (final Throwable e) {
      if (this.stopping.get()) {
        this.renderQueue.markFailed(coord);
        return;
      }
      this.plugin
          .getLogger()
          .log(
              Level.WARNING,
              "Failed to render zoomed-out tile " + coord + ": " + Throwables.describe(e),
              e);
      this.manualRenders.markIncomplete(coord.world());
    } finally {
      slotCounter.decrementAndGet();
    }
    finishTiles(List.of(coord), task.priority());
    dispatchSoon();
  }

  private void finishTiles(
      final @NotNull List<TileCoordinate> tiles, final @NotNull RenderPriority priority) {
    for (final @NotNull TileCoordinate tile : tiles) {
      this.renderQueue.markClean(tile);
      recordTileCompletion(tile);
    }
    for (final @NotNull TileCoordinate tile : tiles) {
      enqueueParentTile(tile, priority.parentPriority());
    }
  }

  private void recordTileCompletion(final @NotNull TileCoordinate coord) {
    final @Nullable CompletedRender completed = this.manualRenders.recordTileCompletion(coord);
    if (completed == null) {
      return;
    }
    final boolean sweeps =
        FULL_RENDER.equals(completed.mode()) || LIMIT_RENDER.equals(completed.mode());
    if (sweeps && this.manualRenders.complete()) {
      sweepStaleTiles(completed);
    } else if (sweeps) {
      this.plugin
          .getLogger()
          .warning(
              "Kept the old tiles of "
                  + completed.worldName()
                  + " because some of its regions could not be read or rendered");
    }
    for (final @NotNull MapView view : MapView.values()) {
      for (final @NotNull MapRotation rotation :
          view.rotated() ? MapRotation.values() : new MapRotation[] {MapRotation.R0}) {
        resizePyramid(new TileCoordinate(completed.worldName(), view, rotation, 0, 0, 0));
      }
    }
  }

  private void sweepStaleTiles(final @NotNull CompletedRender render) {
    final long cutoff = render.finishedAt() - render.durationMillis() - STALE_TILE_MARGIN_MILLIS;
    try {
      final @NotNull List<TileCoordinate> stale =
          this.storage.tilesOlderThan(render.worldName(), cutoff);
      int removed = 0;
      for (final @NotNull TileCoordinate tile : stale) {
        if (this.storage.lastModified(tile) < cutoff && deleteStoredTile(tile)) {
          enqueueParentTile(tile, RenderPriority.FULL_RENDER_ZOOM_PARENT);
          removed++;
        }
      }
      if (removed > 0) {
        this.plugin
            .getLogger()
            .info("Removed " + removed + " stale tiles from " + render.worldName());
      }
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .log(Level.WARNING, "Failed to remove stale tiles from " + render.worldName(), e);
    }
  }

  private void persistTile(final @NotNull TileCoordinate coord, final @NotNull BufferedImage image)
      throws Exception {
    persistTile(coord, FogView.BASE, image);
  }

  private void persistTile(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull BufferedImage image)
      throws Exception {
    if (!this.config.views(coord.world()).shows(coord.view())) {
      return;
    }
    final TileStorage.@NotNull Saved saved = this.storage.saveTile(coord, layer, image);
    this.cache.put(coord, layer, image);
    if (saved.changed()) {
      this.tileUpdateBroadcaster.tileUpdated(coord);
    }
    if (layer.base() && saved == TileStorage.Saved.CREATED) {
      this.depths.extend(coord);
      pyramidChanged(coord);
    }
  }

  private void persistLayers(
      final @NotNull TileCoordinate parent, final TileCoordinate @NotNull [] children)
      throws Exception {
    if (!this.regions.anyFogged(parent.world())) {
      return;
    }
    final @NotNull Set<FogView> layers = new TreeSet<>(FogView.ORDER);
    final @NotNull List<List<FogView>> stored = new ArrayList<>(children.length);
    for (final @NotNull TileCoordinate child : children) {
      final @NotNull List<FogView> childLayers = this.storage.layers(child);
      stored.add(childLayers);
      layers.addAll(childLayers);
    }
    for (final @NotNull FogView layer : layers) {
      final BufferedImage @NotNull [] images = new BufferedImage[children.length];
      for (int index = 0; index < children.length; index++) {
        final @NotNull TileCoordinate child = children[index];
        images[index] =
            stored.get(index).contains(layer)
                ? this.cache.get(child, layer).orElse(null)
                : layer.kind() == FogView.Kind.CLEAR ? this.cache.get(child).orElse(null) : null;
      }
      persistTile(parent, layer, combine(images, parent.view()));
    }
    keepOnly(parent, layers);
  }

  @Override
  public void dropUnusedLayers(final @NotNull String worldName) {
    if (this.regions.anyFogged(worldName)) {
      return;
    }
    try {
      this.storage.deleteLayers(worldName);
      this.cache.clear();
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .log(Level.WARNING, "Failed to delete the region layers of " + worldName, e);
    }
  }

  @Override
  public void dropView(final @NotNull String worldName, final @NotNull MapView view) {
    try {
      this.storage.deleteView(worldName, view);
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .log(Level.WARNING, "Failed to delete the " + view.id() + " tiles of " + worldName, e);
    }
    this.cache.clear();
    for (final @NotNull MapRotation rotation :
        view.rotated() ? MapRotation.values() : new MapRotation[] {MapRotation.R0}) {
      resizePyramid(new TileCoordinate(worldName, view, rotation, 0, 0, 0));
    }
  }

  private void keepOnly(final @NotNull TileCoordinate coord, final @NotNull Set<FogView> layers) {
    for (final @NotNull FogView stored : this.storage.layers(coord)) {
      if (!layers.contains(stored) && this.storage.deleteLayer(coord, stored)) {
        this.cache.invalidate(coord, stored);
        this.tileUpdateBroadcaster.tileUpdated(coord);
      }
    }
  }

  private void persistPick(final @NotNull RenderedTile tile) {
    final @Nullable PickBuffer pick = tile.pick();
    if (pick == null) {
      return;
    }
    try {
      final byte @Nullable [] encoded = pick.encode();
      if (encoded != null) {
        this.storage.savePick(tile.coordinate(), tile.layer(), encoded);
      }
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .log(Level.WARNING, "Failed to store pick buffer for " + tile.coordinate(), e);
    }
  }

  private boolean deleteStoredTile(final @NotNull TileCoordinate coord) {
    final boolean deleted = this.storage.deleteTile(coord);
    this.cache.invalidate(coord);
    if (deleted) {
      this.tileUpdateBroadcaster.tileUpdated(coord);
    }
    return deleted;
  }

  private int depth(final @NotNull TileCoordinate tile) {
    final @Nullable Integer known = this.depths.known(tile);
    return known != null ? known : resizePyramid(tile);
  }

  private void pyramidChanged(final @NotNull TileCoordinate tile) {
    if (tile.zoom() + 1 >= depth(tile)) {
      resizePyramid(tile);
    }
  }

  private int resizePyramid(final @NotNull TileCoordinate tile) {
    final @NotNull PyramidDepths.Measure measure =
        this.depths.measure(tile, !this.manualRenders.rendering(tile.world()));
    if (measure.depth() > measure.top()) {
      measure
          .topTiles()
          .forEach(top -> enqueueParentTile(top, RenderPriority.FULL_RENDER_ZOOM_PARENT));
    }
    for (int zoom = measure.depth() + 1; zoom <= measure.top(); zoom++) {
      this.storage.tilesAt(tile.at(zoom, 0, 0)).forEach(this::deleteStoredTile);
    }
    return measure.depth();
  }

  private void enqueueParentTile(
      final @NotNull TileCoordinate coord, final @NotNull RenderPriority priority) {
    if (coord.zoom() < depth(coord)) {
      this.renderQueue.enqueue(TileHierarchy.parentOf(coord), priority);
    }
  }

  @Override
  public synchronized void setThreads(final int configuredThreads) {
    final @NotNull RenderWorkerAllocation allocation =
        RenderWorkerAllocation.fromConfiguredThreads(configuredThreads);
    resize(this.baseExecutorService, allocation.baseThreads());
    resize(this.zoomExecutorService, allocation.zoomThreads());
    this.baseThreads = allocation.baseThreads();
    this.zoomThreads = allocation.zoomThreads();
    this.plugin
        .getLogger()
        .info(
            "Rendering with "
                + this.baseThreads
                + " base and "
                + this.zoomThreads
                + " zoom workers");
    dispatchSoon();
  }

  private static void resize(final @NotNull ThreadPoolExecutor executor, final int threads) {
    if (threads > executor.getMaximumPoolSize()) {
      executor.setMaximumPoolSize(threads);
      executor.setCorePoolSize(threads);
    } else {
      executor.setCorePoolSize(threads);
      executor.setMaximumPoolSize(threads);
    }
  }
}

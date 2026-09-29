package net.aerulion.taerra.rendering.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderQueueTest {

  @TempDir private Path temp;

  private RenderQueue tracker;

  private static @NotNull TileCoordinate tile(final int x, final int z) {
    return TileCoordinate.topDown("world", 0, x, z);
  }

  @Test
  @DisplayName("a batch never mixes the two views, whose coordinates mean different things")
  void batchesNeverMixViews() {

    for (int i = 0; i < 4; i++) {
      this.tracker.enqueue(
          TileCoordinate.topDown("world", 0, i, 0), RenderPriority.FULL_RENDER_ZOOM_0);
      this.tracker.enqueue(
          TileCoordinate.isometric("world", MapRotation.R0, 0, i, 0),
          RenderPriority.FULL_RENDER_ZOOM_0);
    }

    int batches = 0;
    List<RenderQueueTask> batch;
    while (!(batch = this.tracker.pollNextBaseBatch(16, true)).isEmpty()) {
      batches++;
      final @NotNull MapView view = batch.getFirst().coordinate().view();
      for (final @NotNull RenderQueueTask task : batch) {
        assertSame(view, task.coordinate().view(), "batch mixed views");
        this.tracker.markClean(task.coordinate());
      }
    }
    assertTrue(batches >= 2, "both views should have been drained, got " + batches + " batches");
  }

  @Test
  @DisplayName("tiles queued ahead jump what is already waiting, in their own order")
  void enqueueAheadJumpsTheQueue() {
    this.tracker.enqueue(tile(100, 0), RenderPriority.FULL_RENDER_ZOOM_0);
    this.tracker.enqueueAllAhead(
        List.of(tile(0, 0), tile(1, 0)), RenderPriority.FULL_RENDER_ZOOM_0);

    final @NotNull List<RenderQueueTask> batch = this.tracker.pollNextBaseBatch(1, true);
    assertEquals(tile(0, 0), batch.getFirst().coordinate());
    assertEquals(tile(1, 0), this.tracker.pollNextBaseBatch(1, true).getFirst().coordinate());
    assertEquals(tile(100, 0), this.tracker.pollNextBaseBatch(1, true).getFirst().coordinate());
    assertCountersConsistent();
  }

  private void assertCountersConsistent() {
    final @NotNull RenderQueueSnapshot snapshot = this.tracker.snapshot();
    final int queued =
        snapshot.queuedLiveZoom0()
            + snapshot.queuedFullZoom0()
            + snapshot.queuedLiveZoom()
            + snapshot.queuedFullZoom();
    final int inFlight = snapshot.baseInFlight() + snapshot.zoomInFlight();
    assertEquals(
        queued + inFlight, this.tracker.size(), "counters disagree with tracked task count");
  }

  @BeforeEach
  void setUp() {
    this.tracker =
        new RenderQueueImpl(
            new RenderQueueStoreImpl(
                this.temp.resolve("dirty-tiles.json"), Logger.getLogger("RenderQueueTest")));
  }

  @Test
  @DisplayName("counters survive a full enqueue, poll and complete cycle")
  void countersTrackFullCycle() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    this.tracker.enqueue(tile(1, 0), RenderPriority.LIVE_ZOOM_0);
    assertEquals(2, this.tracker.snapshot().queuedLiveZoom0());
    assertCountersConsistent();

    final @NotNull List<RenderQueueTask> batch = this.tracker.pollNextBaseBatch(16, true);
    assertEquals(2, batch.size());
    assertEquals(0, this.tracker.snapshot().queuedLiveZoom0());
    assertEquals(2, this.tracker.snapshot().baseInFlight());
    assertCountersConsistent();

    batch.forEach(task -> this.tracker.markClean(task.coordinate()));
    assertEquals(0, this.tracker.size());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("upgrading a queued tile moves it between priorities without double counting")
  void priorityUpgradeMovesTheCount() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.FULL_RENDER_ZOOM_0);
    assertEquals(1, this.tracker.snapshot().queuedFullZoom0());

    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    assertEquals(0, this.tracker.snapshot().queuedFullZoom0());
    assertEquals(1, this.tracker.snapshot().queuedLiveZoom0());
    assertEquals(1, this.tracker.size());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("a tile dirtied while in flight is re-queued when it completes")
  void requeueWhileInFlight() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    this.tracker.pollNextBaseBatch(16, true);

    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    assertEquals(0, this.tracker.snapshot().queuedLiveZoom0(), "should not queue while in flight");

    this.tracker.markClean(tile(0, 0));
    assertEquals(1, this.tracker.snapshot().queuedLiveZoom0(), "re-dirtied tile must come back");
    assertCountersConsistent();
  }

  @Test
  @DisplayName("a failed tile is retried")
  void failedTileIsRetried() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    this.tracker.pollNextBaseBatch(16, true);
    this.tracker.markFailed(tile(0, 0));

    assertEquals(1, this.tracker.snapshot().queuedLiveZoom0());
    assertEquals(0, this.tracker.snapshot().baseInFlight());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("a batch never mixes priorities, so its data source stays unambiguous")
  void batchesAreSinglePriority() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    this.tracker.enqueue(tile(1, 0), RenderPriority.FULL_RENDER_ZOOM_0);

    final @NotNull List<RenderQueueTask> batch = this.tracker.pollNextBaseBatch(16, true);
    assertEquals(1, batch.size(), "the bulk tile must not join a live batch");
    assertSame(RenderPriority.LIVE_ZOOM_0, batch.getFirst().priority());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("a batch only takes tiles from the seed's own region")
  void batchesAreRegionLocal() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.FULL_RENDER_ZOOM_0);
    this.tracker.enqueue(tile(1, 0), RenderPriority.FULL_RENDER_ZOOM_0);
    this.tracker.enqueue(tile(8, 0), RenderPriority.FULL_RENDER_ZOOM_0);

    final @NotNull List<RenderQueueTask> batch = this.tracker.pollNextBaseBatch(64, true);
    assertEquals(2, batch.size(), "tiles from a different region must not be batched together");
    assertCountersConsistent();
  }

  @Test
  @DisplayName("zoom work is reported separately from zoom-0 work")
  void zoomTasksTrackedSeparately() {
    assertNull(this.tracker.nextZoomPriority(true));

    this.tracker.enqueue(TileCoordinate.topDown("world", 1, 0, 0), RenderPriority.LIVE_ZOOM_PARENT);
    assertSame(RenderPriority.LIVE_ZOOM_PARENT, this.tracker.nextZoomPriority(true));
    assertNull(this.tracker.nextBasePriority(true), "a zoom tile is not zoom-0 work");
    assertCountersConsistent();
  }

  @Test
  @DisplayName("throttled live work does not hold back the full render queued behind it")
  void throttledLiveWorkDoesNotBlockFullRender() {
    final @NotNull TileCoordinate live = TileCoordinate.topDown("world", 1, 0, 0);
    final @NotNull TileCoordinate full = TileCoordinate.topDown("world", 1, 5, 0);
    this.tracker.enqueue(live, RenderPriority.LIVE_ZOOM_PARENT);
    this.tracker.enqueue(full, RenderPriority.FULL_RENDER_ZOOM_PARENT);

    assertSame(RenderPriority.FULL_RENDER_ZOOM_PARENT, this.tracker.nextZoomPriority(false));
    assertEquals(full, this.tracker.pollNextZoomTask(false).coordinate());
    assertNull(this.tracker.pollNextZoomTask(false), "live work waits for a permit");
    assertEquals(live, this.tracker.pollNextZoomTask(true).coordinate());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("canceling a manual render discards only its queued tiles")
  void discardQueuedLeavesLiveWorkAlone() {
    this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    this.tracker.enqueue(tile(1, 0), RenderPriority.FULL_RENDER_ZOOM_0);

    assertEquals(1, this.tracker.discardQueued(RenderPriority.FULL_RENDER_ZOOM_0));
    assertEquals(1, this.tracker.snapshot().queuedLiveZoom0());
    assertEquals(0, this.tracker.snapshot().queuedFullZoom0());
    assertCountersConsistent();
  }

  @Test
  @DisplayName("re-enqueueing the same tile does not inflate the queue")
  void duplicateEnqueueIsDeduplicated() {
    for (int i = 0; i < 50; i++) {
      this.tracker.enqueue(tile(0, 0), RenderPriority.LIVE_ZOOM_0);
    }
    assertEquals(1, this.tracker.size());
    assertCountersConsistent();
  }
}

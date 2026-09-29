package net.aerulion.taerra.rendering.queue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.aerulion.taerra.model.RegionCoordinate;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RenderQueueImpl implements RenderQueue {

  private static final @NotNull List<RenderPriority> BASE_PRIORITY_ORDER =
      List.of(RenderPriority.LIVE_ZOOM_0, RenderPriority.FULL_RENDER_ZOOM_0);
  private static final @NotNull List<RenderPriority> ZOOM_PRIORITY_ORDER =
      List.of(RenderPriority.LIVE_ZOOM_PARENT, RenderPriority.FULL_RENDER_ZOOM_PARENT);

  private static final int BATCH_SCAN_WINDOW = 4096;

  private final @NotNull RenderQueueStore store;
  private final @NotNull EnumMap<RenderPriority, ArrayDeque<TileCoordinate>> queues;
  private final @NotNull Map<TileCoordinate, RenderPriority> queuedPriorities;
  private final @NotNull Map<TileCoordinate, RenderPriority> inFlightPriorities;
  private final @NotNull Map<TileCoordinate, RenderPriority> requeuePriorities;

  private final int @NotNull [] queuedCounts;
  private int baseInFlightCount;
  private int zoomInFlightCount;

  public RenderQueueImpl(final @NotNull RenderQueueStore store) {
    super();
    this.store = store;
    this.queues = new EnumMap<>(RenderPriority.class);
    this.queuedPriorities = new HashMap<>();
    this.inFlightPriorities = new HashMap<>();
    this.requeuePriorities = new HashMap<>();
    this.queuedCounts = new int[RenderPriority.values().length];

    for (final RenderPriority priority : RenderPriority.values()) {
      this.queues.put(priority, new ArrayDeque<>());
    }

    load();
  }

  @Override
  public synchronized boolean enqueue(
      final @NotNull TileCoordinate coord, final @NotNull RenderPriority priority) {
    return enqueue(coord, priority, false);
  }

  @Override
  public synchronized void enqueueAll(
      final @NotNull Collection<TileCoordinate> coordinates,
      final @NotNull RenderPriority priority) {
    for (final @NotNull TileCoordinate coordinate : coordinates) {
      enqueue(coordinate, priority);
    }
  }

  @Override
  public synchronized void enqueueAllAhead(
      final @NotNull List<TileCoordinate> coordinates, final @NotNull RenderPriority priority) {
    for (final @NotNull TileCoordinate coordinate : coordinates.reversed()) {
      enqueue(coordinate, priority, true);
    }
  }

  @Override
  public synchronized @NotNull List<RenderQueueTask> pollNextBaseBatch(
      final int maxBatchSize, final boolean liveAllowed) {
    final @Nullable RenderQueueTask seedTask = pollNext(BASE_PRIORITY_ORDER, liveAllowed);
    if (seedTask == null) {
      return List.of();
    }

    final @NotNull List<RenderQueueTask> batch = new ArrayList<>(Math.max(1, maxBatchSize));
    batch.add(seedTask);
    if (maxBatchSize <= 1) {
      return batch;
    }

    final @NotNull RegionCoordinate seedRegion =
        RegionCoordinate.fromZoom0Tile(seedTask.coordinate());
    final @NotNull RenderPriority seedPriority = seedTask.priority();
    final Deque<TileCoordinate> queue = this.queues.get(seedPriority);
    final @NotNull Iterator<TileCoordinate> iterator = queue.iterator();
    for (int scanned = 0;
        iterator.hasNext() && batch.size() < maxBatchSize && scanned < BATCH_SCAN_WINDOW;
        scanned++) {
      final TileCoordinate candidate = iterator.next();
      if (this.queuedPriorities.get(candidate) != seedPriority
          || candidate.view() != seedTask.coordinate().view()
          || candidate.rotation() != seedTask.coordinate().rotation()
          || !candidate.world().equals(seedTask.coordinate().world())
          || !RegionCoordinate.fromZoom0Tile(candidate).equals(seedRegion)) {
        continue;
      }
      iterator.remove();
      clearQueued(candidate);
      setInFlight(candidate, seedPriority);
      batch.add(new RenderQueueTask(candidate, seedPriority));
    }
    return batch;
  }

  @Override
  public synchronized @Nullable RenderQueueTask pollNextZoomTask(final boolean liveAllowed) {
    return pollNext(ZOOM_PRIORITY_ORDER, liveAllowed);
  }

  @Override
  public synchronized void markClean(final @NotNull TileCoordinate coord) {
    clearInFlight(coord);
    final RenderPriority requeuePriority = this.requeuePriorities.remove(coord);
    if (requeuePriority != null) {
      setQueued(coord, requeuePriority);
      this.queues.get(requeuePriority).addFirst(coord);
    }
  }

  @Override
  public synchronized void markFailed(final @NotNull TileCoordinate coord) {
    final @Nullable RenderPriority inFlightPriority = clearInFlight(coord);
    final @Nullable RenderPriority retryPriority =
        RenderPriority.higherPriority(this.requeuePriorities.remove(coord), inFlightPriority);
    if (retryPriority != null) {
      setQueued(coord, retryPriority);
      this.queues.get(retryPriority).addFirst(coord);
    }
  }

  @Override
  public synchronized @Nullable RenderPriority nextBasePriority(final boolean liveAllowed) {
    return nextPriority(BASE_PRIORITY_ORDER, liveAllowed);
  }

  @Override
  public synchronized @Nullable RenderPriority nextZoomPriority(final boolean liveAllowed) {
    return nextPriority(ZOOM_PRIORITY_ORDER, liveAllowed);
  }

  @Override
  public synchronized int discardQueued(final @NotNull RenderPriority priority) {
    final ArrayDeque<TileCoordinate> queue = this.queues.get(priority);
    int removed = 0;
    for (final @NotNull TileCoordinate coord : queue) {
      if (this.queuedPriorities.get(coord) == priority) {
        clearQueued(coord);
        removed++;
      }
    }
    queue.clear();
    return removed;
  }

  @Override
  public synchronized @NotNull RenderQueueSnapshot snapshot() {
    return new RenderQueueSnapshot(
        this.queuedCounts[RenderPriority.LIVE_ZOOM_0.ordinal()],
        this.queuedCounts[RenderPriority.FULL_RENDER_ZOOM_0.ordinal()],
        this.queuedCounts[RenderPriority.LIVE_ZOOM_PARENT.ordinal()],
        this.queuedCounts[RenderPriority.FULL_RENDER_ZOOM_PARENT.ordinal()],
        this.baseInFlightCount,
        this.zoomInFlightCount,
        this.requeuePriorities.size());
  }

  @Override
  public synchronized int size() {
    return this.queuedPriorities.size() + this.inFlightPriorities.size();
  }

  @Override
  public void save() {
    synchronized (this.store) {
      final @NotNull Map<TileCoordinate, RenderPriority> allTasks;
      synchronized (this) {
        allTasks = new HashMap<>(this.queuedPriorities);
        this.inFlightPriorities.forEach(
            (coord, priority) -> allTasks.merge(coord, priority, RenderPriority::higherPriority));
        this.requeuePriorities.forEach(
            (coord, priority) -> allTasks.merge(coord, priority, RenderPriority::higherPriority));
      }
      this.store.save(allTasks);
    }
  }

  private boolean enqueue(
      final @NotNull TileCoordinate coord,
      final @NotNull RenderPriority priority,
      final boolean ahead) {
    if ((coord.zoom() == 0) != priority.isZoom0()) {
      throw new IllegalArgumentException(
          "Priority " + priority + " does not match tile zoom " + coord.zoom());
    }

    final RenderPriority inFlightPriority = this.inFlightPriorities.get(coord);
    if (inFlightPriority != null) {
      this.requeuePriorities.put(
          coord, RenderPriority.higherPriority(this.requeuePriorities.get(coord), priority));
      return false;
    }

    final RenderPriority existingPriority = this.queuedPriorities.get(coord);
    if (existingPriority == null) {
      setQueued(coord, priority);
      if (ahead) {
        this.queues.get(priority).addFirst(coord);
      } else {
        this.queues.get(priority).addLast(coord);
      }
      return true;
    }

    if (priority.outranks(existingPriority)) {
      setQueued(coord, priority);
      this.queues.get(priority).addFirst(coord);
    }
    return false;
  }

  private void setQueued(
      final @NotNull TileCoordinate coord, final @NotNull RenderPriority priority) {
    final @Nullable RenderPriority previous = this.queuedPriorities.put(coord, priority);
    if (previous != null) {
      this.queuedCounts[previous.ordinal()]--;
    }
    this.queuedCounts[priority.ordinal()]++;
  }

  private void clearQueued(final @NotNull TileCoordinate coord) {
    final RenderPriority previous = this.queuedPriorities.remove(coord);
    if (previous != null) {
      this.queuedCounts[previous.ordinal()]--;
    }
  }

  private void setInFlight(
      final @NotNull TileCoordinate coord, final @NotNull RenderPriority priority) {
    final @Nullable RenderPriority previous = this.inFlightPriorities.put(coord, priority);
    if (previous != null) {
      adjustInFlight(previous, -1);
    }
    adjustInFlight(priority, 1);
  }

  private @Nullable RenderPriority clearInFlight(final @NotNull TileCoordinate coord) {
    final RenderPriority previous = this.inFlightPriorities.remove(coord);
    if (previous != null) {
      adjustInFlight(previous, -1);
    }
    return previous;
  }

  private void adjustInFlight(final @NotNull RenderPriority priority, final int delta) {
    if (priority.isZoom0()) {
      this.baseInFlightCount += delta;
    } else {
      this.zoomInFlightCount += delta;
    }
  }

  private @Nullable RenderQueueTask pollNext(
      final @NotNull List<RenderPriority> priorities, final boolean liveAllowed) {
    for (final @NotNull RenderPriority priority : priorities) {
      if (priority.isLive() && !liveAllowed) {
        continue;
      }
      final ArrayDeque<TileCoordinate> queue = this.queues.get(priority);
      while (!queue.isEmpty()) {
        final TileCoordinate coord = queue.pollFirst();
        final RenderPriority currentPriority = this.queuedPriorities.get(coord);
        if (currentPriority != priority) {
          continue;
        }
        clearQueued(coord);
        setInFlight(coord, priority);
        return new RenderQueueTask(coord, priority);
      }
    }
    return null;
  }

  private @Nullable RenderPriority nextPriority(
      final @NotNull List<RenderPriority> priorities, final boolean liveAllowed) {
    for (final @NotNull RenderPriority priority : priorities) {
      if ((liveAllowed || !priority.isLive()) && this.queuedCounts[priority.ordinal()] > 0) {
        return priority;
      }
    }
    return null;
  }

  private synchronized void load() {
    this.store.load().forEach(this::enqueue);
  }
}

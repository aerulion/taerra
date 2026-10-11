package net.aerulion.taerra.rendering.queue;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.aerulion.taerra.model.TileCoordinate;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class ChangeBufferImpl implements ChangeBuffer {

  private final @NotNull Plugin plugin;
  private final @NotNull RenderQueue renderQueue;
  private final @NotNull Set<TileCoordinate> pending;
  private final @NotNull ScheduledExecutorService scheduler;
  private final long intervalSeconds;

  public ChangeBufferImpl(
      final @NotNull Plugin plugin,
      final @NotNull RenderQueue renderQueue,
      final long intervalSeconds) {
    super();
    this.plugin = plugin;
    this.renderQueue = renderQueue;
    this.intervalSeconds = intervalSeconds;
    this.pending = ConcurrentHashMap.newKeySet();
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Taerra-Changes").daemon().factory());
  }

  private boolean isBuffering() {
    return this.intervalSeconds > 0L;
  }

  @Override
  public void add(final @NotNull Collection<TileCoordinate> tiles) {
    if (tiles.isEmpty()) {
      return;
    }
    if (!isBuffering()) {
      this.renderQueue.enqueueAll(tiles, RenderPriority.LIVE_ZOOM_0);
      return;
    }
    this.pending.addAll(tiles);
  }

  @Override
  public int discard() {
    final int discarded = this.pending.size();
    this.pending.clear();
    return discarded;
  }

  @Override
  public void start() {
    if (!isBuffering()) {
      this.plugin.getLogger().info("Rendering world changes as they happen");
      return;
    }

    this.scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            flush();
          } catch (final Throwable e) {
            this.plugin
                .getLogger()
                .log(Level.WARNING, "Failed to queue world changes: " + e.getMessage(), e);
          }
        },
        this.intervalSeconds,
        this.intervalSeconds,
        TimeUnit.SECONDS);

    this.plugin.getLogger().info("Rendering world changes every " + this.intervalSeconds + "s");
  }

  @Override
  public void stop() {
    this.scheduler.shutdownNow();
    flush();
  }

  private void flush() {
    if (this.pending.isEmpty()) {
      return;
    }

    final @NotNull List<TileCoordinate> batch = List.copyOf(this.pending);
    this.pending.removeAll(batch);
    this.renderQueue.enqueueAll(batch, RenderPriority.LIVE_ZOOM_0);
  }
}

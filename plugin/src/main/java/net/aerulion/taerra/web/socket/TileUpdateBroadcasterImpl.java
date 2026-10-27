package net.aerulion.taerra.web.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.model.TileGeometry;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileUpdateBroadcasterImpl implements TileUpdateBroadcaster {

  private static final int MAX_TILES_PER_MESSAGE = 256;

  private static final long MIN_BULK_INTERVAL_MILLIS = 5_000L;
  private static final long FLUSH_INTERVAL_MILLIS = 500L;
  private final @NotNull Plugin plugin;
  private final @NotNull MapWebSocketHandler wsHandler;
  private final @NotNull ObjectMapper mapper;
  private final @NotNull Map<String, Set<TileCoordinate>> pendingByWorld;
  private final @NotNull Map<String, Long> lastBulkByWorld;
  private final @NotNull ScheduledExecutorService scheduler;

  public TileUpdateBroadcasterImpl(
      final @NotNull Plugin plugin, final @NotNull MapWebSocketHandler wsHandler) {
    super();
    this.plugin = plugin;
    this.wsHandler = wsHandler;
    this.mapper = new ObjectMapper();
    this.pendingByWorld = new ConcurrentHashMap<>();
    this.lastBulkByWorld = new ConcurrentHashMap<>();
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("Taerra-TileUpdates").daemon().factory());
  }

  @Override
  public void tileUpdated(final @NotNull TileCoordinate coord) {
    if (this.wsHandler.clientCount(coord.world()) == 0) {
      return;
    }

    this.pendingByWorld.compute(
        coord.world(),
        (world, pending) -> {
          final @NotNull Set<TileCoordinate> tiles = pending == null ? new HashSet<>() : pending;
          tiles.add(coord);
          return tiles;
        });
  }

  @Override
  public void start() {
    this.scheduler.scheduleWithFixedDelay(
        () -> {
          try {
            flush();
          } catch (final Throwable e) {
            this.plugin
                .getLogger()
                .log(Level.WARNING, "Failed to send tile updates: " + e.getMessage(), e);
          }
        },
        FLUSH_INTERVAL_MILLIS,
        FLUSH_INTERVAL_MILLIS,
        TimeUnit.MILLISECONDS);
  }

  @Override
  public void stop() {
    this.scheduler.shutdownNow();
    this.pendingByWorld.clear();
    this.lastBulkByWorld.clear();
  }

  private void flush() {
    for (final @NotNull String world : List.copyOf(this.pendingByWorld.keySet())) {
      final @Nullable Set<TileCoordinate> tiles = this.pendingByWorld.remove(world);
      if (tiles == null || tiles.isEmpty()) {
        continue;
      }
      if (this.wsHandler.clientCount(world) == 0) {
        continue;
      }

      final @NotNull Map<String, Object> message = SocketMessage.TILE_UPDATED.payload();
      message.put("world", world);

      if (tiles.size() > MAX_TILES_PER_MESSAGE) {
        if (!allowBulk(world)) {

          this.pendingByWorld.merge(
              world,
              tiles,
              (newer, held) -> {
                newer.addAll(held);
                return newer;
              });
          continue;
        }
        message.put("bulk", true);
      } else {

        final @NotNull List<Object[]> payload = new ArrayList<>(tiles.size());
        for (final @NotNull TileCoordinate coord : tiles) {
          payload.add(
              new Object[] {
                TileGeometry.toLeafletZoom(coord.zoom()),
                coord.x(),
                coord.z(),
                coord.view().id(),
                coord.rotation().index()
              });
        }
        message.put("tiles", payload);
      }

      try {
        this.wsHandler.broadcastToWorld(world, this.mapper.writeValueAsString(message));
      } catch (final Exception e) {
        this.plugin
            .getLogger()
            .log(Level.WARNING, "Failed to serialize tile updates for " + world, e);
      }
    }
  }

  private boolean allowBulk(final @NotNull String world) {
    final long now = System.currentTimeMillis();
    final @Nullable Long lastBulk = this.lastBulkByWorld.get(world);
    if (lastBulk != null && now - lastBulk < MIN_BULK_INTERVAL_MILLIS) {
      return false;
    }
    this.lastBulkByWorld.put(world, now);
    return true;
  }
}

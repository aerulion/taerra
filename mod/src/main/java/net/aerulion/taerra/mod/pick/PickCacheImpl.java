package net.aerulion.taerra.mod.pick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;
import net.aerulion.taerra.mod.tile.TileKey;
import net.aerulion.taerra.mod.util.Urls;
import net.aerulion.taerra.mod.web.WebClient;
import net.aerulion.taerra.mod.web.model.MapConfig;
import net.aerulion.taerra.mod.web.model.Marker;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PickCacheImpl implements PickCache {

  private static final int MAX_BUFFERS = 256;
  private static final int MAX_IN_FLIGHT = 6;
  private static final int MAX_EDGE_SAMPLES = 512;
  private static final long RETRY_MILLIS = 5_000;

  private final WebClient client;
  private final Supplier<@Nullable MapConfig> config;
  private final Set<String> loading = new HashSet<>();
  private final Map<String, Ground> grounds = new HashMap<>();

  private final LinkedHashMap<String, Pick> buffers =
      new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<String, Pick> eldest) {
          return size() > MAX_BUFFERS;
        }
      };

  public PickCacheImpl(final WebClient client, final Supplier<@Nullable MapConfig> config) {
    this.client = client;
    this.config = config;
  }

  private static @NotNull String topKey(final String world, final int x, final int z) {
    return "top:" + world + ":" + x + ":" + z;
  }

  private static @NotNull String isoKey(
      final String world, final int rotation, final int x, final int y) {
    return "iso:" + world + ":" + rotation + ":" + x + ":" + y;
  }

  private static double[] smoothed(final double @NotNull [] heights) {
    final double @NotNull [] smoothed = new double[heights.length];
    for (int i = 0; i < heights.length; i++) {
      final double[] window =
          Arrays.stream(heights, Math.max(0, i - 2), Math.min(heights.length, i + 3))
              .filter(Double::isFinite)
              .sorted()
              .toArray();
      smoothed[i] = window.length > 0 ? window[window.length >> 1] : heights[i];
    }
    return smoothed;
  }

  private @Nullable Optional<PickBuffer> buffer(final String key, final String path) {
    final Pick entry = this.buffers.get(key);
    final boolean wanted =
        entry == null || (entry.stale && System.currentTimeMillis() >= entry.retryAt);
    if (wanted && !this.loading.contains(key) && this.loading.size() < MAX_IN_FLIGHT) {
      this.loading.add(key);
      this.client
          .get(path, entry == null ? null : entry.etag)
          .handle(
              (response, error) -> {
                final int status = error == null ? response.statusCode() : 0;
                final @Nullable PickBuffer decoded =
                    status == 200 ? PickBuffer.decode(response.body()) : null;
                final @Nullable String etag =
                    status == 200 ? response.headers().firstValue("ETag").orElse(null) : null;
                Minecraft.getInstance().execute(() -> loaded(key, status, decoded, etag));
                return null;
              });
    }
    return entry == null ? null : entry.buffer;
  }

  private void loaded(
      final String key,
      final int status,
      final @Nullable PickBuffer decoded,
      final @Nullable String etag) {
    this.loading.remove(key);
    final Pick entry = this.buffers.get(key);
    if (status == 200 || status == 404) {
      this.buffers.put(key, new Pick(Optional.ofNullable(decoded), etag));
    } else if (status == 304 && entry != null) {
      entry.stale = false;
    } else if (entry != null) {
      entry.retryAt = System.currentTimeMillis() + RETRY_MILLIS;
    }
  }

  @Override
  public @Nullable OptionalInt surface(final @NotNull String world, final int x, final int z) {
    final @Nullable MapConfig config = this.config.get();
    if (config == null) {
      return null;
    }
    final int size = config.tileSize();
    final int tileX = Math.floorDiv(x, size);
    final int tileZ = Math.floorDiv(z, size);
    final @Nullable Optional<PickBuffer> buffer =
        buffer(
            topKey(world, tileX, tileZ),
            "/api/tiles/%s/%d/%d.pick".formatted(Urls.segment(world), tileX, tileZ));
    if (buffer == null) {
      return null;
    }
    final int @Nullable [] cell =
        buffer.map(pick -> pick.cell(x - tileX * size, z - tileZ * size)).orElse(null);
    return cell == null ? OptionalInt.empty() : OptionalInt.of(cell[1]);
  }

  @Override
  public int @Nullable [] isometric(
      final @NotNull String world, final int rotation, final int screenX, final int screenY) {
    final @Nullable MapConfig config = this.config.get();
    if (config == null) {
      return null;
    }
    final int size = config.isometric().tileSize();
    final int tileX = Math.floorDiv(screenX, size);
    final int tileY = Math.floorDiv(screenY, size);
    final @Nullable Optional<PickBuffer> buffer =
        buffer(
            isoKey(world, rotation, tileX, tileY),
            "/api/iso/%s/%d/%d/%d.pick".formatted(Urls.segment(world), rotation, tileX, tileY));
    final int @Nullable [] cell =
        buffer == null
            ? null
            : buffer
                .map(pick -> pick.cell(screenX - tileX * size, screenY - tileY * size))
                .orElse(null);
    return cell == null
        ? null
        : PickBuffer.isometricBlock(
            rotation, config.isometric().blockWidth(), screenX, cell[0], cell[1]);
  }

  @Override
  public @Nullable List<List<double[]>> ground(
      final String key, final @NotNull String world, final @NotNull Marker marker) {
    final @Nullable MapConfig config = this.config.get();
    if (config == null) {
      return null;
    }
    return this.grounds
        .computeIfAbsent(key, _ -> new Ground(world, marker.points(), config.tileSize()))
        .sample();
  }

  @Override
  public @Nullable List<List<double[]>> drape(
      final @NotNull String world, final @NotNull List<double[]> outline) {
    final @Nullable MapConfig config = this.config.get();
    if (config == null) {
      return null;
    }
    return new Ground(
            world,
            outline.stream().map(point -> List.of(point[0], point[1])).toList(),
            config.tileSize())
        .sample();
  }

  @Override
  public void refresh(final @NotNull TileKey tile) {
    final @Nullable MapConfig config = this.config.get();
    if (config == null) {
      return;
    }
    if (tile.isometric() && tile.zoom() == config.isometric().maxZoom()) {
      stale(isoKey(tile.world(), tile.rotation(), tile.x(), tile.y()));
    } else if (!tile.isometric() && tile.zoom() == config.maxZoom()) {
      stale(topKey(tile.world(), tile.x(), tile.y()));
    }
  }

  @Override
  public void refreshAll() {
    this.buffers.values().forEach(entry -> entry.stale = true);
  }

  @Override
  public void refreshWorld(final String world) {
    this.buffers.forEach(
        (key, entry) -> {
          if (key.startsWith("top:" + world + ":") || key.startsWith("iso:" + world + ":")) {
            entry.stale = true;
          }
        });
  }

  private void stale(final String key) {
    final Pick entry = this.buffers.get(key);
    if (entry != null) {
      entry.stale = true;
    }
  }

  private static final class Pick {

    private final Optional<PickBuffer> buffer;
    private final @Nullable String etag;
    private boolean stale;
    private long retryAt;

    private Pick(final Optional<PickBuffer> buffer, final @Nullable String etag) {
      this.buffer = buffer;
      this.etag = etag;
    }
  }

  private record Pending(String path, int originX, int originZ, List<int[]> samples) {}

  private final class Ground {

    private final double @NotNull [][][] samples;
    private final double @NotNull [][] heights;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private @Nullable List<List<double[]>> result;

    private Ground(
        final @NotNull String world, final @NotNull List<List<Double>> outline, final int size) {
      double centerX = 0;
      double centerZ = 0;
      for (final @NotNull List<Double> point : outline) {
        centerX += point.get(0) / outline.size();
        centerZ += point.get(1) / outline.size();
      }
      this.samples = new double[outline.size()][][];
      this.heights = new double[outline.size()][];
      for (int side = 0; side < outline.size(); side++) {
        final List<Double> from = outline.get(side);
        final List<Double> to = outline.get((side + 1) % outline.size());
        final double dx = to.get(0) - from.get(0);
        final double dz = to.get(1) - from.get(1);
        final int steps =
            Math.min(MAX_EDGE_SAMPLES, Math.max(1, (int) Math.ceil(Math.hypot(dx, dz))));
        this.samples[side] = new double[steps + 1][];
        this.heights[side] = new double[steps + 1];
        for (int step = 0; step <= steps; step++) {
          final double x = from.get(0) + dx * step / steps;
          final double z = from.get(1) + dz * step / steps;
          this.samples[side][step] = new double[] {x, z};
          final int columnX = (int) Math.floor(x + Math.signum(centerX - x) * .5);
          final int columnZ = (int) Math.floor(z + Math.signum(centerZ - z) * .5);
          final int tileX = Math.floorDiv(columnX, size);
          final int tileZ = Math.floorDiv(columnZ, size);
          this.pending
              .computeIfAbsent(
                  topKey(world, tileX, tileZ),
                  _ ->
                      new Pending(
                          "/api/tiles/%s/%d/%d.pick".formatted(Urls.segment(world), tileX, tileZ),
                          tileX * size,
                          tileZ * size,
                          new ArrayList<>()))
              .samples()
              .add(new int[] {side, step, columnX, columnZ});
        }
      }
    }

    private @Nullable List<List<double[]>> sample() {
      if (this.result != null) {
        return this.result;
      }
      final @NotNull Iterator<Map.Entry<String, Pending>> tiles =
          this.pending.entrySet().iterator();
      while (tiles.hasNext()) {
        final Map.Entry<String, Pending> tile = tiles.next();
        final @Nullable Optional<PickBuffer> buffer = buffer(tile.getKey(), tile.getValue().path());
        if (buffer == null) {
          continue;
        }
        final Pending pending = tile.getValue();
        for (final int[] sample : pending.samples()) {
          final int @Nullable [] cell =
              buffer
                  .map(
                      pick ->
                          pick.cell(sample[2] - pending.originX(), sample[3] - pending.originZ()))
                  .orElse(null);
          this.heights[sample[0]][sample[1]] = cell == null ? Double.NaN : cell[1];
        }
        tiles.remove();
      }
      if (!this.pending.isEmpty()) {
        return null;
      }
      final @NotNull List<List<double[]>> sides = new ArrayList<>();
      for (int side = 0; side < this.samples.length; side++) {
        final double @NotNull [] ground = smoothed(this.heights[side]);
        final @NotNull List<double[]> points = new ArrayList<>();
        for (int step = 0; step < ground.length; step++) {
          final double[] sample = this.samples[side][step];
          points.add(new double[] {sample[0], ground[step] + 1, sample[1]});
        }
        final @NotNull List<double[]> levelled = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
          if (i == 0
              || i == points.size() - 1
              || points.get(i)[1] != points.get(i - 1)[1]
              || points.get(i)[1] != points.get(i + 1)[1]) {
            levelled.add(points.get(i));
          }
        }
        sides.add(levelled);
      }
      this.result = sides;
      return sides;
    }
  }
}

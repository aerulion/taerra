package net.aerulion.taerra.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.storage.TileStore.Kind;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileStorageImpl implements TileStorage {

  private static final long RETRY_MILLIS = 60_000;

  private final @NotNull Logger logger;
  private final @NotNull File dataDirectory;
  private final @NotNull Cache<LayerKey, EncodedTile> encodedTiles;
  private final @NotNull Cache<TileCoordinate, Map<Integer, Set<FogView>>> layers;
  private volatile @NotNull TileStore store;
  private volatile @Nullable TileStore previous;
  private volatile @NotNull StorageSettings settings;
  private final @NotNull Set<Record> dropped = ConcurrentHashMap.newKeySet();
  private volatile boolean closed;

  TileStorageImpl(
      final @NotNull TileStore store,
      final @NotNull StorageSettings settings,
      final @NotNull File dataDirectory,
      final @NotNull Logger logger,
      final int maxEncodedTiles) {
    super();
    this.logger = logger;
    this.dataDirectory = dataDirectory;
    this.store = store;
    this.settings = settings;
    this.encodedTiles = Caffeine.newBuilder().maximumSize(Math.max(64, maxEncodedTiles)).build();
    this.layers = Caffeine.newBuilder().maximumSize(Math.max(64, maxEncodedTiles)).build();
  }

  public static @NotNull TileStorageImpl open(
      final @NotNull Plugin plugin, final int maxEncodedTiles) throws IOException {
    final @NotNull StorageSettings settings = StorageSettings.of(plugin.getConfig());
    return new TileStorageImpl(
        store(settings, plugin.getDataFolder(), plugin.getLogger()),
        settings,
        plugin.getDataFolder(),
        plugin.getLogger(),
        maxEncodedTiles);
  }

  private static @NotNull TileStore store(
      final @NotNull StorageSettings settings,
      final @NotNull File dataDirectory,
      final @NotNull Logger logger)
      throws IOException {
    return switch (settings.type()) {
      case StorageSettings.FILES -> new FileTileStore(dataDirectory, logger);
      case "mariadb", "mysql" -> SqlTileStore.open(settings, logger);
      default ->
          throw new IOException("storage.type is files, mariadb or mysql, not " + settings.type());
    };
  }

  private static boolean sameDatabase(
      final @NotNull StorageSettings a, final @NotNull StorageSettings b) {
    return a.database()
        && b.database()
        && a.host().equals(b.host())
        && a.port() == b.port()
        && a.name().equals(b.name());
  }

  @Override
  public @NotNull StorageSettings settings() {
    return this.settings;
  }

  @Override
  public boolean moving() {
    return this.previous != null;
  }

  @Override
  public @NotNull Prepared prepare(final @NotNull StorageSettings settings) throws IOException {
    return new Prepared(store(settings, this.dataDirectory, this.logger), settings);
  }

  @Override
  public void move(final @NotNull Prepared prepared, final @NotNull Set<String> worlds) {
    prepared.moved = true;
    final @NotNull TileStore from = this.store;
    final @NotNull StorageSettings before = this.settings;
    this.previous = from;
    this.store = prepared.store;
    this.settings = prepared.settings;
    if (sameDatabase(before, prepared.settings)) {
      this.logger.info("Reconnected the tile storage to " + prepared.settings);
      drain(from, before, Set.of());
    } else {
      this.logger.info("Moving tiles from " + before + " to " + prepared.settings);
      drain(from, before, worlds);
    }
  }

  @Override
  public void resume(final @NotNull Set<String> worlds) {
    if (!this.settings.database()) {
      return;
    }
    final @NotNull FileTileStore files = new FileTileStore(this.dataDirectory, this.logger);
    if (files.holds(worlds)) {
      this.previous = files;
      this.logger.info("Moving the tiles still in files to " + this.settings);
      drain(files, StorageSettings.of(new YamlConfiguration()), worlds);
    }
  }

  private void drain(
      final @NotNull TileStore from,
      final @NotNull StorageSettings before,
      final @NotNull Set<String> worlds) {
    final @NotNull TileStore to = this.store;
    final @NotNull StorageSettings target = this.settings;
    Thread.ofPlatform()
        .name("Taerra-Move")
        .daemon()
        .start(
            () -> {
              final long started = System.currentTimeMillis();
              final int[] copied = {0};
              while (!this.closed) {
                try {
                  from.forEach(
                      worlds,
                      (coord, layer, kind, data, modified) -> {
                        synchronized (this.dropped) {
                          if (!dropped(coord, layer)
                              && to.writeIfNewer(coord, layer, kind, data, modified)) {
                            copied[0]++;
                          }
                        }
                      });
                  // only files are deleted: two database settings can name the same database
                  if (from instanceof FileTileStore) {
                    for (final @NotNull String world : worlds) {
                      for (final @NotNull MapView view : MapView.values()) {
                        from.deleteView(world, view);
                      }
                    }
                  }
                  break;
                } catch (final IOException | RuntimeException e) {
                  if (this.closed) {
                    return;
                  }
                  this.logger.warning(
                      "Failed to move tiles to "
                          + target
                          + " after "
                          + copied[0]
                          + ": "
                          + e.getMessage()
                          + ". Trying again in a minute, the rest stay readable from "
                          + before
                          + " meanwhile");
                  try {
                    Thread.sleep(RETRY_MILLIS);
                  } catch (final InterruptedException interrupted) {
                    return;
                  }
                }
              }
              if (this.closed) {
                return;
              }
              this.previous = null;
              this.dropped.clear();
              from.close();
              if (!worlds.isEmpty()) {
                this.logger.info(
                    "Moved "
                        + copied[0]
                        + " tiles to "
                        + target
                        + " in "
                        + (System.currentTimeMillis() - started) / 1000
                        + "s");
              }
            });
  }

  private boolean dropped(final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    return this.dropped.contains(new LayerKey(coord, layer))
        || this.dropped.contains(new ViewKey(coord.world(), coord.view()))
        || !layer.base() && this.dropped.contains(new LayersKey(coord.world()));
  }

  private byte @Nullable [] read(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer, final @NotNull Kind kind)
      throws IOException {
    final byte @Nullable [] data = this.store.read(coord, layer, kind);
    final @Nullable TileStore previous = this.previous;
    return data != null || previous == null ? data : previous.read(coord, layer, kind);
  }

  private static @NotNull TileCoordinate column(final @NotNull TileCoordinate coord) {
    return coord.at(coord.zoom(), coord.x(), 0);
  }

  @Override
  public @NotNull Saved saveTile(
      final @NotNull TileCoordinate coord, final @NotNull BufferedImage image) throws IOException {
    return saveTile(coord, FogView.BASE, image);
  }

  @Override
  public @NotNull Saved saveTile(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull BufferedImage image)
      throws IOException {
    final byte @NotNull [] encoded = ImageUtils.encodePNG(image);
    final @NotNull EncodedTile tile = new EncodedTile(encoded, EncodedTile.eTagOf(encoded));
    final @NotNull Optional<EncodedTile> stored = readEncodedTile(coord, layer);
    if (stored.map(found -> found.eTag().equals(tile.eTag())).orElse(false)) {
      try {
        this.store.touch(coord, layer);
        return Saved.UNCHANGED;
      } catch (final IOException e) {
      }
    }
    this.store.write(coord, layer, Kind.TILE, encoded);
    this.encodedTiles.put(new LayerKey(coord, layer), tile);
    if (!layer.base()) {
      final @Nullable Map<Integer, Set<FogView>> column = this.layers.getIfPresent(column(coord));
      if (column != null) {
        column.computeIfAbsent(coord.z(), _ -> ConcurrentHashMap.newKeySet()).add(layer);
      }
    }
    return stored.isEmpty() ? Saved.CREATED : Saved.CHANGED;
  }

  @Override
  public boolean deleteTile(final @NotNull TileCoordinate coord) {
    for (final @NotNull FogView layer : layers(coord)) {
      deleteLayer(coord, layer);
    }
    return deleteLayer(coord, FogView.BASE);
  }

  @Override
  public boolean deleteLayer(final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    try {
      final @NotNull TileStore store = this.store;
      final @Nullable TileStore previous = this.previous;
      if (previous == null) {
        return store.delete(coord, layer);
      }
      synchronized (this.dropped) {
        this.dropped.add(new LayerKey(coord, layer));
        final boolean deleted = store.delete(coord, layer);
        return previous.delete(coord, layer) || deleted;
      }
    } catch (final IOException e) {
      this.logger.warning("Failed to delete tile " + coord + ": " + e.getMessage());
      return false;
    } finally {
      this.encodedTiles.invalidate(new LayerKey(coord, layer));
      final @Nullable Map<Integer, Set<FogView>> column =
          layer.base() ? null : this.layers.getIfPresent(column(coord));
      final @Nullable Set<FogView> stored = column == null ? null : column.get(coord.z());
      if (stored != null) {
        stored.remove(layer);
      }
    }
  }

  @Override
  public void deleteView(final @NotNull String worldName, final @NotNull MapView view)
      throws IOException {
    try {
      final @NotNull TileStore store = this.store;
      final @Nullable TileStore previous = this.previous;
      if (previous == null) {
        store.deleteView(worldName, view);
      } else {
        synchronized (this.dropped) {
          this.dropped.add(new ViewKey(worldName, view));
          store.deleteView(worldName, view);
          previous.deleteView(worldName, view);
        }
      }
    } finally {
      this.encodedTiles
          .asMap()
          .keySet()
          .removeIf(key -> key.coord().view() == view && key.coord().world().equals(worldName));
      this.layers.invalidateAll();
    }
  }

  @Override
  public @NotNull List<FogView> layers(final @NotNull TileCoordinate coord) {
    final @Nullable Map<Integer, Set<FogView>> column =
        this.layers.get(column(coord), this::loadLayers);
    final @Nullable Set<FogView> stored = column == null ? null : column.get(coord.z());
    if (stored == null || stored.isEmpty()) {
      return List.of();
    }
    final @NotNull List<FogView> found = new ArrayList<>(stored);
    found.sort(FogView.ORDER);
    return List.copyOf(found);
  }

  private @Nullable Map<Integer, Set<FogView>> loadLayers(final @NotNull TileCoordinate column) {
    try {
      final @NotNull Map<Integer, Set<FogView>> found = this.store.layers(column);
      final @Nullable TileStore previous = this.previous;
      if (previous != null) {
        previous
            .layers(column)
            .forEach(
                (z, stored) ->
                    found.computeIfAbsent(z, _ -> ConcurrentHashMap.newKeySet()).addAll(stored));
      }
      return found;
    } catch (final IOException e) {
      this.logger.warning("Failed to list the layers of " + column + ": " + e.getMessage());
      return null;
    }
  }

  @Override
  public void deleteLayers(final @NotNull String worldName) throws IOException {
    final @NotNull TileStore store = this.store;
    final @Nullable TileStore previous = this.previous;
    if (previous == null) {
      store.deleteLayers(worldName);
    } else {
      synchronized (this.dropped) {
        this.dropped.add(new LayersKey(worldName));
        store.deleteLayers(worldName);
        previous.deleteLayers(worldName);
      }
    }
    this.encodedTiles.asMap().keySet().removeIf(key -> !key.layer().base());
    this.layers.invalidateAll();
  }

  @Override
  public @NotNull List<TileCoordinate> tilesAt(final @NotNull TileCoordinate level) {
    try {
      final @Nullable TileStore previous = this.previous;
      return previous == null
          ? this.store.tilesAt(level)
          : Stream.concat(this.store.tilesAt(level).stream(), previous.tilesAt(level).stream())
              .distinct()
              .toList();
    } catch (final IOException e) {
      this.logger.warning("Failed to list the tiles at " + level + ": " + e.getMessage());
      return List.of();
    }
  }

  @Override
  public @NotNull List<TileCoordinate> tilesOlderThan(
      final @NotNull String worldName, final long cutoffMillis) throws IOException {
    final @Nullable TileStore previous = this.previous;
    return previous == null
        ? this.store.tilesOlderThan(worldName, cutoffMillis)
        : Stream.concat(
                this.store.tilesOlderThan(worldName, cutoffMillis).stream(),
                previous.tilesOlderThan(worldName, cutoffMillis).stream())
            .distinct()
            .toList();
  }

  @Override
  public long lastModified(final @NotNull TileCoordinate coord) throws IOException {
    final @Nullable TileStore previous = this.previous;
    return Math.max(
        this.store.lastModified(coord), previous == null ? 0 : previous.lastModified(coord));
  }

  @Override
  public @NotNull Optional<EncodedTile> readEncodedTile(final @NotNull TileCoordinate coord) {
    return readEncodedTile(coord, FogView.BASE);
  }

  @Override
  public @NotNull Optional<EncodedTile> readEncodedTile(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    return Optional.ofNullable(
        this.encodedTiles.get(new LayerKey(coord, layer), this::loadEncodedTile));
  }

  private @Nullable EncodedTile loadEncodedTile(final @NotNull LayerKey key) {
    try {
      final byte @Nullable [] data = read(key.coord(), key.layer(), Kind.TILE);
      return data == null ? null : new EncodedTile(data, EncodedTile.eTagOf(data));
    } catch (final IOException e) {
      this.logger.warning("Failed to read tile " + key.coord() + ": " + e.getMessage());
      return null;
    }
  }

  @Override
  public @NotNull Optional<BufferedImage> loadTile(final @NotNull TileCoordinate coord) {
    return loadTile(coord, FogView.BASE);
  }

  @Override
  public @NotNull Optional<BufferedImage> loadTile(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    try {
      final byte @Nullable [] data = read(coord, layer, Kind.TILE);
      return data == null ? Optional.empty() : Optional.ofNullable(ImageUtils.loadPNG(data));
    } catch (final IOException | RuntimeException e) {
      this.logger.warning("Failed to load tile " + coord + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void savePick(final @NotNull TileCoordinate coord, final byte @NotNull [] data)
      throws IOException {
    savePick(coord, FogView.BASE, data);
  }

  @Override
  public void savePick(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final byte @NotNull [] data)
      throws IOException {
    this.store.write(coord, layer, Kind.PICK, data);
  }

  @Override
  public @NotNull Optional<byte[]> readPick(final @NotNull TileCoordinate coord) {
    return readPick(coord, FogView.BASE);
  }

  @Override
  public @NotNull Optional<byte[]> readPick(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    try {
      final byte @Nullable [] data = read(coord, layer, Kind.PICK);
      if (data == null) {
        return Optional.empty();
      }
      if (data.length > 4 && data[4] != PickBuffer.FORMAT_VERSION) {
        final @Nullable PickBuffer legacy = PickBuffer.decode(data);
        final byte @Nullable [] upgraded = legacy == null ? null : legacy.encode();
        if (upgraded == null) {
          return Optional.empty();
        }
        this.store.write(coord, layer, Kind.PICK, upgraded);
        return Optional.of(upgraded);
      }
      return Optional.of(data);
    } catch (final IOException e) {
      this.logger.warning("Failed to read pick buffer for " + coord + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public void resize(final int maxEncodedTiles) {
    this.encodedTiles
        .policy()
        .eviction()
        .ifPresent(eviction -> eviction.setMaximum(Math.max(64, maxEncodedTiles)));
    this.layers
        .policy()
        .eviction()
        .ifPresent(eviction -> eviction.setMaximum(Math.max(64, maxEncodedTiles)));
  }

  @Override
  public void close() {
    this.closed = true;
    this.store.close();
    final @Nullable TileStore previous = this.previous;
    if (previous != null) {
      previous.close();
    }
  }

  private record LayerKey(@NotNull TileCoordinate coord, @NotNull FogView layer) {}

  private record ViewKey(@NotNull String world, @NotNull MapView view) {}

  private record LayersKey(@NotNull String world) {}
}

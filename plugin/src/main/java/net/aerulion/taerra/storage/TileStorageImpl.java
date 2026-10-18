package net.aerulion.taerra.storage;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.rendering.tile.PickBuffer;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileStorageImpl implements TileStorage {

  private final @NotNull Plugin plugin;
  private final @NotNull File dataDirectory;
  private final @NotNull Cache<LayerKey, EncodedTile> encodedTiles;
  private final @NotNull Cache<File, Map<Integer, Set<FogView>>> layers;

  public TileStorageImpl(final @NotNull Plugin plugin, final int maxEncodedTiles) {
    super();
    this.plugin = plugin;
    this.dataDirectory = plugin.getDataFolder();
    for (final @NotNull MapView view : MapView.values()) {
      new File(this.dataDirectory, view.directoryName()).mkdirs();
    }
    this.encodedTiles = Caffeine.newBuilder().maximumSize(Math.max(64, maxEncodedTiles)).build();
    this.layers = Caffeine.newBuilder().maximumSize(Math.max(64, maxEncodedTiles)).build();
  }

  private static @NotNull String sanitizeWorldName(final @NotNull String worldName) {
    return worldName.replaceAll("[^a-zA-Z0-9_-]", "_");
  }

  private static @Nullable Integer parseInt(final @NotNull String value) {
    try {
      return Integer.parseInt(value);
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  static @Nullable TileCoordinate parseTile(
      final @NotNull String worldName, final @NotNull MapView view, final @NotNull Path relative) {
    final int offset = view.rotated() ? 1 : 0;
    if (relative.getNameCount() != 3 + offset) {
      return null;
    }
    try {
      final @Nullable MapRotation rotation =
          view.rotated()
              ? MapRotation.ofIndex(Integer.parseInt(relative.getName(0).toString()))
              : MapRotation.R0;
      final @NotNull String file = relative.getName(2 + offset).toString();
      return rotation == null
          ? null
          : new TileCoordinate(
              worldName,
              view,
              rotation,
              Integer.parseInt(relative.getName(offset).toString()),
              Integer.parseInt(relative.getName(1 + offset).toString()),
              Integer.parseInt(file.substring(0, file.length() - ".png".length())));
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  private @NotNull File pickFile(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    return new File(tileFile(coord).getParentFile(), coord.z() + suffix(layer) + ".pick");
  }

  private static @NotNull String suffix(final @NotNull FogView layer) {
    return layer.base() ? "" : "." + layer.id();
  }

  @Override
  public @NotNull File tileFile(final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    return layer.base()
        ? tileFile(coord)
        : new File(tileFile(coord).getParentFile(), coord.z() + suffix(layer) + ".png");
  }

  @Override
  public @NotNull File tileFile(final @NotNull TileCoordinate coord) {

    final @NotNull String rotationSegment =
        coord.view().rotated() ? coord.rotation().id() + "/" : "";
    return new File(
        this.dataDirectory,
        String.format(
            "%s/%s/%s%d/%d/%d.png",
            coord.view().directoryName(),
            sanitizeWorldName(coord.world()),
            rotationSegment,
            coord.zoom(),
            coord.x(),
            coord.z()));
  }

  @Override
  public boolean saveTile(final @NotNull TileCoordinate coord, final @NotNull BufferedImage image)
      throws IOException {
    return saveTile(coord, FogView.BASE, image);
  }

  @Override
  public boolean saveTile(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull BufferedImage image)
      throws IOException {
    final byte @NotNull [] encoded = ImageUtils.encodePNG(image);
    final @NotNull EncodedTile tile = new EncodedTile(encoded, EncodedTile.eTagOf(encoded));
    final @NotNull File file = tileFile(coord, layer);
    if (readEncodedTile(coord, layer)
        .map(stored -> stored.eTag().equals(tile.eTag()))
        .orElse(false)) {
      try {
        Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(System.currentTimeMillis()));
        return false;
      } catch (final IOException e) {
      }
    }
    final boolean created = !layer.base() && !file.exists();
    AtomicFiles.write(file, encoded);
    this.encodedTiles.put(new LayerKey(coord, layer), tile);
    if (created) {
      final @Nullable Map<Integer, Set<FogView>> column =
          this.layers.getIfPresent(tileFile(coord).getParentFile());
      if (column != null) {
        column.computeIfAbsent(coord.z(), _ -> ConcurrentHashMap.newKeySet()).add(layer);
      }
    }
    return true;
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
      Files.deleteIfExists(pickFile(coord, layer).toPath());
      return Files.deleteIfExists(tileFile(coord, layer).toPath());
    } catch (final IOException e) {
      this.plugin.getLogger().warning("Failed to delete tile " + coord + ": " + e.getMessage());
      return false;
    } finally {
      this.encodedTiles.invalidate(new LayerKey(coord, layer));
      final @Nullable Map<Integer, Set<FogView>> column =
          layer.base() ? null : this.layers.getIfPresent(tileFile(coord).getParentFile());
      final @Nullable Set<FogView> stored = column == null ? null : column.get(coord.z());
      if (stored != null) {
        stored.remove(layer);
      }
    }
  }

  @Override
  public @NotNull List<FogView> layers(final @NotNull TileCoordinate coord) {
    final @Nullable Set<FogView> stored =
        this.layers
            .get(tileFile(coord).getParentFile(), TileStorageImpl::scanLayers)
            .get(coord.z());
    if (stored == null || stored.isEmpty()) {
      return List.of();
    }
    final @NotNull List<FogView> found = new ArrayList<>(stored);
    found.sort(Comparator.comparingInt(FogView::region).thenComparing(FogView::kind));
    return List.copyOf(found);
  }

  private static @NotNull Map<Integer, Set<FogView>> scanLayers(final @NotNull File column) {
    final @NotNull Map<Integer, Set<FogView>> found = new ConcurrentHashMap<>();
    final @Nullable String @Nullable [] names = column.list((dir, name) -> name.endsWith(".png"));
    for (final @Nullable String name : names == null ? new String[0] : names) {
      final int dot = name == null ? -1 : name.indexOf('.');
      if (dot < 0 || dot == name.length() - ".png".length()) {
        continue;
      }
      final @Nullable Integer z = parseInt(name.substring(0, dot));
      final @Nullable FogView layer =
          FogView.parse(name.substring(dot + 1, name.length() - ".png".length()));
      if (z != null && layer != null && !layer.base()) {
        found.computeIfAbsent(z, _ -> ConcurrentHashMap.newKeySet()).add(layer);
      }
    }
    return found;
  }

  @Override
  public @NotNull List<TileCoordinate> tilesAt(final @NotNull TileCoordinate level) {
    final @NotNull List<TileCoordinate> tiles = new ArrayList<>();
    final @Nullable File @Nullable [] columns =
        tileFile(level).getParentFile().getParentFile().listFiles(File::isDirectory);
    for (final @Nullable File column : columns == null ? new File[0] : columns) {
      final @Nullable Integer x = parseInt(column.getName());
      final @Nullable String @Nullable [] names = column.list((dir, name) -> name.endsWith(".png"));
      if (x == null || names == null) {
        continue;
      }
      for (final @Nullable String name : names) {
        final @Nullable Integer z = parseInt(name.substring(0, name.length() - ".png".length()));
        if (z != null) {
          tiles.add(level.at(level.zoom(), x, z));
        }
      }
    }
    return tiles;
  }

  @Override
  public @NotNull List<TileCoordinate> tilesOlderThan(
      final @NotNull String worldName, final long cutoffMillis) throws IOException {
    final @NotNull List<TileCoordinate> stale = new ArrayList<>();
    for (final @NotNull MapView view : MapView.values()) {
      final @NotNull Path directory =
          this.dataDirectory
              .toPath()
              .resolve(view.directoryName())
              .resolve(sanitizeWorldName(worldName));
      if (!Files.isDirectory(directory)) {
        continue;
      }
      try (final @NotNull Stream<Path> paths = Files.walk(directory)) {
        for (final @NotNull Path path : (Iterable<Path>) paths::iterator) {
          if (!path.getFileName().toString().endsWith(".png")
              || Files.getLastModifiedTime(path).toMillis() >= cutoffMillis) {
            continue;
          }
          final @Nullable TileCoordinate coord =
              parseTile(worldName, view, directory.relativize(path));
          if (coord != null) {
            stale.add(coord);
          }
        }
      }
    }
    return stale;
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
    final @NotNull File file = tileFile(key.coord(), key.layer());
    if (!file.exists()) {
      return null;
    }

    try {
      final byte @NotNull [] data = Files.readAllBytes(file.toPath());
      return new EncodedTile(data, EncodedTile.eTagOf(data));
    } catch (final IOException e) {
      this.plugin.getLogger().warning("Failed to read tile " + key.coord() + ": " + e.getMessage());
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
    final @NotNull File file = tileFile(coord, layer);

    if (!file.exists()) {
      return Optional.empty();
    }

    try {
      final @Nullable BufferedImage image = ImageUtils.loadPNG(file);
      return Optional.ofNullable(image);
    } catch (final IOException | RuntimeException e) {
      this.plugin.getLogger().warning("Failed to load tile " + coord + ": " + e.getMessage());
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
    AtomicFiles.write(pickFile(coord, layer), data);
  }

  @Override
  public @NotNull Optional<byte[]> readPick(final @NotNull TileCoordinate coord) {
    return readPick(coord, FogView.BASE);
  }

  @Override
  public @NotNull Optional<byte[]> readPick(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    final @NotNull File file = pickFile(coord, layer);
    if (!file.exists()) {
      return Optional.empty();
    }
    try {
      final byte @NotNull [] data = Files.readAllBytes(file.toPath());
      if (data.length > 4 && data[4] != PickBuffer.FORMAT_VERSION) {
        final @Nullable PickBuffer legacy = PickBuffer.decode(data);
        final byte @Nullable [] upgraded = legacy == null ? null : legacy.encode();
        if (upgraded == null) {
          return Optional.empty();
        }
        AtomicFiles.write(file, upgraded);
        return Optional.of(upgraded);
      }
      return Optional.of(data);
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .warning("Failed to read pick buffer for " + coord + ": " + e.getMessage());
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

  private record LayerKey(@NotNull TileCoordinate coord, @NotNull FogView layer) {}
}

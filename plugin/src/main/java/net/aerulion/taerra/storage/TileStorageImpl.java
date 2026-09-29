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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.util.AtomicFiles;
import net.aerulion.taerra.util.ImageUtils;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TileStorageImpl implements TileStorage {

  private final @NotNull Plugin plugin;
  private final @NotNull File dataDirectory;
  private final @NotNull Cache<TileCoordinate, EncodedTile> encodedTiles;

  public TileStorageImpl(final @NotNull Plugin plugin, final int maxEncodedTiles) {
    super();
    this.plugin = plugin;
    this.dataDirectory = plugin.getDataFolder();
    for (final @NotNull MapView view : MapView.values()) {
      new File(this.dataDirectory, view.directoryName()).mkdirs();
    }
    this.encodedTiles = Caffeine.newBuilder().maximumSize(Math.max(64, maxEncodedTiles)).build();
  }

  private static @NotNull String computeETag(final byte @NotNull [] data) {
    long hash = 0xcbf29ce484222325L;
    for (final byte value : data) {
      hash ^= value & 0xFF;
      hash *= 0x100000001b3L;
    }
    return "\"" + Integer.toHexString(data.length) + '-' + Long.toHexString(hash) + '"';
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

  private @NotNull File pickFile(final @NotNull TileCoordinate coord) {
    final @NotNull File tile = tileFile(coord);
    return new File(tile.getParentFile(), coord.z() + ".pick");
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
    final byte @NotNull [] encoded = ImageUtils.encodePNG(image);
    final @NotNull EncodedTile tile = new EncodedTile(encoded, computeETag(encoded));
    if (readEncodedTile(coord).map(stored -> stored.eTag().equals(tile.eTag())).orElse(false)) {
      try {
        Files.setLastModifiedTime(
            tileFile(coord).toPath(), FileTime.fromMillis(System.currentTimeMillis()));
        return false;
      } catch (final IOException e) {
      }
    }
    AtomicFiles.write(tileFile(coord), encoded);
    this.encodedTiles.put(coord, tile);
    return true;
  }

  @Override
  public boolean deleteTile(final @NotNull TileCoordinate coord) {
    this.encodedTiles.invalidate(coord);
    try {

      Files.deleteIfExists(pickFile(coord).toPath());
      return Files.deleteIfExists(tileFile(coord).toPath());
    } catch (final IOException e) {
      this.plugin.getLogger().warning("Failed to delete tile " + coord + ": " + e.getMessage());
      return false;
    }
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
    final @Nullable EncodedTile cached = this.encodedTiles.getIfPresent(coord);
    if (cached != null) {
      return Optional.of(cached);
    }

    final @NotNull File file = tileFile(coord);
    if (!file.exists()) {
      return Optional.empty();
    }

    try {
      final byte @NotNull [] data = Files.readAllBytes(file.toPath());
      final @NotNull EncodedTile encodedTile = new EncodedTile(data, computeETag(data));
      this.encodedTiles.put(coord, encodedTile);
      return Optional.of(encodedTile);
    } catch (final IOException e) {
      this.plugin.getLogger().warning("Failed to read tile " + coord + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  @Override
  public @NotNull Optional<BufferedImage> loadTile(final @NotNull TileCoordinate coord) {
    final @NotNull File file = tileFile(coord);

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
    AtomicFiles.write(pickFile(coord), data);
  }

  @Override
  public @NotNull Optional<byte[]> readPick(final @NotNull TileCoordinate coord) {
    final @NotNull File file = pickFile(coord);
    if (!file.exists()) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readAllBytes(file.toPath()));
    } catch (final IOException e) {
      this.plugin
          .getLogger()
          .warning("Failed to read pick buffer for " + coord + ": " + e.getMessage());
      return Optional.empty();
    }
  }
}

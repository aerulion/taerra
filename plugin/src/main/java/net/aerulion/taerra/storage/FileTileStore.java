package net.aerulion.taerra.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Stream;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.util.AtomicFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileTileStore implements TileStore {

  private static final @NotNull String DELETED = ".deleted";

  private final @NotNull Logger logger;
  private final @NotNull File dataDirectory;

  FileTileStore(final @NotNull File dataDirectory, final @NotNull Logger logger) {
    super();
    this.logger = logger;
    this.dataDirectory = dataDirectory;
    for (final @NotNull MapView view : MapView.values()) {
      final @Nullable File @Nullable [] deleted =
          new File(this.dataDirectory, view.directoryName())
              .listFiles(file -> file.getName().endsWith(DELETED));
      for (final @Nullable File trash : deleted == null ? new File[0] : deleted) {
        if (trash != null) {
          deleteLater(trash.toPath());
        }
      }
    }
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

  private @NotNull File column(final @NotNull TileCoordinate coord) {
    final @NotNull String rotationSegment =
        coord.view().rotated() ? coord.rotation().id() + "/" : "";
    return new File(
        this.dataDirectory,
        String.format(
            "%s/%s/%s%d/%d",
            coord.view().directoryName(),
            sanitizeWorldName(coord.world()),
            rotationSegment,
            coord.zoom(),
            coord.x()));
  }

  @NotNull
  File file(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer, final @NotNull Kind kind) {
    return new File(
        column(coord), coord.z() + (layer.base() ? "" : "." + layer.id()) + kind.extension);
  }

  private @NotNull Path worldDirectory(
      final @NotNull String worldName, final @NotNull MapView view) {
    return this.dataDirectory
        .toPath()
        .resolve(view.directoryName())
        .resolve(sanitizeWorldName(worldName));
  }

  private static byte @Nullable [] readIfExists(final @NotNull Path path) throws IOException {
    try {
      return Files.readAllBytes(path);
    } catch (final NoSuchFileException e) {
      return null;
    }
  }

  boolean holds(final @NotNull Set<String> worlds) {
    for (final @NotNull MapView view : MapView.values()) {
      for (final @NotNull String world : worlds) {
        if (Files.isDirectory(worldDirectory(world, view))) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public byte @Nullable [] read(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer, final @NotNull Kind kind)
      throws IOException {
    final @NotNull File file = file(coord, layer, kind);
    return file.exists() ? Files.readAllBytes(file.toPath()) : null;
  }

  @Override
  public void write(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull Kind kind,
      final byte @NotNull [] data)
      throws IOException {
    AtomicFiles.write(file(coord, layer, kind), data);
  }

  @Override
  public boolean writeIfNewer(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull Kind kind,
      final byte @NotNull [] data,
      final long modified)
      throws IOException {
    final @NotNull File file = file(coord, layer, kind);
    if (file.exists() && file.lastModified() >= modified) {
      return false;
    }
    AtomicFiles.write(file, data);
    Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(modified));
    return true;
  }

  @Override
  public void forEach(final @NotNull Set<String> worlds, final @NotNull Visitor visitor)
      throws IOException {
    for (final @NotNull MapView view : MapView.values()) {
      for (final @NotNull String world : worlds) {
        final @NotNull Path directory = worldDirectory(world, view);
        if (!Files.isDirectory(directory)) {
          continue;
        }
        try (final @NotNull Stream<Path> paths = Files.walk(directory)) {
          for (final @NotNull Path path : (Iterable<Path>) paths::iterator) {
            final @NotNull String name = path.getFileName().toString();
            final @Nullable Kind kind =
                name.endsWith(Kind.TILE.extension)
                    ? Kind.TILE
                    : name.endsWith(Kind.PICK.extension) ? Kind.PICK : null;
            final int dot = name.indexOf('.');
            if (kind == null || dot <= 0) {
              continue;
            }
            final @Nullable FogView layer =
                FogView.parse(
                    dot == name.length() - kind.extension.length()
                        ? ""
                        : name.substring(dot + 1, name.length() - kind.extension.length()));
            final @Nullable TileCoordinate coord =
                parseTile(
                    world,
                    view,
                    directory.relativize(path).resolveSibling(name.substring(0, dot) + ".png"));
            final byte @Nullable [] data =
                layer == null || coord == null ? null : readIfExists(path);
            if (data != null) {
              visitor.visit(coord, layer, kind, data, path.toFile().lastModified());
            }
          }
        }
      }
    }
  }

  @Override
  public void touch(final @NotNull TileCoordinate coord, final @NotNull FogView layer)
      throws IOException {
    Files.setLastModifiedTime(
        file(coord, layer, Kind.TILE).toPath(), FileTime.fromMillis(System.currentTimeMillis()));
  }

  @Override
  public boolean delete(final @NotNull TileCoordinate coord, final @NotNull FogView layer)
      throws IOException {
    Files.deleteIfExists(file(coord, layer, Kind.PICK).toPath());
    return Files.deleteIfExists(file(coord, layer, Kind.TILE).toPath());
  }

  @Override
  public long lastModified(final @NotNull TileCoordinate coord) {
    return file(coord, FogView.BASE, Kind.TILE).lastModified();
  }

  @Override
  public void deleteView(final @NotNull String worldName, final @NotNull MapView view)
      throws IOException {
    final @NotNull Path directory = worldDirectory(worldName, view);
    if (Files.isDirectory(directory)) {
      final @NotNull Path trash =
          directory.resolveSibling(
              directory.getFileName() + "-" + System.currentTimeMillis() + DELETED);
      Files.move(directory, trash, StandardCopyOption.ATOMIC_MOVE);
      deleteLater(trash);
    }
  }

  private void deleteLater(final @NotNull Path trash) {
    Thread.ofPlatform()
        .name("Taerra-Delete")
        .daemon()
        .start(
            () -> {
              try (final @NotNull Stream<Path> paths = Files.walk(trash)) {
                for (final @NotNull Path path :
                    (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                  Files.deleteIfExists(path);
                }
              } catch (final IOException e) {
                this.logger.warning(
                    "Failed to delete old tiles in " + trash + ": " + e.getMessage());
              }
            });
  }

  @Override
  public @NotNull Map<Integer, Set<FogView>> layers(final @NotNull TileCoordinate column) {
    final @NotNull Map<Integer, Set<FogView>> found = new ConcurrentHashMap<>();
    final @Nullable String @Nullable [] names =
        column(column).list((dir, name) -> name.endsWith(".png"));
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
  public void deleteLayers(final @NotNull String worldName) throws IOException {
    for (final @NotNull MapView view : MapView.values()) {
      final @NotNull Path directory = worldDirectory(worldName, view);
      if (!Files.isDirectory(directory)) {
        continue;
      }
      try (final @NotNull Stream<Path> paths = Files.walk(directory)) {
        for (final @NotNull Path path : (Iterable<Path>) paths::iterator) {
          final @NotNull String name = path.getFileName().toString();
          final int dot = name.indexOf('.');
          if (dot > 0
              && name.indexOf('.', dot + 1) > 0
              && !name.endsWith(".tmp")
              && FogView.parse(name.substring(dot + 1, name.lastIndexOf('.'))) != null) {
            Files.deleteIfExists(path);
          }
        }
      }
    }
  }

  @Override
  public @NotNull List<TileCoordinate> tilesAt(final @NotNull TileCoordinate level) {
    final @NotNull List<TileCoordinate> tiles = new ArrayList<>();
    final @Nullable File @Nullable [] columns =
        column(level).getParentFile().listFiles(File::isDirectory);
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
      final @NotNull Path directory = worldDirectory(worldName, view);
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
}

package net.aerulion.taerra.storage;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

sealed interface TileStore permits FileTileStore, SqlTileStore {

  byte @Nullable [] read(@NotNull TileCoordinate coord, @NotNull FogView layer, @NotNull Kind kind)
      throws IOException;

  void write(
      @NotNull TileCoordinate coord,
      @NotNull FogView layer,
      @NotNull Kind kind,
      byte @NotNull [] data)
      throws IOException;

  boolean writeIfNewer(
      @NotNull TileCoordinate coord,
      @NotNull FogView layer,
      @NotNull Kind kind,
      byte @NotNull [] data,
      long modified)
      throws IOException;

  void forEach(@NotNull Set<String> worlds, @NotNull Visitor visitor) throws IOException;

  void touch(@NotNull TileCoordinate coord, @NotNull FogView layer) throws IOException;

  boolean delete(@NotNull TileCoordinate coord, @NotNull FogView layer) throws IOException;

  long lastModified(@NotNull TileCoordinate coord) throws IOException;

  void deleteView(@NotNull String worldName, @NotNull MapView view) throws IOException;

  void deleteLayers(@NotNull String worldName) throws IOException;

  @NotNull
  Map<Integer, Set<FogView>> layers(@NotNull TileCoordinate column) throws IOException;

  @NotNull
  List<TileCoordinate> tilesAt(@NotNull TileCoordinate level) throws IOException;

  @NotNull
  List<TileCoordinate> tilesOlderThan(@NotNull String worldName, long cutoffMillis)
      throws IOException;

  default void close() {}

  @FunctionalInterface
  interface Visitor {
    void visit(
        @NotNull TileCoordinate coord,
        @NotNull FogView layer,
        @NotNull Kind kind,
        byte @NotNull [] data,
        long modified)
        throws IOException;
  }

  enum Kind {
    TILE(".png"),
    PICK(".pick");

    final @NotNull String extension;

    Kind(final @NotNull String extension) {
      this.extension = extension;
    }
  }
}

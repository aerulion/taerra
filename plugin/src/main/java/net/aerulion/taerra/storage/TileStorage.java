package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;

public sealed interface TileStorage permits TileStorageImpl {

  @NotNull
  Saved saveTile(@NotNull TileCoordinate coord, @NotNull BufferedImage image) throws IOException;

  @NotNull
  Saved saveTile(
      @NotNull TileCoordinate coord, @NotNull FogView layer, @NotNull BufferedImage image)
      throws IOException;

  boolean deleteTile(@NotNull TileCoordinate coord);

  boolean deleteLayer(@NotNull TileCoordinate coord, @NotNull FogView layer);

  void deleteView(@NotNull String worldName, @NotNull MapView view) throws IOException;

  @NotNull
  List<FogView> layers(@NotNull TileCoordinate coord);

  void deleteLayers(@NotNull String worldName) throws IOException;

  @NotNull
  List<TileCoordinate> tilesAt(@NotNull TileCoordinate level);

  @NotNull
  List<TileCoordinate> tilesOlderThan(@NotNull String worldName, long cutoffMillis)
      throws IOException;

  @NotNull
  Optional<EncodedTile> readEncodedTile(@NotNull TileCoordinate coord);

  @NotNull
  Optional<EncodedTile> readEncodedTile(@NotNull TileCoordinate coord, @NotNull FogView layer);

  @NotNull
  Optional<BufferedImage> loadTile(@NotNull TileCoordinate coord);

  @NotNull
  Optional<BufferedImage> loadTile(@NotNull TileCoordinate coord, @NotNull FogView layer);

  void savePick(@NotNull TileCoordinate coord, byte @NotNull [] data) throws IOException;

  void savePick(@NotNull TileCoordinate coord, @NotNull FogView layer, byte @NotNull [] data)
      throws IOException;

  @NotNull
  Optional<byte[]> readPick(@NotNull TileCoordinate coord);

  @NotNull
  Optional<byte[]> readPick(@NotNull TileCoordinate coord, @NotNull FogView layer);

  long lastModified(@NotNull TileCoordinate coord) throws IOException;

  void resize(int maxEncodedTiles);

  @NotNull
  StorageSettings settings();

  boolean moving();

  @NotNull
  Prepared prepare(@NotNull StorageSettings settings) throws IOException;

  void move(@NotNull Prepared prepared, @NotNull Set<String> worlds);

  void resume(@NotNull Set<String> worlds);

  void close();

  enum Saved {
    CREATED,
    CHANGED,
    UNCHANGED;

    public boolean changed() {
      return this != UNCHANGED;
    }
  }

  final class Prepared {

    final @NotNull TileStore store;
    final @NotNull StorageSettings settings;
    volatile boolean moved;

    Prepared(final @NotNull TileStore store, final @NotNull StorageSettings settings) {
      this.store = store;
      this.settings = settings;
    }

    public @NotNull StorageSettings settings() {
      return this.settings;
    }

    public void discard() {
      if (!this.moved) {
        this.store.close();
      }
    }
  }
}

package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public sealed interface TileStorage permits TileStorageImpl {

  @NotNull
  File tileFile(@NotNull TileCoordinate coord);

  boolean saveTile(@NotNull TileCoordinate coord, @NotNull BufferedImage image) throws IOException;

  boolean deleteTile(@NotNull TileCoordinate coord);

  @NotNull
  List<TileCoordinate> tilesAt(@NotNull TileCoordinate level);

  @NotNull
  List<TileCoordinate> tilesOlderThan(@NotNull String worldName, long cutoffMillis)
      throws IOException;

  @NotNull
  Optional<EncodedTile> readEncodedTile(@NotNull TileCoordinate coord);

  @NotNull
  Optional<BufferedImage> loadTile(@NotNull TileCoordinate coord);

  void savePick(@NotNull TileCoordinate coord, byte @NotNull [] data) throws IOException;

  @NotNull
  Optional<byte[]> readPick(@NotNull TileCoordinate coord);

  void resize(int maxEncodedTiles);
}

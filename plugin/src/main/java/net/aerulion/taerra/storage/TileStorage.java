package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;

public sealed interface TileStorage permits TileStorageImpl {

  @NotNull
  File tileFile(@NotNull TileCoordinate coord);

  @NotNull
  File tileFile(@NotNull TileCoordinate coord, @NotNull FogView layer);

  boolean saveTile(@NotNull TileCoordinate coord, @NotNull BufferedImage image) throws IOException;

  boolean saveTile(
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

  void resize(int maxEncodedTiles);
}

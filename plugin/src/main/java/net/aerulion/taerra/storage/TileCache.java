package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.util.Optional;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;

public sealed interface TileCache permits TileCacheImpl {

  @NotNull
  Optional<BufferedImage> get(@NotNull TileCoordinate coord);

  void put(@NotNull TileCoordinate coord, @NotNull BufferedImage image);

  void invalidate(@NotNull TileCoordinate coord);

  void clear();

  @NotNull
  TileCacheStats stats();
}

package net.aerulion.taerra.storage;

import java.awt.image.BufferedImage;
import java.util.Optional;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;

public sealed interface TileCache permits TileCacheImpl {

  @NotNull
  Optional<BufferedImage> get(@NotNull TileCoordinate coord);

  @NotNull
  Optional<BufferedImage> get(@NotNull TileCoordinate coord, @NotNull FogView layer);

  void put(@NotNull TileCoordinate coord, @NotNull BufferedImage image);

  void put(@NotNull TileCoordinate coord, @NotNull FogView layer, @NotNull BufferedImage image);

  void invalidate(@NotNull TileCoordinate coord);

  void invalidate(@NotNull TileCoordinate coord, @NotNull FogView layer);

  void clear();

  @NotNull
  TileCacheStats stats();

  void resize(int maxSize);
}

package net.aerulion.taerra.rendering.tile;

import java.awt.image.BufferedImage;
import net.aerulion.taerra.model.TileCoordinate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RenderedTile(
    @NotNull TileCoordinate coordinate, @NotNull BufferedImage image, @Nullable PickBuffer pick) {

  public RenderedTile(
      final @NotNull TileCoordinate coordinate, final @NotNull RenderedImage rendered) {
    this(coordinate, rendered.image(), rendered.pick());
  }
}

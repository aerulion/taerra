package net.aerulion.taerra.rendering.tile;

import java.awt.image.BufferedImage;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RenderedTile(
    @NotNull TileCoordinate coordinate,
    @NotNull FogView layer,
    @NotNull BufferedImage image,
    @Nullable PickBuffer pick) {

  public RenderedTile(
      final @NotNull TileCoordinate coordinate,
      final @NotNull BufferedImage image,
      final @Nullable PickBuffer pick) {
    this(coordinate, FogView.BASE, image, pick);
  }

  public RenderedTile(
      final @NotNull TileCoordinate coordinate, final @NotNull RenderedImage rendered) {
    this(coordinate, FogView.BASE, rendered.image(), rendered.pick());
  }
}

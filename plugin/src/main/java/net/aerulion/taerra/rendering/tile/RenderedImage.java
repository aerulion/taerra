package net.aerulion.taerra.rendering.tile;

import java.awt.image.BufferedImage;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RenderedImage(
    @NotNull BufferedImage image,
    @Nullable PickBuffer pick,
    @Nullable BufferedImage unveiled,
    @NotNull List<BufferedImage> views) {

  public RenderedImage(final @NotNull BufferedImage image, final @Nullable PickBuffer pick) {
    this(image, pick, null, List.of());
  }

  public RenderedImage(
      final @NotNull BufferedImage image,
      final @Nullable PickBuffer pick,
      final @Nullable BufferedImage unveiled) {
    this(image, pick, unveiled, List.of());
  }
}

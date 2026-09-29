package net.aerulion.taerra.rendering.tile;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record RenderedImage(@NotNull BufferedImage image, @Nullable PickBuffer pick) {}

package net.aerulion.taerra.rendering.color;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.minecraft.world.level.DryFoliageColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Colormaps {

  private static final int EXPECTED_SIZE = 256;

  private Colormaps() {
    super();
  }

  public static void load(final @NotNull Logger logger) {
    try {
      load(logger, "grass", GrassColor::init);
      load(logger, "foliage", FoliageColor::init);
      load(logger, "dry_foliage", DryFoliageColor::init);
    } catch (final IOException | RuntimeException e) {
      logger.log(Level.SEVERE, "Failed to load biome colormaps: " + e.getMessage(), e);
    }
  }

  private static void load(
      final @NotNull Logger logger, final @NotNull String name, final @NotNull Consumer<int[]> init)
      throws IOException {
    final @NotNull String path = "/colormap/" + name + ".png";
    try (final @Nullable InputStream input = Colormaps.class.getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Colormap resource not found: " + path);
      }
      final @Nullable BufferedImage image = ImageIO.read(input);
      if (image == null) {
        throw new IOException("Failed to read colormap image " + path);
      }
      final int width = image.getWidth();
      final int height = image.getHeight();
      if (width != EXPECTED_SIZE || height != EXPECTED_SIZE) {
        logger.warning("Colormap " + name + " is " + width + "x" + height + " instead of 256x256");
      }
      final int @NotNull [] pixels = new int[width * height];
      image.getRGB(0, 0, width, height, pixels, 0, width);
      init.accept(pixels);
    }
  }
}

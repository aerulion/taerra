package net.aerulion.taerra.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldImageExporterTest {

  private static @NotNull BufferedImage solid(final int size, final int argb) {
    final @NotNull BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    for (int x = 0; x < size; x++) {
      for (int y = 0; y < size; y++) {
        image.setRGB(x, y, argb);
      }
    }
    image.setRGB(size - 1, size - 1, 0x80123456);
    return image;
  }

  @Test
  @DisplayName(
      "tiles are stitched in place, a missing one stays transparent, and a standard decoder reads it back")
  void stitchesTilesIntoOneReadablePng(@TempDir final @NotNull Path directory) throws Exception {
    final int size = 4;
    final @NotNull Path target = directory.resolve("world.png");
    WorldImageExporter.write(
        target,
        size,
        2,
        2,
        (tileX, tileZ) ->
            tileX == 1 && tileZ == 0
                ? null
                : solid(size, 0xFF000000 | (tileX * 0x7F0000) | (tileZ * 0x00007F)));

    final BufferedImage image = ImageIO.read(target.toFile());
    assertEquals(8, image.getWidth());
    assertEquals(8, image.getHeight());
    assertEquals(0xFF000000, image.getRGB(0, 0));
    assertEquals(0, image.getRGB(5, 1) >>> 24, "the missing tile is transparent");
    assertEquals(0xFF00007F, image.getRGB(1, 5));
    assertEquals(0xFF7F007F, image.getRGB(6, 6));
    assertEquals(0x80123456, image.getRGB(7, 7), "alpha and color survive the filter");
  }
}

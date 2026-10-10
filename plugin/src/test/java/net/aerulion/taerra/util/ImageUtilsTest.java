package net.aerulion.taerra.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImageUtilsTest {

  private static @NotNull BufferedImage solid(final int argb, final int size) {
    final @NotNull BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        image.setRGB(x, y, argb);
      }
    }
    return image;
  }

  @Test
  @DisplayName("a child smaller than its quadrant is doubled pixel for pixel, never blended")
  void combineTilesDoublesSmallChildren() {
    final @NotNull BufferedImage child = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    child.setRGB(0, 0, 0xFF000000);
    child.setRGB(1, 0, 0xFFFFFFFF);
    child.setRGB(0, 1, 0xFF00FF00);
    child.setRGB(1, 1, 0xFF0000FF);

    final @NotNull BufferedImage combined = ImageUtils.combineTiles(child, null, null, null, 8);

    for (int y = 0; y < 4; y++) {
      for (int x = 0; x < 4; x++) {
        assertEquals(child.getRGB(x / 2, y / 2), combined.getRGB(x, y));
      }
    }
  }

  @Test
  @DisplayName("children land in the correct quadrant of their parent")
  void combineTilesPlacesQuadrants() {
    final int size = 8;
    final int topLeft = 0xFF112233;
    final int topRight = 0xFF445566;
    final int bottomLeft = 0xFF778899;
    final int bottomRight = 0xFFAABBCC;

    final @NotNull BufferedImage combined =
        ImageUtils.combineTiles(
            solid(topLeft, size),
            solid(topRight, size),
            solid(bottomLeft, size),
            solid(bottomRight, size),
            size);

    assertEquals(size, combined.getWidth());
    assertEquals(topLeft, combined.getRGB(0, 0));
    assertEquals(topRight, combined.getRGB(size - 1, 0));
    assertEquals(bottomLeft, combined.getRGB(0, size - 1));
    assertEquals(bottomRight, combined.getRGB(size - 1, size - 1));
  }

  @Test
  @DisplayName("downsampling averages in linear light, so a black and white checker turns mid grey")
  void combineTilesAveragesInLinearLight() {
    final int size = 8;
    final @NotNull BufferedImage source =
        new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        source.setRGB(x, y, (x + y) % 2 == 0 ? 0xFF000000 : 0xFFFFFFFF);
      }
    }

    final @NotNull BufferedImage combined =
        ImageUtils.combineTiles(source, source, source, source, size);

    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        assertEquals(0xFFBCBCBC, combined.getRGB(x, y));
      }
    }
  }

  @Test
  @DisplayName("a solid color survives downsampling unchanged, for every channel value")
  void combineTilesKeepsSolidColors() {
    for (int value = 0; value < 256; value++) {
      final int argb = 0xFF000000 | value << 16 | (255 - value) << 8 | value / 2;
      assertEquals(argb, ImageUtils.combineTiles(solid(argb, 4), null, null, null, 4).getRGB(0, 0));
    }
  }

  @Test
  @DisplayName("transparent pixels lower coverage but never darken the color they border")
  void combineTilesWeighsByAlpha() {
    final @NotNull BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    source.setRGB(0, 0, 0xFFC03010);
    source.setRGB(1, 1, 0xFFC03010);

    final int argb = ImageUtils.combineTiles(source, null, null, null, 4).getRGB(0, 0);

    assertEquals(0x80C03010, argb);
  }

  @Test
  @DisplayName("a missing child leaves its quadrant transparent rather than black")
  void combineTilesLeavesMissingChildTransparent() {
    final int size = 8;
    final @NotNull BufferedImage combined =
        ImageUtils.combineTiles(solid(0xFF112233, size), null, null, null, size);

    assertEquals(0xFF112233, combined.getRGB(0, 0));
    assertEquals(0, combined.getRGB(size - 1, 0) >>> 24, "absent child must be fully transparent");
  }

  @Test
  @DisplayName(
      "PNG encoding decodes to exactly the pixels drawn, with or without a palette, and zooms from either")
  void pngRoundTripIsExact() throws Exception {

    final @NotNull BufferedImage few = ImageUtils.createEmptyTile(16);
    final @NotNull BufferedImage many = ImageUtils.createEmptyTile(32);
    for (int y = 0; y < 16; y++) {
      for (int x = 0; x < 16; x++) {
        few.setRGB(x, y, x < 4 ? 0 : (x * 17 << 24) | (y * 13 << 8) | 0x40);
      }
    }
    for (int i = 0; i < 32 * 32; i++) {
      many.setRGB(i % 32, i / 32, 0xFF000000 | i * 16411);
    }
    for (final @NotNull BufferedImage original : new BufferedImage[] {few, many}) {
      final int size = original.getWidth();
      final BufferedImage decoded =
          javax.imageio.ImageIO.read(
              new java.io.ByteArrayInputStream(ImageUtils.encodePNG(original)));
      assertArrayEquals(
          original.getRGB(0, 0, size, size, null, 0, size),
          decoded.getRGB(0, 0, size, size, null, 0, size));

      final @NotNull BufferedImage zoomedOriginal =
          ImageUtils.combineTiles(original, null, null, null, size);
      final @NotNull BufferedImage zoomedDecoded =
          ImageUtils.combineTiles(decoded, null, null, null, size);
      assertArrayEquals(
          zoomedOriginal.getRGB(0, 0, size, size, null, 0, size),
          zoomedDecoded.getRGB(0, 0, size, size, null, 0, size));
    }
  }
}

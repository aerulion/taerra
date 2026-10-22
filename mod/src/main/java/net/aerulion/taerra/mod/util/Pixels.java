package net.aerulion.taerra.mod.util;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

public final class Pixels {

  public static final int ICON = 8;

  private Pixels() {}

  public static int @Nullable [] read(final byte[] png) {
    try (final NativeImage image = NativeImage.read(png)) {
      if (image.getWidth() != ICON || image.getHeight() != ICON) {
        return null;
      }
      final int[] pixels = new int[ICON * ICON];
      for (int y = 0; y < ICON; y++) {
        for (int x = 0; x < ICON; x++) {
          pixels[y * ICON + x] = image.getPixel(x, y);
        }
      }
      return pixels;
    } catch (final IOException | RuntimeException e) {
      return null;
    }
  }

  public static byte[] png(final int[] pixels) throws IOException {
    final Path file = Files.createTempFile("taerra-icon", ".png");
    try (final NativeImage image = new NativeImage(ICON, ICON, true)) {
      for (int i = 0; i < pixels.length; i++) {
        image.setPixel(i % ICON, i / ICON, pixels[i]);
      }
      image.writeToFile(file);
      return Files.readAllBytes(file);
    } finally {
      Files.deleteIfExists(file);
    }
  }

  public static boolean monochrome(final int[] pixels) {
    final Set<Integer> colors = new HashSet<>();
    for (final int pixel : pixels) {
      if (pixel >>> 24 != 0) {
        colors.add(pixel & 0xFFFFFF);
      }
    }
    return colors.equals(Set.of(0xFFFFFF)) || colors.equals(Set.of(0));
  }
}

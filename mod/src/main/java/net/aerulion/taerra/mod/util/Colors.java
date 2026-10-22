package net.aerulion.taerra.mod.util;

import org.jetbrains.annotations.NotNull;

public final class Colors {

  private Colors() {}

  public static boolean isHex(final @NotNull String value) {
    return value.matches("#[0-9a-fA-F]{6}");
  }

  public static int argb(final @NotNull String hex) {
    try {
      return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
    } catch (final RuntimeException e) {
      return 0xFFFFFFFF;
    }
  }

  public static @NotNull String hex(final int rgb) {
    return "#%06x".formatted(rgb & 0xFFFFFF);
  }

  public static int withAlpha(final int alpha, final int color) {
    return alpha << 24 | color & 0xFFFFFF;
  }

  public static int shade(final int color, final double factor) {
    int shaded = 0;
    for (int shift = 0; shift <= 16; shift += 8) {
      shaded |= (int) Math.round(((color >> shift) & 0xFF) * factor) << shift;
    }
    return shaded;
  }

  public static int mix(final int from, final int to, final double amount) {
    int mixed = 0xFF000000;
    for (int shift = 0; shift <= 16; shift += 8) {
      final int start = from >> shift & 0xFF;
      mixed |= (int) Math.round(start + ((to >> shift & 0xFF) - start) * amount) << shift;
    }
    return mixed;
  }

  public static double contrast(final int first, final int second) {
    final double a = luminance(first);
    final double b = luminance(second);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }

  private static double luminance(final int color) {
    double luminance = 0;
    final double[] weights = {.0722, .7152, .2126};
    for (int i = 0; i < 3; i++) {
      final double channel = (color >> i * 8 & 0xFF) / 255.0;
      luminance +=
          weights[i]
              * (channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4));
    }
    return luminance;
  }

  public static int fromHsv(final double hue, final double saturation, final double value) {
    int rgb = 0;
    for (final int n : new int[] {5, 3, 1}) {
      final double k = (n + hue / 60) % 6;
      final double channel =
          value - value * saturation * Math.max(0, Math.min(k, Math.min(4 - k, 1)));
      rgb = rgb << 8 | (int) Math.round(channel * 255);
    }
    return rgb;
  }

  public static double[] toHsv(final int rgb) {
    final double r = (rgb >> 16 & 0xFF) / 255.0;
    final double g = (rgb >> 8 & 0xFF) / 255.0;
    final double b = (rgb & 0xFF) / 255.0;
    final double max = Math.max(r, Math.max(g, b));
    final double range = max - Math.min(r, Math.min(g, b));
    final double hue;
    if (range == 0) {
      hue = 0;
    } else if (max == r) {
      hue = ((g - b) / range + 6) % 6;
    } else if (max == g) {
      hue = (b - r) / range + 2;
    } else {
      hue = (r - g) / range + 4;
    }
    return new double[] {hue * 60, max == 0 ? 0 : range / max, max};
  }
}

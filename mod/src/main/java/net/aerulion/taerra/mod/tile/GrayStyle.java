package net.aerulion.taerra.mod.tile;

public final class GrayStyle {

  private GrayStyle() {}

  public static int apply(final int argb) {
    final int alpha = argb >>> 24;
    if (alpha == 0) {
      return argb;
    }
    final double r = (argb >> 16 & 0xFF) / 255.0;
    final double g = (argb >> 8 & 0xFF) / 255.0;
    final double b = (argb & 0xFF) / 255.0;
    final double luma = .2126 * r + .7152 * g + .0722 * b;
    final double water = Math.clamp(1.5 * (b - .5 * r - .5 * g) - .45, 0, 1);
    final double a = alpha / 255.0;
    final double outAlpha = water + a * (1 - water);
    final double outR =
        (.27 * water + Math.min(1, luma * .431 + .118) * a * (1 - water)) / outAlpha;
    final double outG =
        (.36 * water + Math.min(1, luma * .435 + .118) * a * (1 - water)) / outAlpha;
    final double outB = (.5 * water + Math.min(1, luma * .443 + .122) * a * (1 - water)) / outAlpha;
    return (int) Math.round(outAlpha * 255) << 24
        | (int) Math.round(outR * 255) << 16
        | (int) Math.round(outG * 255) << 8
        | (int) Math.round(outB * 255);
  }
}

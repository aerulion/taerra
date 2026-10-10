package net.aerulion.taerra.rendering.style;

import org.jetbrains.annotations.NotNull;

public final class MapStyle {

  public static final float POSITIVE_X_FACE = 0.80f;

  public static final float POSITIVE_Z_FACE = 0.62f;

  public static final float @NotNull [] OCCLUSION_LEVELS = {
    1.00f, 1.00f, 0.93f, 0.93f, 0.86f, 0.86f, 0.80f, 0.80f, 0.76f
  };

  public static final int SUN_STEP_X = 809;

  public static final int SUN_STEP_Z = -627;

  public static final int ISO_SHADOW_LENGTH_PER_BLOCK = 2048;

  public static final int TOP_DOWN_SHADOW_LENGTH_PER_BLOCK = 1024;

  public static final float @NotNull [] DEPTH_TINT = {0.84f, 0.90f, 0.98f};

  public static final int DEPTH_BAND_BLOCKS = 4;

  public static final int MAX_DEPTH_BANDS = 4;
  public static final int MAX_EDGE_DROP = 8;

  public static final int TOP_DOWN_SHADOW_DISTANCE = 32;

  public static final int ISO_SHADOW_DISTANCE = 64;

  public static final float TOP_FACE_WEIGHT = 2.0f;

  public static final float WATER_CLARITY = 0.5f;
  public static final float WATER_MAX_OPACITY = 0.8f;
  public static final float SHALLOW_GLINT = 0.10f;
  public static final int SHALLOW_TINT = 0xFFCDEDE6;
  private static final int WATER_OPACITY_STEPS = 32;
  private static final float @NotNull [] WATER_OPACITY = buildWaterOpacity();
  private static final float @NotNull [] @NotNull [] DEPTH_FACTORS = buildDepthFactors();

  private MapStyle() {
    super();
  }

  public static int blendFaces(
      final int topRgb,
      final int sideRgb,
      final int dropX,
      final int dropZ,
      final float topWeight) {
    if (dropX <= 0 && dropZ <= 0) {
      return topRgb;
    }
    return blendFaces(
        topRgb,
        shade(sideRgb, POSITIVE_X_FACE),
        shade(sideRgb, POSITIVE_Z_FACE),
        dropX,
        dropZ,
        topWeight);
  }

  public static int blendFaces(
      final int topRgb,
      final int faceX,
      final int faceZ,
      final int dropX,
      final int dropZ,
      final float topWeight) {
    if (dropX <= 0 && dropZ <= 0) {
      return topRgb;
    }

    final float bandsX = Math.min(MAX_EDGE_DROP, Math.max(0, dropX));
    final float bandsZ = Math.min(MAX_EDGE_DROP, Math.max(0, dropZ));
    final float total = topWeight + bandsX + bandsZ;

    final int red =
        (int)
            ((((topRgb >> 16) & 0xFF) * topWeight
                    + ((faceX >> 16) & 0xFF) * bandsX
                    + ((faceZ >> 16) & 0xFF) * bandsZ)
                / total);
    final int green =
        (int)
            ((((topRgb >> 8) & 0xFF) * topWeight
                    + ((faceX >> 8) & 0xFF) * bandsX
                    + ((faceZ >> 8) & 0xFF) * bandsZ)
                / total);
    final int blue =
        (int)
            (((topRgb & 0xFF) * topWeight + (faceX & 0xFF) * bandsX + (faceZ & 0xFF) * bandsZ)
                / total);
    return (topRgb & 0xFF000000)
        | (Math.min(255, red) << 16)
        | (Math.min(255, green) << 8)
        | Math.min(255, blue);
  }

  public static int shallow(final int argb, final int depth) {
    return depth == 1 ? blend(argb, SHALLOW_TINT, SHALLOW_GLINT) : argb;
  }

  private static float @NotNull [] buildWaterOpacity() {
    final float @NotNull [] opacity = new float[WATER_OPACITY_STEPS + 1];
    for (int depth = 0; depth <= WATER_OPACITY_STEPS; depth++) {
      opacity[depth] = WATER_MAX_OPACITY * (1.0f - (float) Math.pow(WATER_CLARITY, depth));
    }
    return opacity;
  }

  public static int underwater(final int seen, final int water, final int depth) {
    final int clamped = Math.max(1, Math.min(WATER_OPACITY_STEPS, depth));
    return depthTinted(blend(seen, water, WATER_OPACITY[clamped]), depth);
  }

  private static float @NotNull [] @NotNull [] buildDepthFactors() {
    final float @NotNull [] @NotNull [] factors = new float[MAX_DEPTH_BANDS + 1][DEPTH_TINT.length];
    for (int band = 0; band <= MAX_DEPTH_BANDS; band++) {
      for (int channel = 0; channel < DEPTH_TINT.length; channel++) {
        factors[band][channel] = (float) Math.pow(DEPTH_TINT[channel], band);
      }
    }
    return factors;
  }

  public static int depthTinted(final int argb, final int depth) {
    final int band = Math.min(MAX_DEPTH_BANDS, Math.max(0, depth) / DEPTH_BAND_BLOCKS);
    if (band == 0) {
      return argb;
    }
    final float @NotNull [] factors = DEPTH_FACTORS[band];
    final int red = (int) (((argb >> 16) & 0xFF) * factors[0]);
    final int green = (int) (((argb >> 8) & 0xFF) * factors[1]);
    final int blue = (int) ((argb & 0xFF) * factors[2]);
    return (argb & 0xFF000000)
        | (Math.min(255, red) << 16)
        | (Math.min(255, green) << 8)
        | Math.min(255, blue);
  }

  public static float occlusion(final int tallerNeighbors) {
    if (tallerNeighbors <= 0) {
      return 1.0f;
    }
    return OCCLUSION_LEVELS[Math.min(tallerNeighbors, OCCLUSION_LEVELS.length - 1)];
  }

  public static int shade(final int argb, final float factor) {
    final int red = Math.min(255, (int) (((argb >> 16) & 0xFF) * factor));
    final int green = Math.min(255, (int) (((argb >> 8) & 0xFF) * factor));
    final int blue = Math.min(255, (int) ((argb & 0xFF) * factor));
    return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
  }

  public static int blend(final int from, final int to, final float amount) {
    if (amount <= 0.0f) {
      return from;
    }
    if (amount >= 1.0f) {
      return (from & 0xFF000000) | (to & 0x00FFFFFF);
    }
    final int red =
        (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * amount);
    final int green =
        (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * amount);
    final int blue = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
    return (from & 0xFF000000) | (red << 16) | (green << 8) | blue;
  }
}

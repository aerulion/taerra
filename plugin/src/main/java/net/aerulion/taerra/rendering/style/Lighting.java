package net.aerulion.taerra.rendering.style;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;

public final class Lighting {

  public static final int TOP = 0;

  public static final int POSITIVE_X = 1;

  public static final int POSITIVE_Z = 2;

  public static final int EMISSIVE = 3;

  public static final int WATER = 4;

  public static final int NO_CONTACT = 0;

  public static final int CONTACT_FOOT = 1;

  public static final int CONTACT_NEAR = 2;

  private static final float @NotNull [] SUN = {0.95f, 0.80f, 0.52f};
  private static final float @NotNull [] SKY = {0.20f, 0.28f, 0.50f};

  private static final float @NotNull [] SUN_ON_FACE = {1.00f, 0.70f, 0.00f, 1.00f, 1.00f};
  private static final float @NotNull [] SKY_ON_FACE = {1.00f, 0.60f, 1.05f, 1.00f, 1.00f};
  private static final float @NotNull [] CONTACT_SKY = {1.00f, 0.45f, 0.72f};
  private static final float @NotNull [] BOUNCE = {0.00f, 0.25f, 0.12f};
  private static final float SATURATION = 1.20f;
  private static final float WATER_SHEEN = 0.04f;
  private static final double SHOULDER_KNEE = 0.85;

  private static final float @NotNull [] TO_LINEAR = new float[256];
  private static final @NotNull ThreadLocal<Long2IntOpenHashMap> CACHE =
      ThreadLocal.withInitial(Lighting::newCache);
  private static final @NotNull ThreadLocal<Long2IntOpenHashMap> BOUNCE_CACHE =
      ThreadLocal.withInitial(Lighting::newCache);

  static {
    for (int i = 0; i < 256; i++) {
      final float c = i / 255.0f;
      TO_LINEAR[i] = c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }
  }

  private Lighting() {
    super();
  }

  private static @NotNull Long2IntOpenHashMap newCache() {
    final @NotNull Long2IntOpenHashMap cache = new Long2IntOpenHashMap();
    cache.defaultReturnValue(0);
    return cache;
  }

  public static int face(
      final int argb,
      final int face,
      final int tallerNeighbors,
      final boolean shadowed,
      final int contact) {
    final long key =
        (argb & 0xFFFFFFFFL)
            | (long) face << 32
            | (long) Math.min(15, tallerNeighbors) << 35
            | (shadowed ? 1L << 39 : 0L)
            | (long) contact << 41;

    final @NotNull Long2IntOpenHashMap cache = CACHE.get();
    int lit = cache.get(key);
    if (lit == 0) {
      lit =
          face == EMISSIVE
              ? light(argb, TOP, 1.0f, false, 1.0f)
              : light(
                  argb, face, MapStyle.occlusion(tallerNeighbors), shadowed, CONTACT_SKY[contact]);
      cache.put(key, lit);
    }
    return lit;
  }

  public static int bounce(
      final int litArgb, final int albedoArgb, final int groundArgb, final int contact) {
    if (BOUNCE[contact] == 0.0f) {
      return litArgb;
    }
    final long key = (litArgb & 0xFFFFFFFFL) | (long) (groundArgb & 0x00FFFFFF) << 32;
    final @NotNull Long2IntOpenHashMap cache = BOUNCE_CACHE.get();
    final long levelKey = key ^ (long) contact << 56;
    int bounced = cache.get(levelKey);
    if (bounced == 0) {
      final float amount = BOUNCE[contact];
      bounced =
          clamped(
              litArgb,
              TO_LINEAR[(litArgb >> 16) & 0xFF]
                  + amount
                      * TO_LINEAR[(albedoArgb >> 16) & 0xFF]
                      * TO_LINEAR[(groundArgb >> 16) & 0xFF],
              TO_LINEAR[(litArgb >> 8) & 0xFF]
                  + amount
                      * TO_LINEAR[(albedoArgb >> 8) & 0xFF]
                      * TO_LINEAR[(groundArgb >> 8) & 0xFF],
              TO_LINEAR[litArgb & 0xFF]
                  + amount * TO_LINEAR[albedoArgb & 0xFF] * TO_LINEAR[groundArgb & 0xFF]);
      cache.put(levelKey, bounced);
    }
    return bounced;
  }

  private static int light(
      final int argb,
      final int face,
      final float occlusion,
      final boolean shadowed,
      final float contact) {
    final float sun =
        (shadowed ? 0.0f : SUN_ON_FACE[face]) * (0.5f + 0.5f * occlusion) * contactSun(contact);
    final float sky = SKY_ON_FACE[face] * occlusion * contact;
    final float sheen = face == WATER ? (shadowed ? 0.0f : WATER_SHEEN) : 0.0f;
    final float red =
        TO_LINEAR[(argb >> 16) & 0xFF] * (SUN[0] * sun + SKY[0] * sky) + SUN[0] * sheen;
    final float green =
        TO_LINEAR[(argb >> 8) & 0xFF] * (SUN[1] * sun + SKY[1] * sky) + SUN[1] * sheen;
    final float blue = TO_LINEAR[argb & 0xFF] * (SUN[2] * sun + SKY[2] * sky) + SUN[2] * sheen;

    final double l = Math.cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue);
    final double m = Math.cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue);
    final double s = Math.cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue);
    final double lightness = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
    final double a = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s) * SATURATION;
    final double b = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s) * SATURATION;
    final double l2 = Math.pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3);
    final double m2 = Math.pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3);
    final double s2 = Math.pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3);
    return encode(
        argb,
        4.0767416621 * l2 - 3.3077115913 * m2 + 0.2309699292 * s2,
        -1.2684380046 * l2 + 2.6097574011 * m2 - 0.3413193965 * s2,
        -0.0041960863 * l2 - 0.7034186147 * m2 + 1.7076147010 * s2);
  }

  private static int encode(
      final int argb, final double red, final double green, final double blue) {
    return (argb & 0xFF000000)
        | (toSrgb(shoulder(red)) << 16)
        | (toSrgb(shoulder(green)) << 8)
        | toSrgb(shoulder(blue));
  }

  private static int clamped(
      final int argb, final double red, final double green, final double blue) {
    return (argb & 0xFF000000) | (toSrgb(red) << 16) | (toSrgb(green) << 8) | toSrgb(blue);
  }

  private static double shoulder(final double linear) {
    if (linear <= SHOULDER_KNEE) {
      return linear;
    }
    final double range = 1.0 - SHOULDER_KNEE;
    final double over = Math.min(linear - SHOULDER_KNEE, 2.0 * range);
    return SHOULDER_KNEE + over - over * over / (4.0 * range);
  }

  private static float contactSun(final float contactSky) {
    return 0.6f + 0.4f * contactSky;
  }

  private static int toSrgb(final double linear) {
    final double c = Math.max(0.0, Math.min(1.0, linear));
    final double encoded = c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
    return (int) Math.round(encoded * 255.0);
  }
}

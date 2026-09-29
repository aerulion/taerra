package net.aerulion.taerra.rendering.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LightingTest {

  private static final int GRAY = 0xFF808080;

  private static int red(final int argb) {
    return (argb >> 16) & 0xFF;
  }

  private static int green(final int argb) {
    return (argb >> 8) & 0xFF;
  }

  private static int blue(final int argb) {
    return argb & 0xFF;
  }

  private static int brightness(final int argb) {
    return red(argb) + green(argb) + blue(argb);
  }

  private static int lit(
      final int face, final int taller, final boolean shadowed, final int contact) {
    return Lighting.face(GRAY, face, taller, shadowed, contact);
  }

  @Test
  @DisplayName("sunlight is warm and shadow is lit by the sky, so it goes blue rather than gray")
  void sunIsWarmAndShadowIsBlue() {
    final int sunlit = lit(Lighting.TOP, 0, false, Lighting.NO_CONTACT);
    final int shaded = lit(Lighting.TOP, 0, true, Lighting.NO_CONTACT);
    assertTrue(red(sunlit) > blue(sunlit), "a gray surface in the sun should come out warm");
    assertTrue(blue(shaded) > red(shaded), "the same surface in shadow should come out cool");
    assertTrue(brightness(shaded) < brightness(sunlit), "shadow must still darken");
  }

  @Test
  @DisplayName("the side facing the sun is warmer and brighter than the side facing away")
  void sidesAreLitApart() {
    final int towardSun = lit(Lighting.POSITIVE_X, 0, false, Lighting.NO_CONTACT);
    final int awayFromSun = lit(Lighting.POSITIVE_Z, 0, false, Lighting.NO_CONTACT);
    assertTrue(brightness(towardSun) > brightness(awayFromSun));
    assertTrue(red(towardSun) - blue(towardSun) > red(awayFromSun) - blue(awayFromSun));
  }

  @Test
  @DisplayName("occlusion and contact shading only ever darken, and the foot of a wall most of all")
  void occlusionAndContactDarken() {
    assertTrue(
        brightness(lit(Lighting.TOP, 8, false, Lighting.NO_CONTACT))
            < brightness(lit(Lighting.TOP, 0, false, Lighting.NO_CONTACT)));
    for (final int face : new int[] {Lighting.POSITIVE_X, Lighting.POSITIVE_Z}) {
      final int open = brightness(lit(face, 0, false, Lighting.NO_CONTACT));
      final int near = brightness(lit(face, 0, false, Lighting.CONTACT_NEAR));
      final int foot = brightness(lit(face, 0, false, Lighting.CONTACT_FOOT));
      assertTrue(
          foot < near && near < open, "contact should deepen toward the ground on face " + face);
    }
  }

  @Test
  @DisplayName("lighting keeps alpha and gives the same answer every time")
  void alphaSurvivesAndResultsAreStable() {
    final int first = lit(Lighting.POSITIVE_Z, 3, true, Lighting.CONTACT_FOOT);
    assertEquals(0xFF, first >>> 24);
    assertEquals(first, lit(Lighting.POSITIVE_Z, 3, true, Lighting.CONTACT_FOOT));
  }
}

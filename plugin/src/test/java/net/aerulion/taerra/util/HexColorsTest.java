package net.aerulion.taerra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexColorsTest {

  @Test
  @DisplayName("a color is six hex digits, with or without the hash, in either case")
  void parsesHexColors() {
    assertEquals(OptionalInt.of(0x4FA3E0), HexColors.parseRgb("#4fa3e0"));
    assertEquals(OptionalInt.of(0x4FA3E0), HexColors.parseRgb("4FA3E0"));
    assertEquals(OptionalInt.empty(), HexColors.parseRgb("#4fa3e"));
    assertEquals(OptionalInt.empty(), HexColors.parseRgb("blue"));
    assertEquals(OptionalInt.empty(), HexColors.parseRgb("#4fa3e0ff"));
    assertEquals(OptionalInt.empty(), HexColors.parseRgb(null));
  }

  @Test
  @DisplayName("a color formats as a lowercase hash and six digits")
  void formatsHexColors() {
    assertEquals("#4fa3e0", HexColors.format(0x4FA3E0));
    assertEquals("#00000f", HexColors.format(0xFF00000F));
  }
}

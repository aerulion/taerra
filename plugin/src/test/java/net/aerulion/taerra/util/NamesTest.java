package net.aerulion.taerra.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NamesTest {

  @Test
  @DisplayName("names are letters, digits, spaces and common punctuation, up to 32 long")
  void of() {
    assertEquals("Base #2 (North)", Names.of("  Base #2 (North) "));
    assertEquals("Wassergrundstück", Names.of("Wassergrundstu\u0308ck"));
    assertEquals("Rock'n'Roll: Hi-Fi_Shop!", Names.of("Rock'n'Roll: Hi-Fi_Shop!"));
    assertEquals("x".repeat(32), Names.of("x".repeat(32)));
    assertNull(Names.of("x".repeat(33)));
    assertNull(Names.of("Home \uD83C\uDFE0"));
    assertNull(Names.of("<b>Home</b>"));
    assertNull(Names.of("§cHome"));
    assertNull(Names.of("   "));
    assertNull(Names.of(".."));
    assertNull(Names.of("?!"));
    assertNull(Names.of(null));
  }

  @Test
  @DisplayName("descriptions keep line breaks, drop control characters and stop at 280")
  void description() {
    assertEquals("Shop\n\nOpen daily", Names.description("  Shop\r\n\n\n\tOpen daily \u0007"));
    assertEquals("x".repeat(280), Names.description("x".repeat(300)));
    assertNull(Names.description(" \n "));
    assertNull(Names.description(null));
  }
}

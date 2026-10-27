package net.aerulion.taerra.mod.gui.screen.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingValuesTest {

  @Test
  @DisplayName("layer scales read and write powers of two in pixels or blocks")
  void scales() {
    assertEquals(3, SettingValues.parseScale("8px"));
    assertEquals(-2, SettingValues.parseScale("4blocks"));
    assertEquals(0, SettingValues.parseScale("1block"));
    assertNull(SettingValues.parseScale("6px"));
    assertNull(SettingValues.parseScale("8 px"));
    assertEquals("8px", SettingValues.scaleText(3));
    assertEquals("4blocks", SettingValues.scaleText(-2));
    assertEquals("", SettingValues.scaleText(null));
  }

  @Test
  @DisplayName("limits need all four bounds, inside the world, and round ones a real size")
  void limits() {
    assertFalse(SettingValues.badLimit(SettingValues.limit("none", null, null, null, null)));
    assertFalse(SettingValues.badLimit(SettingValues.limit("rectangle", -5, -5, 5, 10)));
    assertTrue(SettingValues.badLimit(SettingValues.limit("rectangle", -5, null, 5, 10)));
    assertTrue(SettingValues.badLimit(SettingValues.limit("rectangle", 5, 0, -5, 10)));
    assertTrue(SettingValues.badLimit(SettingValues.limit("square", 0, 0, 1, 1)));
    assertTrue(SettingValues.badLimit(SettingValues.limit("rectangle", 0, 0, 30_000_001, 1)));
  }

  @Test
  @DisplayName("reshaping keeps the centre and turns the larger side into the diameter")
  void reshape() {
    assertEquals(
        SettingValues.limit("circle", -10, 0, 10, 20),
        SettingValues.reshape(SettingValues.limit("rectangle", -10, 5, 10, 15), "circle"));
    assertEquals(
        SettingValues.limit("rectangle", -1000, -1000, 1000, 1000),
        SettingValues.reshape(null, "rectangle"));
  }

  @Test
  @DisplayName("an order is the same when the worlds both list keep their sequence")
  void sameOrder() {
    assertTrue(
        SettingValues.sameOrder(
            JsonParser.parseString("[\"a\", \"b\"]"),
            JsonParser.parseString("[\"a\", \"c\", \"b\"]")));
    assertFalse(
        SettingValues.sameOrder(
            JsonParser.parseString("[\"a\", \"b\"]"), JsonParser.parseString("[\"b\", \"a\"]")));
  }

  @Test
  @DisplayName("only gaining a view needs a render")
  void addsView() {
    assertTrue(SettingValues.addsView(new JsonPrimitive("top_down"), new JsonPrimitive("both")));
    assertFalse(SettingValues.addsView(new JsonPrimitive("both"), new JsonPrimitive("isometric")));
    assertFalse(SettingValues.addsView(null, new JsonPrimitive("bogus")));
  }

  @Test
  @DisplayName("a world field names its world even when the id has colons")
  void worldOf() {
    assertEquals(
        "minecraft:overworld",
        SettingValues.worldOf(SettingValues.worldField("minecraft:overworld", "enabled")));
  }
}

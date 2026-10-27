package net.aerulion.taerra.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingTest {

  @Test
  @DisplayName("numbers must be whole and inside the setting's range")
  void numbers() {
    final Setting port = Setting.number("web.port", 1, 65535, false);
    assertEquals(8123, port.valid(8123));
    assertEquals(8123, port.valid(8123.0));
    assertNull(port.valid(8123.5));
    assertNull(port.valid(0));
    assertNull(port.valid(65536));
    assertNull(port.valid("8123"));
  }

  @Test
  @DisplayName("toggles take booleans, choices only their options")
  void togglesAndChoices() {
    assertEquals(true, Setting.toggle("markers.spawn.enabled").valid(true));
    assertNull(Setting.toggle("markers.spawn.enabled").valid("true"));
    final Setting type = Setting.choice("storage.type", "files", "mariadb");
    assertEquals("mariadb", type.valid("mariadb"));
    assertNull(type.valid("postgres"));
  }

  @Test
  @DisplayName("text is stripped, refuses control characters, and the map name is cleaned")
  void text() {
    assertEquals("example.net", Setting.text("web.host", false).valid("  example.net "));
    assertNull(Setting.text("web.host", false).valid("a\nb"));
    assertEquals("", Setting.text("web.name", true).valid("   "));
    assertNotNull(Setting.text("web.name", true).valid("My Server"));
  }

  @Test
  @DisplayName("secrets keep their spaces but refuse control characters")
  void secrets() {
    final Setting password = Setting.secret("storage.database.password");
    assertEquals(" pass word ", password.valid(" pass word "));
    assertNull(password.valid("pass\tword"));
  }

  @Test
  @DisplayName("every setting key is listed once")
  void uniqueKeys() {
    final Set<String> keys = new HashSet<>();
    ConfigEditor.SETTINGS.forEach(setting -> assertTrue(keys.add(setting.key()), setting.key()));
  }
}

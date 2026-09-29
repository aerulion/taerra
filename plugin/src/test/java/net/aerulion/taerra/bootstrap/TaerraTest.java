package net.aerulion.taerra.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaerraTest {

  @Test
  @DisplayName("a padded api-version matches the server version it was built for, and only that")
  void versionsMatch() {
    assertEquals(Taerra.trimVersion("26.2"), Taerra.trimVersion("26.2.0"));
    assertEquals(Taerra.trimVersion("26.2.1"), Taerra.trimVersion("26.2.1"));
    assertNotEquals(Taerra.trimVersion("26.2.1"), Taerra.trimVersion("26.2.0"));
    assertNotEquals(Taerra.trimVersion("26.20"), Taerra.trimVersion("26.2.0"));
  }
}

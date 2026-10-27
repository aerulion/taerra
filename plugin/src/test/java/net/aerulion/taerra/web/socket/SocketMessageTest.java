package net.aerulion.taerra.web.socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocketMessageTest {

  private static final Set<SocketMessage> WEB_IGNORES = Set.of(SocketMessage.PONG);
  private static final Set<SocketMessage> MOD_IGNORES =
      Set.of(SocketMessage.PONG, SocketMessage.DEVICES);

  private static String sources(final String folder, final String suffix) throws IOException {
    try (final Stream<Path> files = Files.walk(Path.of(folder))) {
      return files
          .filter(file -> file.toString().endsWith(suffix))
          .map(
              file -> {
                try {
                  return Files.readString(file);
                } catch (final IOException e) {
                  throw new IllegalStateException(e);
                }
              })
          .collect(Collectors.joining("\n"));
    }
  }

  @Test
  @DisplayName("messages are JSON with their type first and every value escaped")
  void json() {
    assertEquals("{\"type\":\"icons\"}", SocketMessage.ICONS.json());
    assertEquals(
        "{\"type\":\"markers\",\"world\":\"a\\\"b\"}", SocketMessage.MARKERS.json("world", "a\"b"));
    assertEquals(
        "{\"type\":\"navigation\",\"target\":null}", SocketMessage.NAVIGATION.json("target", null));
    assertEquals("{\"type\":\"sky\",\"data\":[1]}", SocketMessage.SKY.withData("[1]"));
    assertThrows(IllegalArgumentException.class, () -> SocketMessage.MARKERS.json("world"));
  }

  @Test
  @DisplayName("the web map and the mod handle every message the plugin sends")
  void clientsHandleEveryType() throws IOException {
    final String web = sources("src/main/resources/web/js", ".js");
    final String mod = sources("../mod/src/main/java", ".java");
    for (final SocketMessage message : SocketMessage.values()) {
      if (!WEB_IGNORES.contains(message)) {
        assertTrue(web.contains(".on('" + message.type() + "'"), "web map: " + message.type());
      }
      if (!MOD_IGNORES.contains(message)) {
        assertTrue(mod.contains("case \"" + message.type() + "\""), "mod: " + message.type());
      }
    }
  }
}

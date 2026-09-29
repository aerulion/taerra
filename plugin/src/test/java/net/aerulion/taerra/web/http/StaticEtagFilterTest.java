package net.aerulion.taerra.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaticEtagFilterTest {

  private static String tag(final @NotNull String content) throws IOException {
    return StaticEtagFilter.hash(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  @DisplayName("a file changed to the same length gets a new tag, an unchanged one keeps it")
  void tagFollowsContentNotLength() throws IOException {
    assertNotEquals(tag("details.append(a, b)"), tag("details.append(b, a)"));
    assertEquals(tag("details.append(a, b)"), tag("details.append(a, b)"));
  }
}

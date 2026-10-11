package net.aerulion.taerra.web.http.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IndexPageTest {

  private static @NotNull String template() throws IOException {
    try (final InputStream in =
        Objects.requireNonNull(
            IndexPageTest.class.getClassLoader().getResourceAsStream("web/index.html"))) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("the server name lands in the title, link preview and header, escaped")
  void nameEverywhere() throws IOException {
    final @NotNull String html = MapRoutes.indexPage(template(), "Tom & <Jerry>", "");
    final @NotNull String name = "Tom &amp; &lt;Jerry&gt;";
    assertTrue(html.contains("<title>" + name + "</title>"));
    assertTrue(html.contains("content=\"" + name + "\" property=\"og:title\""));
    assertTrue(html.contains("content=\"" + name + "\" property=\"og:image:alt\""));
    assertTrue(html.contains("id=\"server-name\">" + name + "</span>"));
    assertFalse(html.contains("<Jerry>"));
    assertTrue(html.contains("class=\"brand\""), "the TAERRA brand stays");
  }

  @Test
  @DisplayName("without a name the page is the template, the header slot hidden")
  void noName() throws IOException {
    assertEquals(template(), MapRoutes.indexPage(template(), "", ""));
    assertTrue(
        template().contains("<span class=\"server-name\" hidden id=\"server-name\"></span>"));
  }
}

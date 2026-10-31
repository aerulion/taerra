package net.aerulion.taerra.web.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticFileFilterTest {

  private static final byte[] SCRIPT = "export {};".getBytes(StandardCharsets.UTF_8);

  private static @Nullable String tag(final @NotNull String content) {
    return StaticFileFilter.hash(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("a file changed to the same length gets a new tag, an unchanged one keeps it")
  void tagFollowsContentNotLength() {
    assertNotEquals(tag("details.append(a, b)"), tag("details.append(b, a)"));
    assertEquals(tag("details.append(a, b)"), tag("details.append(a, b)"));
  }

  @Test
  @DisplayName("files are served from a jar whose path has Cyrillic letters and brackets")
  void servesFromJarUnderAnyPath(final @TempDir Path dir) throws IOException {
    final Path jar = Files.createDirectories(dir.resolve("сервер [1]")).resolve("taerra.jar");
    try (final OutputStream file = Files.newOutputStream(jar);
        final JarOutputStream out = new JarOutputStream(file)) {
      out.putNextEntry(new JarEntry("web/js/"));
      out.putNextEntry(new JarEntry("web/js/app.js"));
      out.write(SCRIPT);
    }
    try (final URLClassLoader loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
      final StaticFileFilter filter = new StaticFileFilter(loader);
      final StaticFileFilter.StaticFile app = filter.file("/js/app.js");
      assertNotNull(app);
      assertArrayEquals(SCRIPT, app.bytes());
      assertEquals("text/javascript;charset=utf-8", app.type());
      assertNull(filter.file("/js"));
      assertNull(filter.file("/js/missing.js"));
      assertNull(filter.file("/js/../js/app.js"));
    }
  }
}

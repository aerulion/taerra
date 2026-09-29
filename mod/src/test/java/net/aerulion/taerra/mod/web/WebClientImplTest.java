package net.aerulion.taerra.mod.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class WebClientImplTest {

  @Test
  void gunzipRestoresTheBody() throws Exception {
    final byte @NotNull [] json =
        "[{\"name\":\"world\"}]".repeat(100).getBytes(StandardCharsets.UTF_8);
    final @NotNull ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (final @NotNull GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(json);
    }
    assertArrayEquals(json, WebClientImpl.gunzip(out.toByteArray()));
  }
}

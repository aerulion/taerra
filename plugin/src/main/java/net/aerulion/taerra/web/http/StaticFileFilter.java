package net.aerulion.taerra.web.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.http.MimeTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StaticFileFilter extends HttpFilter {

  private final transient @NotNull ClassLoader loader;
  private final transient @NotNull Map<String, StaticFile> files = new ConcurrentHashMap<>();

  StaticFileFilter(final @NotNull ClassLoader loader) {
    super();
    this.loader = loader;
  }

  static @Nullable String hash(final byte @NotNull [] bytes) {
    try {
      final byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return "W/\"" + HexFormat.of().formatHex(Arrays.copyOf(digest, 16)) + "\"";
    } catch (final NoSuchAlgorithmException e) {
      return null;
    }
  }

  @Override
  protected void doFilter(
      final @NotNull HttpServletRequest request,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain chain)
      throws IOException, ServletException {
    final @NotNull String method = request.getMethod();
    final @Nullable StaticFile file =
        "GET".equals(method) || "HEAD".equals(method) ? file(request.getRequestURI()) : null;
    if (file == null) {
      chain.doFilter(request, response);
      return;
    }
    response.setHeader("Cache-Control", "no-cache");
    if (file.etag() != null) {
      response.setHeader("ETag", file.etag());
      if (file.etag().equals(request.getHeader("If-None-Match"))) {
        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        return;
      }
    }
    response.setContentType(file.type());
    response.setContentLength(file.bytes().length);
    if ("GET".equals(method)) {
      response.getOutputStream().write(file.bytes());
    }
  }

  @Nullable
  StaticFile file(final @NotNull String path) {
    if (path.startsWith("/api/")
        || path.endsWith("/")
        || path.contains("..")
        || path.contains("//")
        || path.contains("\\")) {
      return null;
    }
    final @Nullable String type = MimeTypes.DEFAULTS.getMimeByExtension(path);
    if (type == null) {
      return null;
    }
    return this.files.computeIfAbsent(
        path,
        key -> {
          try (final @Nullable InputStream in = this.loader.getResourceAsStream("web" + key)) {
            if (in == null) {
              return null;
            }
            final byte[] bytes = in.readAllBytes();
            return new StaticFile(
                bytes, type.startsWith("text/") ? type + ";charset=utf-8" : type, hash(bytes));
          } catch (final IOException e) {
            return null;
          }
        });
  }

  record StaticFile(byte @NotNull [] bytes, @NotNull String type, @Nullable String etag) {}
}

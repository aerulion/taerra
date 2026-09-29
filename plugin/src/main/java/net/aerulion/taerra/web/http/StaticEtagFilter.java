package net.aerulion.taerra.web.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class StaticEtagFilter extends HttpFilter {

  private static final @NotNull String IF_NONE_MATCH = "If-None-Match";
  private static final @NotNull String ETAG = "ETag";

  private final transient @NotNull ClassLoader loader;
  private final transient @NotNull Map<String, String> etags = new ConcurrentHashMap<>();

  StaticEtagFilter(final @NotNull ClassLoader loader) {
    super();
    this.loader = loader;
  }

  static @Nullable String hash(final @NotNull InputStream in) throws IOException {
    try {
      final byte[] digest = MessageDigest.getInstance("SHA-256").digest(in.readAllBytes());
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
    final @Nullable String etag = etag(request);
    if (etag == null) {
      chain.doFilter(request, response);
      return;
    }
    if (etag.equals(request.getHeader(IF_NONE_MATCH))) {
      response.setHeader("Cache-Control", "no-cache");
      response.setHeader(ETAG, etag);
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return;
    }
    chain.doFilter(
        new HttpServletRequestWrapper(request) {
          @Override
          public @Nullable String getHeader(final String name) {
            return IF_NONE_MATCH.equalsIgnoreCase(name) ? null : super.getHeader(name);
          }

          @Override
          public Enumeration<String> getHeaders(final String name) {
            return IF_NONE_MATCH.equalsIgnoreCase(name)
                ? Collections.emptyEnumeration()
                : super.getHeaders(name);
          }
        },
        new HttpServletResponseWrapper(response) {
          @Override
          public void setHeader(final String name, final String value) {
            super.setHeader(name, ETAG.equalsIgnoreCase(name) ? etag : value);
          }

          @Override
          public void addHeader(final String name, final String value) {
            super.addHeader(name, ETAG.equalsIgnoreCase(name) ? etag : value);
          }
        });
  }

  private @Nullable String etag(final @NotNull HttpServletRequest request) {
    final @NotNull String method = request.getMethod();
    final @NotNull String path = request.getRequestURI();
    if (!("GET".equals(method) || "HEAD".equals(method))
        || path.startsWith("/api/")
        || path.contains("..")
        || path.contains("//")
        || path.contains("\\")) {
      return null;
    }
    return this.etags.computeIfAbsent(
        path,
        key -> {
          try (final @Nullable InputStream in =
                  this.loader.getResourceAsStream(
                      "web" + (key.endsWith("/") ? key + "index.html" : key))) {
            return in == null ? null : hash(in);
          } catch (final IOException e) {
            return null;
          }
        });
  }
}

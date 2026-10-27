package net.aerulion.taerra.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestsTest {

  private static int status(final int port, final String method, final String site)
      throws Exception {
    try (final HttpClient client = HttpClient.newHttpClient()) {
      return client
          .send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/thing"))
                  .method(method, HttpRequest.BodyPublishers.noBody())
                  .header("Sec-Fetch-Site", site)
                  .build(),
              HttpResponse.BodyHandlers.discarding())
          .statusCode();
    }
  }

  @Test
  @DisplayName("every API route that changes something refuses cross-site requests")
  void crossSiteFilter() throws Exception {
    final Javalin app =
        Javalin.create(
                config -> {
                  config.routes.before("/api/*", Requests::rejectCrossSite);
                  config.routes.get("/api/thing", ctx -> ctx.status(200));
                  config.routes.post("/api/thing", ctx -> ctx.status(204));
                  config.routes.delete("/api/thing", ctx -> ctx.status(204));
                })
            .start("localhost", 0);
    try {
      assertEquals(403, status(app.port(), "POST", "cross-site"));
      assertEquals(403, status(app.port(), "DELETE", "same-site"));
      assertEquals(204, status(app.port(), "POST", "same-origin"));
      assertEquals(200, status(app.port(), "GET", "cross-site"));
    } finally {
      app.stop();
    }
  }

  @Test
  @DisplayName("only the map's own pages and clients without an Origin count as the same origin")
  void sameOrigin() {
    assertTrue(Requests.sameOrigin(Map.of("Sec-Fetch-Site", "same-origin")::get, ""));
    assertFalse(Requests.sameOrigin(Map.of("Sec-Fetch-Site", "same-site")::get, ""));
    assertTrue(Requests.sameOrigin(Map.<String, String>of()::get, ""));
    assertTrue(
        Requests.sameOrigin(
            Map.of("Origin", "https://map.example.net", "Host", "map.example.net")::get, ""));
    assertTrue(
        Requests.sameOrigin(
            Map.of("Origin", "http://10.0.0.1:8123", "Host", "10.0.0.1:8123")::get, ""));
    assertFalse(
        Requests.sameOrigin(
            Map.of("Origin", "https://evil.example.net", "Host", "map.example.net")::get, ""));
    assertFalse(Requests.sameOrigin(Map.of("Origin", "null", "Host", "map.example.net")::get, ""));
    assertTrue(
        Requests.sameOrigin(
            Map.of("Origin", "https://map.example.net", "Host", "localhost:8123")::get,
            "https://map.example.net"));
    assertTrue(
        Requests.sameOrigin(
            Map.of(
                    "Origin", "https://map.example.net",
                    "Host", "localhost:8123",
                    "X-Forwarded-Host", "map.example.net")
                ::get,
            ""));
    assertFalse(
        Requests.sameOrigin(
            Map.of("Origin", "https://evil.example.net", "Host", "localhost:8123")::get,
            "https://map.example.net"));
  }
}

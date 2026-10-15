package net.aerulion.taerra.web.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestsTest {

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

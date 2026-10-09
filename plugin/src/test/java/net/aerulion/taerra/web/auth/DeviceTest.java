package net.aerulion.taerra.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceTest {

  @Test
  @DisplayName("common browsers and systems are named, anything else stays unknown")
  void of() {
    assertEquals(
        new Device("Chrome", "Windows"),
        Device.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/141.0.0.0 Safari/537.36"));
    assertEquals(
        new Device("Edge", "Windows"),
        Device.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0"));
    assertEquals(
        new Device("Safari", "iPhone"),
        Device.of(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15"
                + " (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1"));
    assertEquals(
        new Device("Firefox", "Android"),
        Device.of("Mozilla/5.0 (Android 15; Mobile; rv:143.0) Gecko/143.0 Firefox/143.0"));
    assertEquals(
        new Device("Firefox", "macOS"),
        Device.of(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:143.0) Gecko/20100101 Firefox/143.0"));
    assertEquals(new Device(null, null), Device.of("curl/8.7.1"));
    assertEquals(new Device(null, null), Device.of(null));
  }
}

package net.aerulion.taerra.web.auth;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Device(@Nullable String browser, @Nullable String os) {

  private static final @NotNull List<Map.Entry<String, String>> BROWSERS =
      List.of(
          Map.entry("Edg", "Edge"),
          Map.entry("OPR/", "Opera"),
          Map.entry("SamsungBrowser", "Samsung Internet"),
          Map.entry("Firefox/", "Firefox"),
          Map.entry("FxiOS", "Firefox"),
          Map.entry("CriOS", "Chrome"),
          Map.entry("Chrome/", "Chrome"),
          Map.entry("Safari/", "Safari"));
  private static final @NotNull List<Map.Entry<String, String>> SYSTEMS =
      List.of(
          Map.entry("iPhone", "iPhone"),
          Map.entry("iPad", "iPad"),
          Map.entry("Android", "Android"),
          Map.entry("CrOS", "ChromeOS"),
          Map.entry("Windows", "Windows"),
          Map.entry("Macintosh", "macOS"),
          Map.entry("Linux", "Linux"));

  public static @NotNull Device of(final @Nullable String userAgent) {
    return userAgent == null
        ? new Device(null, null)
        : new Device(match(BROWSERS, userAgent), match(SYSTEMS, userAgent));
  }

  private static @Nullable String match(
      final @NotNull List<Map.Entry<String, String>> known, final @NotNull String userAgent) {
    return known.stream()
        .filter(entry -> userAgent.contains(entry.getKey()))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }
}

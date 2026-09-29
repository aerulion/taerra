package net.aerulion.taerra.mod.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

public final class Urls {

  private Urls() {}

  public static String query(final @NotNull String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public static @NotNull String segment(final @NotNull String value) {
    return query(value).replace("+", "%20");
  }
}

package net.aerulion.taerra.mod.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;

public final class Urls {

  private static final @NotNull String NEW_ISSUE = "https://github.com/aerulion/taerra/issues/new";
  private static final int MAX_URL = 6000;

  private Urls() {}

  public static String query(final @NotNull String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public static @NotNull String newIssue(
      final @NotNull String template, final @NotNull String debug) {
    final @NotNull String form = NEW_ISSUE + "?template=" + template;
    final @NotNull String filled = form + "&debug=" + segment(debug);
    return filled.length() <= MAX_URL ? filled : form;
  }

  public static @NotNull String segment(final @NotNull String value) {
    return query(value).replace("+", "%20");
  }
}

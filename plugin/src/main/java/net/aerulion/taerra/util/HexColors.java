package net.aerulion.taerra.util;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class HexColors {

  private static final @NotNull Pattern HEX_RGB = Pattern.compile("#?[0-9A-Fa-f]{6}");

  private HexColors() {
    super();
  }

  public static @NotNull OptionalInt parseRgb(final @Nullable String value) {
    if (value == null) {
      return OptionalInt.empty();
    }
    final @NotNull String trimmed = value.trim();
    if (!HEX_RGB.matcher(trimmed).matches()) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(
        Integer.parseInt(trimmed.startsWith("#") ? trimmed.substring(1) : trimmed, 16));
  }

  public static @NotNull String format(final int rgb) {
    return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
  }
}

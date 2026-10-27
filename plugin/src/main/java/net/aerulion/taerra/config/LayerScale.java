package net.aerulion.taerra.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LayerScale {

  public static final int MIN = -12;
  public static final int MAX = 8;
  private static final @NotNull Pattern FORMAT = Pattern.compile("(\\d{1,5})(px|blocks?)");

  private LayerScale() {
    super();
  }

  public static @Nullable Integer parse(final @NotNull String text) {
    final @NotNull Matcher matcher = FORMAT.matcher(text);
    if (!matcher.matches()) {
      return null;
    }
    final int amount = Integer.parseInt(matcher.group(1));
    if (Integer.bitCount(amount) != 1) {
      return null;
    }
    final int exponent = Integer.numberOfTrailingZeros(amount);
    final int scale = matcher.group(2).equals("px") ? exponent : -exponent;
    return scale >= MIN && scale <= MAX ? scale : null;
  }

  public static @NotNull String format(final int scale) {
    return scale >= 0 ? (1 << scale) + "px" : (1 << -scale) + "blocks";
  }
}

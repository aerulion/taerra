package net.aerulion.taerra.util;

import java.text.Normalizer;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Names {

  public static final int MAX_LENGTH = 32;
  public static final int MAX_DESCRIPTION = 280;
  public static final @NotNull String CHARACTERS = "\\p{L}\\p{N} _.,:'&()#!?+-";
  private static final @NotNull Pattern NAME =
      Pattern.compile("[" + CHARACTERS + "]{1," + MAX_LENGTH + "}");

  private Names() {
    super();
  }

  public static @Nullable String of(final @Nullable String input) {
    if (input == null) {
      return null;
    }
    final @NotNull String name = Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    return NAME.matcher(name).matches() ? name : null;
  }

  public static @Nullable String description(final @Nullable String input) {
    if (input == null) {
      return null;
    }
    final @NotNull String text =
        Normalizer.normalize(input, Normalizer.Form.NFC)
            .replaceAll("[\\p{Cntrl}&&[^\n]]", "")
            .replaceAll("\n{3,}", "\n\n")
            .strip();
    return text.isEmpty() ? null : text.substring(0, Math.min(text.length(), MAX_DESCRIPTION));
  }
}

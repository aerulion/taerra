package net.aerulion.taerra.config;

import java.util.List;
import java.util.regex.Pattern;
import net.aerulion.taerra.util.Names;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Setting(
    @NotNull String key,
    @NotNull Kind kind,
    int min,
    int max,
    boolean editable,
    @NotNull List<String> options) {

  private static final @NotNull Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

  static @NotNull Setting number(
      final @NotNull String key, final int min, final int max, final boolean editable) {
    return new Setting(key, Kind.NUMBER, min, max, editable, List.of());
  }

  static @NotNull Setting text(final @NotNull String key, final boolean editable) {
    return new Setting(key, Kind.TEXT, 0, 0, editable, List.of());
  }

  static @NotNull Setting toggle(final @NotNull String key) {
    return new Setting(key, Kind.TOGGLE, 0, 0, true, List.of());
  }

  static @NotNull Setting choice(
      final @NotNull String key, final @NotNull String @NotNull ... options) {
    return new Setting(key, Kind.CHOICE, 0, 0, true, List.of(options));
  }

  static @NotNull Setting secret(final @NotNull String key) {
    return new Setting(key, Kind.SECRET, 0, 0, true, List.of());
  }

  public @Nullable Object valid(final @NotNull Object value) {
    return switch (this.kind) {
      case NUMBER ->
          value instanceof final @NotNull Number number
                  && number.doubleValue() == number.intValue()
                  && number.intValue() >= this.min
                  && number.intValue() <= this.max
              ? number.intValue()
              : null;
      case TOGGLE -> value instanceof Boolean ? value : null;
      case CHOICE ->
          value instanceof final @NotNull String text && this.options.contains(text) ? text : null;
      case SECRET ->
          value instanceof final @NotNull String text && !CONTROL.matcher(text).find()
              ? text
              : null;
      case TEXT -> {
        if (!(value instanceof final @NotNull String text) || CONTROL.matcher(text).find()) {
          yield null;
        }
        if (this.key.equals("web.name") && !text.isBlank()) {
          yield Names.of(text);
        }
        yield text.strip();
      }
    };
  }

  public enum Kind {
    NUMBER,
    TOGGLE,
    TEXT,
    CHOICE,
    SECRET
  }
}

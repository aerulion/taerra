package net.aerulion.taerra.region;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum RegionFog {
  NONE,
  VEIL,
  HIDE;

  public static @Nullable RegionFog parse(final @Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }

  public @NotNull String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean fogged() {
    return this != NONE;
  }
}

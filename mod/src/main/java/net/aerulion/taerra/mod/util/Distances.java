package net.aerulion.taerra.mod.util;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public final class Distances {

  private Distances() {}

  public static @NotNull String format(final double blocks) {
    final long meters = Math.round(blocks);
    if (meters < 1000) {
      return meters + "m";
    }
    return meters < 10_000
        ? String.format(Locale.ROOT, "%.1fkm", meters / 1000.0)
        : Math.round(meters / 1000.0) + "km";
  }
}

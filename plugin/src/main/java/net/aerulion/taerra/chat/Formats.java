package net.aerulion.taerra.chat;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public final class Formats {

  private Formats() {
    super();
  }

  public static @NotNull String duration(final long millis) {
    if (millis < 0L) {
      return "unknown";
    }
    if (millis < 1000L) {
      return millis + "ms";
    }
    final long totalSeconds = millis / 1000L;
    if (totalSeconds < 60L) {
      return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }
    final long hours = totalSeconds / 3600L;
    final long minutes = (totalSeconds % 3600L) / 60L;
    final long seconds = totalSeconds % 60L;
    if (hours > 0L) {
      return hours + "h " + minutes + "m";
    }
    return minutes + "m " + seconds + "s";
  }

  public static @NotNull String millis(final double millis) {
    return String.format(Locale.ROOT, "%.1fms", millis);
  }

  public static @NotNull String percent(final double fraction) {
    return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
  }

  public static @NotNull String count(final double count) {
    return String.format(Locale.ROOT, "%.1f", count);
  }

  public static @NotNull String megabytes(final long bytes) {
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }

  public static @NotNull String rate(final double perSecond) {
    return String.format(Locale.ROOT, "%.2f/s", perSecond);
  }

  public static int filled(final double fraction, final int width) {
    return (int) Math.round(Math.clamp(fraction, 0.0, 1.0) * width);
  }
}

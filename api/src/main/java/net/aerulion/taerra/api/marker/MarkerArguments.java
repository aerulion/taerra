package net.aerulion.taerra.api.marker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

final class MarkerArguments {

  private MarkerArguments() {
    super();
  }

  static @NotNull Map<String, String> orderedCopy(final @NotNull Map<String, String> details) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }

  static @NotNull List<Vector> copy(
      final @NotNull List<Vector> points, final int minimum, final @NotNull String what) {
    if (points.size() < minimum) {
      throw new IllegalArgumentException(
          what + " needs at least " + minimum + " points, got " + points.size());
    }
    return points.stream().map(Vector::clone).toList();
  }
}

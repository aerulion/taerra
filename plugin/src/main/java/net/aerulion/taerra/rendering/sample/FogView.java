package net.aerulion.taerra.rendering.sample;

import java.util.Comparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record FogView(@NotNull Kind kind, int region) {

  public static final @NotNull Comparator<FogView> ORDER =
      Comparator.comparingInt(FogView::region).thenComparing(FogView::kind);

  public static final @NotNull FogView BASE = new FogView(Kind.BASE, 0);

  public static final @NotNull FogView CLEAR = new FogView(Kind.CLEAR, 0);

  public static @NotNull FogView body(final int region) {
    return new FogView(Kind.BODY, region);
  }

  public static @NotNull FogView spill(final int region) {
    return new FogView(Kind.SPILL, region);
  }

  public static @Nullable FogView parse(final @Nullable String id) {
    if (id == null || id.isEmpty()) {
      return BASE;
    }
    if (id.equals("c")) {
      return CLEAR;
    }
    final @Nullable Kind kind =
        switch (id.charAt(0)) {
          case 'b' -> Kind.BODY;
          case 's' -> Kind.SPILL;
          default -> null;
        };
    if (kind == null) {
      return null;
    }
    try {
      final int region = Integer.parseInt(id.substring(1));
      return region > 0 ? new FogView(kind, region) : null;
    } catch (final NumberFormatException e) {
      return null;
    }
  }

  public @NotNull String id() {
    return switch (this.kind) {
      case BASE -> "";
      case BODY -> "b" + this.region;
      case SPILL -> "s" + this.region;
      case CLEAR -> "c";
    };
  }

  public boolean base() {
    return this.kind == Kind.BASE;
  }

  public enum Kind {
    BASE,
    BODY,
    SPILL,
    CLEAR
  }
}

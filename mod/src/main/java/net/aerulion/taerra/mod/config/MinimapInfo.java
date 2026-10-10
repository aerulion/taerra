package net.aerulion.taerra.mod.config;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public enum MinimapInfo {
  COORDINATES,
  BIOME,
  TIME,
  DAY,
  LIGHT;

  public @NotNull String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}

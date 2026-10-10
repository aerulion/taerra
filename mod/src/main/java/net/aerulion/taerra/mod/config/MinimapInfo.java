package net.aerulion.taerra.mod.config;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

public enum MinimapInfo {
  COORDINATES,
  NETHER,
  CHUNK,
  FACING,
  BIOME,
  WEATHER,
  TIME,
  DAY,
  LIGHT,
  REAL_TIME,
  FPS;

  public @NotNull String id() {
    return name().toLowerCase(Locale.ROOT);
  }
}

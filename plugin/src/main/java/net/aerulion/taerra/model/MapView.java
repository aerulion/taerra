package net.aerulion.taerra.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MapView {
  TOP_DOWN("top_down", "tiles", 64, 256, false),
  ISOMETRIC("isometric", "tiles-iso", 256, 256, true);

  private final @NotNull String id;
  private final @NotNull String directoryName;
  private final int tilePixels;
  private final int zoomedTilePixels;
  private final boolean rotated;

  MapView(
      final @NotNull String id,
      final @NotNull String directoryName,
      final int tilePixels,
      final int zoomedTilePixels,
      final boolean rotated) {
    this.id = id;
    this.directoryName = directoryName;
    this.tilePixels = tilePixels;
    this.zoomedTilePixels = zoomedTilePixels;
    this.rotated = rotated;
  }

  public static @NotNull MapView fromId(
      final @Nullable String id, final @NotNull MapView fallback) {
    if (id == null) {
      return fallback;
    }
    for (final @NotNull MapView view : values()) {
      if (view.id.equals(id)) {
        return view;
      }
    }
    return fallback;
  }

  public @NotNull String id() {
    return this.id;
  }

  public int tilePixels() {
    return this.tilePixels;
  }

  public int tilePixels(final int zoom) {
    return zoom == 0 ? this.tilePixels : this.zoomedTilePixels;
  }

  public int zoomedTilePixels() {
    return this.zoomedTilePixels;
  }

  public @NotNull String directoryName() {
    return this.directoryName;
  }

  public boolean rotated() {
    return this.rotated;
  }
}

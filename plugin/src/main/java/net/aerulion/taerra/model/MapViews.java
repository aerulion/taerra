package net.aerulion.taerra.model;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MapViews {
  BOTH("both", List.of(MapView.TOP_DOWN, MapView.ISOMETRIC)),
  TOP_DOWN("top_down", List.of(MapView.TOP_DOWN)),
  ISOMETRIC("isometric", List.of(MapView.ISOMETRIC));

  private final @NotNull String id;
  private final @NotNull List<MapView> views;

  MapViews(final @NotNull String id, final @NotNull List<MapView> views) {
    this.id = id;
    this.views = views;
  }

  public static @Nullable MapViews parse(final @Nullable String id) {
    for (final @NotNull MapViews views : values()) {
      if (views.id.equals(id)) {
        return views;
      }
    }
    return null;
  }

  public @NotNull String id() {
    return this.id;
  }

  public @NotNull List<MapView> views() {
    return this.views;
  }

  public boolean shows(final @NotNull MapView view) {
    return this.views.contains(view);
  }
}
